package com.habitgame.domain.engine

import android.content.Context
import com.habitgame.core.database.entity.ActivityCategory
import com.habitgame.core.database.entity.SavingsEntryEntity
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Completing a task, wherever it happens: the app, the Savings tab, or a notification's Done button. */
@Singleton
class TaskCompletionManager @Inject constructor(
    private val taskRepo: TaskRepository,
    private val userRepo: UserRepository,
    private val trackerRepo: TrackerRepository,
    private val progressEngine: UserProgressEngine,
    private val economyManager: EconomyManager,
    private val streakManager: StreakManager,
    private val trackerManager: TrackerManager,
    private val checklistDao: com.habitgame.core.database.dao.ChecklistDao,
    private val completionRewards: CompletionRewards,
    @ApplicationContext private val context: Context
) {
    /**
     * Marks the task done and pays out XP and coins. For savings tasks, [savedAmount]
     * (or the task's own amount) is logged in the Savings tab.
     * Returns null if the task was already done, or its checklist isn't finished yet (its last step completes it).
     */
    suspend fun complete(taskId: Long, savedAmount: Double? = null): LevelUpResult? {
        val task = taskRepo.getTaskById(taskId) ?: return null
        if (task.isCompleted) return null
        val steps = checklistDao.forOwner(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id)
        if (Checklist.progress(steps, task.isRecurring, LocalDate.now().toString()) < steps.size) return null

        taskRepo.completeTask(task.id)
        // Done means every step of its checklist is done too
        checklistDao.checkAll(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, LocalDate.now().toString())
        userRepo.countTaskDone()
        val streak = userRepo.getUser()?.currentStreak ?: 0
        val multiplier = streakManager.streakMultiplier(streak)
        val xpBase = EconomyManager.xpForDifficulty(task.difficulty)
        val coinBase = EconomyManager.coinsForDifficulty(task.difficulty)
        val result = progressEngine.awardXp(xpBase, multiplier)
        val coins = economyManager.earnCoins(coinBase, "Completed: ${task.name}", multiplier)
        completionRewards.remember(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, result.xpEarned, coins)
        streakManager.recordProductiveDay(xpBase, coinBase.toInt())

        val amount = savedAmount ?: task.savingAmount
        if (task.activityCategory == ActivityCategory.SAVINGS && amount > 0) {
            trackerRepo.addSaving(
                SavingsEntryEntity(amount = amount, note = task.name, date = LocalDate.now().toString(), taskId = task.id)
            )
        }

        TaskReminderWorker.dismissNotifications(context, task.id)
        val updated = taskRepo.getTaskById(task.id)
        if (updated != null) trackerManager.scheduleReminder(updated)
        trackerManager.snapshotToday()
        return result
    }

    suspend fun complete(task: TaskEntity, savedAmount: Double? = null): LevelUpResult? = complete(task.id, savedAmount)

    /**
     * Back to not done (ticked by mistake): its XP and coins are taken back, the money a savings
     * task logged today is removed, and its checklist is unticked for today.
     */
    suspend fun undo(taskId: Long) {
        val task = taskRepo.getTaskById(taskId) ?: return
        if (!task.isCompleted) return
        val today = LocalDate.now().toString()
        taskRepo.uncompleteTask(task.id)
        userRepo.uncountTaskDone()
        completionRewards.takeBack(
            com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, task.name,
            EconomyManager.xpForDifficulty(task.difficulty), EconomyManager.coinsForDifficulty(task.difficulty)
        )
        if (task.activityCategory == ActivityCategory.SAVINGS) trackerRepo.deleteSavingForTask(task.id, today)
        checklistDao.uncheckOn(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, today)
        taskRepo.getTaskById(task.id)?.let { trackerManager.scheduleReminder(it) }
        trackerManager.snapshotToday()
    }
}
