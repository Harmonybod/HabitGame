package com.habitgame.domain.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.domain.engine.StreakManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs every night at midnight to:
 * 1. Deduct 100 XP per incomplete overdue task (one-time tasks only)
 * 2. Reset streak if any task is incomplete and no streak freeze is active
 * 3. Keep the XP deduction regardless of streak freeze
 * 4. Reset completed flag for repeating tasks so they show again tomorrow
 */
@HiltWorker
class DailyResetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val userRepo: UserRepository,
    private val streakManager: StreakManager,
    private val boosterManager: BoosterManager,
    private val trackerManager: com.habitgame.domain.tracker.TrackerManager,
    private val skipDao: com.habitgame.core.database.dao.DaySkipDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            // 1. Get incomplete overdue tasks
            // Tasks skipped on their day aren't missed
            val overdueTasks = taskRepo.getIncompleteOverdueTasks().filterNot { task ->
                val day = com.habitgame.domain.tracker.TaskTiming.scheduledDate(task)?.toString() ?: return@filterNot false
                skipDao.isSkipped(com.habitgame.core.database.entity.DaySkipEntity.key(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, day))
            }

            // 2. Deduct XP for each incomplete task (always — even with streak freeze)
            overdueTasks.forEach { _ ->
                userRepo.deductXp(100)
            }

            // 3. Streak penalty (reset streak) unless freeze is active
            if (overdueTasks.isNotEmpty()) {
                val freezeActive = boosterManager.isStreakFreezeActive()
                if (!freezeActive) {
                    val user = userRepo.getUser() ?: return Result.success()
                    userRepo.updateUser(user.copy(currentStreak = 0))
                }
                // Note: streak freeze ONLY protects streak, NOT XP deduction
            }

            // 4. Reset repeating tasks completed on an earlier day (safe even if this runs before midnight)
            taskRepo.resetStaleRecurringTasks()

            // 5. Expire any boosters that ran out
            boosterManager.deactivateExpiredBoosters()

            // 6. Start today's circle on the 100-day board and reschedule reminders
            trackerManager.runDailyMaintenance()

        } catch (e: Exception) {
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "DailyResetWorker"

        /**
         * Schedules the worker to run once per day.
         * Uses a flexible window around midnight.
         */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Calculate delay until next midnight */
        private fun calculateInitialDelay(): Long {
            val now = java.util.Calendar.getInstance()
            val midnight = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        }
    }
}
