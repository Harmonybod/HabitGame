package com.habitgame.domain.focus

import android.content.Context
import com.google.gson.Gson
import com.habitgame.core.database.entity.FocusSessionEntity
import com.habitgame.core.database.repository.FocusSessionRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.engine.StreakManager
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.engine.UserProgressEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What a whole session earned, shown when it ends. */
data class FocusResult(val minutes: Int, val rounds: Int, val xp: Int, val coins: Long)

data class FocusTimerState(
    val run: FocusRun? = null,           // a session in progress (running or paused)
    val finished: FocusResult? = null    // the session that just ended, until it's been seen
)

/**
 * The focus timer, shared by the Focus screen, the Home card and [FocusTimerService] (which keeps
 * it going while HabitGame is closed). The session is saved, so it survives the app being closed.
 * Each finished focus block is rewarded straight away; XP and coins depend on its length.
 */
@Singleton
class FocusTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepo: FocusSessionRepository,
    private val userRepo: UserRepository,
    private val progressEngine: UserProgressEngine,
    private val economyManager: EconomyManager,
    private val streakManager: StreakManager,
    private val taskCompletionManager: TaskCompletionManager
) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _state = MutableStateFlow(FocusTimerState(run = load()))
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    /** The plan used last time, offered again on the Focus screen. */
    var lastPlan: FocusPlan
        get() = runCatching { gson.fromJson(prefs.getString(KEY_PLAN, null), FocusPlan::class.java) }.getOrNull()?.valid() ?: FocusPlan()
        private set(value) { prefs.edit().putString(KEY_PLAN, gson.toJson(value)).apply() }

    fun start(plan: FocusPlan, taskId: Long?) {
        lastPlan = plan.valid()
        save(FocusRun.start(plan, taskId, System.currentTimeMillis()))
        _state.update { it.copy(finished = null) }
        FocusTimerService.start(context)
    }

    fun pause() = _state.value.run?.let { save(it.pause(System.currentTimeMillis())); FocusTimerService.start(context) }

    fun resume() = _state.value.run?.let { save(it.resume(System.currentTimeMillis())); FocusTimerService.start(context) }

    fun skipBreak() = _state.value.run?.let { save(FocusRun.skipBreak(it, System.currentTimeMillis())); FocusTimerService.start(context) }

    /** Ends the session early. Focus blocks already finished keep their rewards. */
    fun stop() {
        val run = _state.value.run ?: return
        save(null)
        if (run.focusedMinutes > 0) {
            _state.update { it.copy(finished = FocusResult(run.focusedMinutes, run.round - 1, run.xpEarned, run.coinsEarned)) }
        }
        FocusTimerService.start(context)  // lets the service tidy up and stop
    }

    fun dismissResult() = _state.update { it.copy(finished = null) }

    /**
     * Moves the clock on: rewards finished focus blocks and starts breaks and the next blocks.
     * Called every second by [FocusTimerService]. Returns what happened, for its alerts.
     */
    suspend fun tick(): List<FocusEvent> = lock.withLock {
        val run = _state.value.run ?: return emptyList()
        val (next, events) = FocusRun.advance(run, System.currentTimeMillis())
        if (events.isEmpty()) return emptyList()

        var xp = run.xpEarned
        var coins = run.coinsEarned
        var minutes = run.focusedMinutes
        events.filterIsInstance<FocusEvent.FocusDone>().forEach { done ->
            val (earnedXp, earnedCoins) = reward(done.minutes, run.taskId)
            xp += earnedXp
            coins += earnedCoins
            minutes += done.minutes
        }
        if (next == null) {
            // Last block done: the task the session was started from is ticked off
            run.taskId?.let { runCatching { taskCompletionManager.complete(it) } }
            save(null)
            _state.update { it.copy(finished = FocusResult(minutes, run.plan.rounds, xp, coins)) }
        } else {
            save(next.copy(xpEarned = xp, coinsEarned = coins, focusedMinutes = minutes))
        }
        events
    }

    /** XP and coins for one finished focus block, with the focus and streak bonus. */
    private suspend fun reward(minutes: Int, taskId: Long?): Pair<Int, Long> {
        val streak = userRepo.getUser()?.currentStreak ?: 0
        val multiplier = FOCUS_MULTIPLIER * streakManager.streakMultiplier(streak)
        val baseXp = xpFor(minutes)
        val baseCoins = coinsFor(minutes)
        val result = progressEngine.awardXp(baseXp, multiplier)
        val coins = economyManager.earnCoins(baseCoins, "Focus session: ${minutes}min", multiplier)
        streakManager.recordProductiveDay(baseXp, baseCoins.toInt())
        userRepo.addFocusMinutes(minutes.toLong())
        val now = System.currentTimeMillis()
        sessionRepo.insertSession(
            FocusSessionEntity(
                plannedDurationMinutes = minutes,
                actualDurationSeconds = minutes * 60L,
                startedAt = now - minutes * 60_000L,
                completedAt = now,
                wasCompleted = true,
                xpEarned = result.xpEarned,
                coinsEarned = coins.toInt(),
                xpMultiplier = FOCUS_MULTIPLIER,
                taskId = taskId
            )
        )
        return result.xpEarned to coins
    }

    private fun save(run: FocusRun?) {
        prefs.edit().putString(KEY_RUN, run?.let { gson.toJson(it) }).apply()
        _state.update { it.copy(run = run) }
    }

    private fun load(): FocusRun? =
        runCatching { prefs.getString(KEY_RUN, null)?.let { gson.fromJson(it, FocusRun::class.java) } }.getOrNull()

    companion object {
        private const val KEY_RUN = "run"
        private const val KEY_PLAN = "plan"
        const val FOCUS_MULTIPLIER = 1.5f

        /** Base rewards for a focus block of [minutes], before bonuses. */
        fun xpFor(minutes: Int): Int = minutes * 4
        fun coinsFor(minutes: Int): Long = minutes * 2L
    }
}
