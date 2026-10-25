package com.habitgame.domain.engine

import android.content.Context
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitManager @Inject constructor(
    private val habitRepo: HabitRepository,
    private val economyManager: EconomyManager,
    private val progressEngine: UserProgressEngine,
    private val streakManager: StreakManager,
    private val trackerManager: com.habitgame.domain.tracker.TrackerManager,
    @ApplicationContext private val context: Context
) {
    /**
     * Returns which habits are due today based on their frequency settings.
     */
    fun filterTodaysHabits(habits: List<HabitEntity>): List<HabitEntity> {
        return filterHabitsForDate(habits, LocalDate.now())
    }

    /**
     * Returns which habits are due on a specific date based on their repeat days and end date.
     */
    fun filterHabitsForDate(habits: List<HabitEntity>, date: LocalDate): List<HabitEntity> =
        habits.filter { TaskTiming.isDueOn(it, date) }

    /**
     * Completes a habit for today and awards rewards.
     */
    suspend fun completeHabit(habit: HabitEntity): HabitCompletionResult {
        habitRepo.completeHabit(habit.id)

        val streak = habitRepo.getHabitStreak(habit.id)
        val multiplier = streakManager.streakMultiplier(streak)

        val xpBase = EconomyManager.xpForDifficulty(habit.difficulty)
        val coinBase = EconomyManager.coinsForDifficulty(habit.difficulty)

        val xpResult = progressEngine.awardXp(xpBase, multiplier)
        val coinsEarned = economyManager.earnCoins(coinBase, "Habit: ${habit.name}", multiplier)
        streakManager.recordProductiveDay(xpBase, coinBase.toInt())

        // Done for today: clear its notifications and move the reminder to its next day
        HabitReminderWorker.dismissNotifications(context, habit.id)
        trackerManager.scheduleReminder(habit)
        trackerManager.snapshotToday()

        return HabitCompletionResult(
            xpEarned = xpResult.xpEarned,
            coinsEarned = coinsEarned,
            habitStreak = streak,
            didLevelUp = xpResult.didLevelUp,
            newLevel = xpResult.newLevel,
            newTitle = xpResult.newTitle
        )
    }

    suspend fun uncompleteHabit(habitId: Long) {
        habitRepo.uncompleteHabit(habitId)
        habitRepo.getHabitById(habitId)?.let { trackerManager.scheduleReminder(it) }
        trackerManager.snapshotToday()
    }

    suspend fun getHabitStreak(habitId: Long): Int = habitRepo.getHabitStreak(habitId)
}

data class HabitCompletionResult(
    val xpEarned: Int,
    val coinsEarned: Long,
    val habitStreak: Int,
    val didLevelUp: Boolean,
    val newLevel: Int,
    val newTitle: String
)
