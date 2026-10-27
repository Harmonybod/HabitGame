package com.habitgame.domain.engine

import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreakManager @Inject constructor(
    private val streakDayDao: com.habitgame.core.database.dao.StreakDayDao,
    private val userRepo: UserRepository,
    private val economyManager: EconomyManager,
    private val boosterManager: BoosterManager,
    private val rewardManager: RewardManager
) {
    private fun today(): String = java.time.LocalDate.now().toString()

    suspend fun recordProductiveDay(xp: Int, coins: Int) {
        val date = today()
        val existing = streakDayDao.getDayByDate(date)
        if (existing == null) {
            streakDayDao.insertOrUpdate(
                com.habitgame.core.database.entity.StreakDayEntity(
                    date, wasProductive = true, tasksCompleted = 1, xpEarned = xp, coinsEarned = coins
                )
            )
        } else {
            streakDayDao.markProductive(date, xp, coins)
        }
        recalculateStreak()
    }

    private suspend fun recalculateStreak() {
        val user = userRepo.getUser() ?: return
        val allDays = streakDayDao.getAllProductiveDays()
        val dateSet = allDays.map { it.date }.toHashSet()
        var streak = 0
        var checkDate = java.time.LocalDate.now()
        while (dateSet.contains(checkDate.toString())) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        val longest = maxOf(streak, user.longestStreak)
        userRepo.updateUser(user.copy(currentStreak = streak, longestStreak = longest))
        checkMilestoneReward(streak)
    }

    /** 50 coins at 5 days, then 100 every 5 days (paid once, however often the streak is counted). */
    private suspend fun checkMilestoneReward(streak: Int) = rewardManager.streakReached(streak)

    suspend fun freezeStreak(): Boolean {
        val freezeCost = 180L
        val spent = economyManager.spendCoins(freezeCost, "Streak freeze")
        if (spent) {
            val date = today()
            val existing = streakDayDao.getDayByDate(date)
                ?: com.habitgame.core.database.entity.StreakDayEntity(date)
            streakDayDao.insertOrUpdate(existing.copy(wasFrozen = true, wasProductive = true))
        }
        return spent
    }

    /**
     * Apply daily penalties for overdue incomplete tasks.
     * Called by DailyResetWorker.
     * - Always deducts 100 XP per incomplete task (even with freeze)
     * - Resets streak to 0 unless streak freeze booster is active
     */
    suspend fun applyDailyPenalties(incompleteTasks: List<TaskEntity>) {
        if (incompleteTasks.isEmpty()) return

        // Always deduct XP regardless of freeze
        incompleteTasks.forEach { _ ->
            userRepo.deductXp(100)
        }

        // Only reset streak if no freeze active
        val freezeActive = boosterManager.isStreakFreezeActive()
        if (!freezeActive) {
            val user = userRepo.getUser() ?: return
            userRepo.updateUser(user.copy(currentStreak = 0))
        }
    }

    fun streakMultiplier(streak: Int): Float = when {
        streak >= 30 -> 2.0f
        streak >= 14 -> 1.5f
        streak >= 7  -> 1.25f
        streak >= 3  -> 1.1f
        else         -> 1.0f
    }
}
