package com.habitgame.domain.transfer

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.habitgame.core.database.AppDatabase
import com.habitgame.core.database.entity.*
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a transfer code carries: progress, money saved, XP and level, coins, owned looks. */
data class TransferPayload(
    val format: Int,
    val appVersion: Int,
    val createdAt: Long,
    val user: UserEntity,
    val ownedCosmetics: List<String>,
    val challenge: ChallengeEntity?,
    val dayProgress: List<DayProgressEntity>,
    val tasks: List<TaskEntity>,
    val habits: List<HabitEntity>,
    val habitCompletions: List<HabitCompletionEntity>,
    val streakDays: List<StreakDayEntity>,
    val zones: List<LocationZoneEntity>,
    val savings: List<SavingsEntryEntity>,
    val journal: List<JournalEntryEntity>,
    val xpLog: List<XpLogEntity>,
    val focusSessions: List<FocusSessionEntity>? = null,  // null in codes from before 2.2
    val notes: List<NoteEntity>? = null,                   // null in codes from before 2.3
    val rewardClaims: List<RewardClaimEntity>? = null,     // null in codes from before 2.7
    val daySkips: List<DaySkipEntity>? = null              // null in codes from before 2.8
)

/**
 * Moves progress between phones without a server: [createCode] packs it into one long
 * text code (compressed), and [load] on the other phone replaces its progress with it.
 * Things that belong to one phone stay put: the profile and background photos, app locks
 * and running App Unlock time.
 */
@Singleton
class ProgressTransfer @Inject constructor(
    private val db: AppDatabase,
    private val trackerManager: TrackerManager,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val dao get() = db.transferDao()

    suspend fun createCode(appVersion: Int): String {
        val payload = TransferPayload(
            format = FORMAT,
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            user = db.userDao().getUser() ?: UserEntity(),
            ownedCosmetics = dao.ownedCosmetics(),
            challenge = db.challengeDao().get(),
            dayProgress = dao.dayProgress(),
            tasks = dao.tasks(),
            habits = dao.habits(),
            habitCompletions = dao.habitCompletions(),
            streakDays = dao.streakDays(),
            zones = dao.zones(),
            savings = dao.savings(),
            journal = dao.journal(),
            xpLog = dao.xpLog(),
            focusSessions = dao.focusSessions(),
            notes = dao.notes(),
            rewardClaims = dao.rewardClaims(),
            daySkips = dao.daySkips()
        )
        return encode(gson.toJson(payload))
    }

    /** Reads a code without changing anything, so the user can see what it holds first. */
    fun read(code: String): TransferPayload {
        val payload = runCatching { gson.fromJson(decode(code), TransferPayload::class.java) }.getOrNull()
            ?: throw IllegalArgumentException("That code isn't complete. Copy the whole code and try again.")
        if (payload.format > FORMAT) throw IllegalArgumentException("That code is from a newer HabitGame. Update this phone first.")
        return payload
    }

    /** True once anything has been earned or written here (a brand-new install has nothing). */
    suspend fun hasProgress(): Boolean {
        val user = db.userDao().getUser() ?: return false
        return user.level > 1 || user.currentXp > 0 || user.totalCoins > 0 || user.totalTasksDone > 0 ||
            dao.xpLog().isNotEmpty() || dao.savings().isNotEmpty() || dao.journal().isNotEmpty() || dao.notes().isNotEmpty()
    }

    /** Replaces this phone's progress with [payload]. */
    suspend fun load(payload: TransferPayload) {
        val oldTasks = dao.tasks()
        val oldHabits = dao.habits()
        val here = db.userDao().getUser()
        db.withTransaction {
            dao.clearTasks(); dao.clearHabits(); dao.clearHabitCompletions(); dao.clearStreakDays()
            dao.clearZones(); dao.clearDayProgress(); dao.clearJournal(); dao.clearSavings(); dao.clearXpLog()
            dao.putZones(payload.zones)
            dao.putTasks(payload.tasks)
            dao.putHabits(payload.habits)
            dao.putHabitCompletions(payload.habitCompletions)
            dao.putStreakDays(payload.streakDays)
            dao.putDayProgress(payload.dayProgress)
            dao.putJournal(payload.journal)
            dao.putSavings(payload.savings)
            dao.putXpLog(payload.xpLog)
            // Older codes don't carry focus sessions; keep this phone's then
            payload.focusSessions?.let { dao.clearFocusSessions(); dao.putFocusSessions(it) }
            payload.notes?.let { dao.clearNotes(); dao.putNotes(it) }
            payload.rewardClaims?.let { dao.clearRewardClaims(); dao.putRewardClaims(it) }
            payload.daySkips?.let { dao.clearDaySkips(); dao.putDaySkips(it) }
            payload.challenge?.let { dao.putChallenge(it) }
            // Photos are files on this phone, so keep this phone's own
            dao.putUser(
                payload.user.copy(
                    profileImageUri = here?.profileImageUri.orEmpty(),
                    backgroundImagePath = here?.backgroundImagePath.orEmpty()
                )
            )
            dao.setOwnedCosmetics(payload.ownedCosmetics)
        }
        // Reminders follow the new tasks and habits
        oldTasks.forEach { TaskReminderWorker.cancelReminder(context, it.id) }
        oldHabits.forEach { HabitReminderWorker.cancelReminders(context, it.id) }
        trackerManager.rescheduleAllReminders()
        trackerManager.snapshotToday()
    }

    companion object {
        const val FORMAT = 2  // 2: shopping list tasks; older versions refuse these codes instead of mis-reading them
        private const val PREFIX = "HG1-"

        /** JSON → gzip → URL-safe Base64, with a short prefix so codes are recognisable. */
        fun encode(json: String): String {
            val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }.toByteArray()
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /** Undoes [encode]. Spaces and line breaks added by chat apps are ignored. */
        fun decode(code: String): String {
            val clean = code.filterNot { it.isWhitespace() }
            require(clean.startsWith(PREFIX)) { "Not a HabitGame transfer code" }
            val bytes = Base64.getUrlDecoder().decode(clean.removePrefix(PREFIX))
            return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }
}
