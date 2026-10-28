package com.habitgame.domain.engine

import com.habitgame.core.database.dao.RewardItemDao
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.entity.RewardSubcategory
import com.habitgame.core.database.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A running Shop purchase.
 *
 * App Unlock and Entertainment time is spent only while a locked app is open ([countsUsage]):
 * buy 30 minutes in the morning and they're still all there the next day until you use them.
 * Boosters and No Restriction Day run on the clock from the moment they're bought.
 */
data class ActiveSession(val item: RewardItemEntity, val startedAt: Long, val totalMillis: Long) {
    val countsUsage: Boolean
        get() = item.subcategory == RewardSubcategory.APP_UNLOCK || item.subcategory == RewardSubcategory.ENTERTAINMENT

    /** True for sessions that open the locked apps. */
    val unlocksApps: Boolean
        get() = countsUsage || item.boosterEffect == BoosterManager.NO_RESTRICTION

    fun remainingMillis(now: Long = System.currentTimeMillis()): Long =
        (if (countsUsage) totalMillis - item.usedMillis else startedAt + totalMillis - now).coerceAtLeast(0L)

    /** 1.0 when it has just started, 0.0 when time is up. */
    fun fractionLeft(now: Long = System.currentTimeMillis()): Float =
        remainingMillis(now).toFloat() / totalMillis.coerceAtLeast(1L)

    fun isFinished(now: Long = System.currentTimeMillis()): Boolean = remainingMillis(now) <= 0L

    /** When a clock-based session ends; usage-based ones have no fixed end. */
    val endsAt: Long? get() = if (countsUsage) null else startedAt + totalMillis
}

/**
 * Runs timed Shop purchases. Each one starts when bought and is used up when its time
 * runs out (so it can be bought again). While an unlock is running the locked apps open;
 * when it ends they lock again.
 */
@Singleton
class BoosterManager @Inject constructor(
    private val rewardDao: RewardItemDao,
    private val appLockRepo: AppLockRepository
) {
    companion object {
        const val DOUBLE_XP = "DOUBLE_XP"
        const val DOUBLE_COINS = "DOUBLE_COINS"
        const val NO_RESTRICTION = "NO_RESTRICTION"
        const val STREAK_FREEZE = "STREAK_FREEZE"

        private const val DAY_MILLIS = 24 * 60 * 60_000L

        /** How long a timed item lasts. Items without a duration (Streak Freeze) last a day. */
        fun durationMillis(item: RewardItemEntity): Long =
            (if (item.durationMinutes > 0) item.durationMinutes * 60_000L else DAY_MILLIS) + item.extendedMinutes * 60_000L

        /** No Restriction Day can be stretched by 5 hours for 50 coins, once it's in its last [EXTEND_OFFER_MILLIS]. */
        const val EXTEND_MINUTES = 5 * 60
        const val EXTEND_COST = 50L
        const val EXTEND_OFFER_MILLIS = 2 * 60 * 60_000L

        fun canExtend(session: ActiveSession, now: Long = System.currentTimeMillis()): Boolean =
            session.item.boosterEffect == NO_RESTRICTION && !session.isFinished(now) && session.remainingMillis(now) <= EXTEND_OFFER_MILLIS

        fun isTimed(item: RewardItemEntity): Boolean =
            item.boosterEffect.isNotEmpty() ||
                item.subcategory == RewardSubcategory.APP_UNLOCK ||
                item.subcategory == RewardSubcategory.ENTERTAINMENT

        fun toSession(item: RewardItemEntity): ActiveSession? {
            if (!item.isActive) return null
            val start = item.activatedAt ?: return null
            return ActiveSession(item, start, durationMillis(item))
        }
    }

    /** Everything currently running: unlock time first (oldest bought first), then boosters by end time. */
    fun observeActiveSessions(): Flow<List<ActiveSession>> =
        rewardDao.observeAllRewards().map { items ->
            items.mapNotNull(::toSession).sortedWith(compareBy({ !it.countsUsage }, { it.endsAt ?: it.startedAt }))
        }

    suspend fun activeSessions(): List<ActiveSession> =
        observeActiveSessions().first().filter { !it.isFinished() }

    /** Starts a bought timed item now. */
    suspend fun startSession(item: RewardItemEntity) {
        rewardDao.activateReward(item.id)
    }

    /**
     * Spends [millis] of locked-app time from the oldest App Unlock / Entertainment purchase.
     * Returns false when there's no such time left.
     */
    suspend fun spendUnlockTime(millis: Long): Boolean {
        val session = activeSessions().firstOrNull { it.countsUsage } ?: return false
        rewardDao.addUsage(session.item.id, millis.coerceAtMost(session.remainingMillis()))
        return true
    }

    /**
     * Ends sessions whose time is up: uses the item up, and locks the apps again
     * once no unlock is running any more. Returns the sessions that just ended.
     */
    suspend fun endFinishedSessions(now: Long = System.currentTimeMillis()): List<ActiveSession> {
        val all = observeActiveSessions().first()
        val finished = all.filter { it.isFinished(now) }
        finished.forEach { rewardDao.consumeReward(it.item.id) }
        if (finished.any { it.unlocksApps } && all.none { it.unlocksApps && !it.isFinished(now) }) {
            appLockRepo.getAll().filter { it.isUnlocked }.forEach { appLockRepo.lockApp(it.packageName) }
        }
        return finished
    }

    /** Adds [minutes] to a running session. False if it has already ended. */
    suspend fun extend(itemId: Long, minutes: Int): Boolean = rewardDao.extend(itemId, minutes) > 0

    /** True while an App Unlock, Entertainment or No Restriction Day has time left. */
    suspend fun hasActiveUnlock(): Boolean = activeSessions().any { it.unlocksApps }

    /** XP multiplier: 2× while Double XP is running. */
    suspend fun xpMultiplier(): Float = if (isRunning(DOUBLE_XP)) 2.0f else 1.0f

    /** Coin multiplier: 2.5× while Coin Rush is running. */
    suspend fun coinMultiplier(): Float = if (isRunning(DOUBLE_COINS)) 2.5f else 1.0f

    suspend fun isNoRestrictionActive(): Boolean = isRunning(NO_RESTRICTION)

    suspend fun isStreakFreezeActive(): Boolean = isRunning(STREAK_FREEZE)

    private suspend fun isRunning(effect: String): Boolean =
        activeSessions().any { it.item.boosterEffect == effect }

    /** Kept for existing callers: starts a booster. */
    suspend fun activateBooster(item: RewardItemEntity) = startSession(item)

    /** Ends any sessions whose time is up — call on app start. */
    suspend fun deactivateExpiredBoosters() {
        endFinishedSessions()
    }
}
