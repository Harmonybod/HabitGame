package com.habitgame.domain.tracker

import android.content.Context
import com.habitgame.core.database.entity.ChallengeEntity
import com.habitgame.core.database.entity.DayProgressEntity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.HabitFrequency
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.core.database.entity.RepetitionType
import com.habitgame.core.database.entity.TaskCategoryType
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.notification.appReminderLeads
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Runs the 100-day challenge: seeds the tracker tasks, records daily progress and keeps reminders scheduled. */
@Singleton
class TrackerManager @Inject constructor(
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val trackerRepo: TrackerRepository,
    private val userRepo: UserRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        fun dayNumber(startDate: String, today: LocalDate): Int {
            val start = runCatching { LocalDate.parse(startDate) }.getOrDefault(today)
            return (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(1, TrackerPlan.TOTAL_DAYS)
        }
    }

    /** Everything that should happen when the app opens or a new day starts. */
    suspend fun runDailyMaintenance() {
        userRepo.initUser()
        taskRepo.resetStaleRecurringTasks()
        removeExpired()
        ensureChallenge()
        rescheduleAllReminders()
        snapshotToday()
    }

    // App start and the screens can call ensureChallenge() at the same moment; seed only once
    private val seedLock = Mutex()

    /** Creates the challenge on first run and adds the tracker checklist as repeating tasks. */
    suspend fun ensureChallenge(): ChallengeEntity = seedLock.withLock {
        val challenge = createChallengeIfNeeded()
        removeDuplicateTrackerTasks()
        moveWeightGainTasksToHabits()
        removeOldCodingNote()
        challenge
    }

    /** The coding task used to point at a built-in coding plan on Home, which is now gone. */
    private suspend fun removeOldCodingNote() {
        taskRepo.getAllTasks()
            .filter { it.trackerKey == TrackerPlan.CODING_TASK_KEY && it.description.startsWith("Today's suggested coding task") }
            .forEach { taskRepo.updateTask(it.copy(description = "")) }
    }

    /**
     * Earlier builds created the weight gain schedule as tasks. Turn each one into a habit,
     * keeping its time, repeat days, end date and today's tick.
     */
    private suspend fun moveWeightGainTasksToHabits() {
        val seedsByKey = TrackerPlan.SEED_HABITS.associateBy { it.key }
        val oldTasks = taskRepo.getAllTasks().filter { it.trackerKey in seedsByKey }
        if (oldTasks.isEmpty()) return
        val existingKeys = habitRepo.getAllHabits().mapNotNull { it.trackerKey }.toSet()
        val today = LocalDate.now()
        oldTasks.forEach { task ->
            val seed = seedsByKey.getValue(task.trackerKey!!)
            if (seed.key !in existingKeys) {
                val rule = TaskTiming.repeatRule(task)
                val time = TaskTiming.timeOfDay(task) ?: java.time.LocalTime.of(seed.hour, seed.minute)
                val habitId = habitRepo.insertHabit(
                    seedToHabit(seed).copy(
                        reminderTimeMillis = TaskTiming.millisForTime(time),
                        frequency = if (rule.days.isEmpty()) HabitFrequency.DAILY else HabitFrequency.SPECIFIC_DAYS,
                        frequencyDays = rule.days.joinToString(","),
                        repeatEndDate = task.repeatEndDate
                    )
                )
                if (TaskTiming.completedOn(task, today)) habitRepo.completeHabit(habitId)
            }
            taskRepo.deleteTask(task)
            TaskReminderWorker.cancelReminder(context, task.id)
            TaskReminderWorker.dismissNotifications(context, task.id)
        }
    }

    private fun seedToHabit(seed: TrackerPlan.SeedHabit) = HabitEntity(
        name = seed.name,
        iconEmoji = seed.emoji,
        frequency = HabitFrequency.DAILY,
        reminderTimeMillis = TaskTiming.millisForTime(java.time.LocalTime.of(seed.hour, seed.minute)),
        difficulty = seed.difficulty,
        xpReward = EconomyManager.xpForDifficulty(seed.difficulty),
        coinReward = EconomyManager.coinsForDifficulty(seed.difficulty).toInt(),
        groupName = TrackerPlan.WEIGHT_GAIN_GROUP,
        trackerKey = seed.key
    )

    /** Cleans up the double set of tracker tasks an earlier build could create. */
    private suspend fun removeDuplicateTrackerTasks() {
        taskRepo.getAllTasks()
            .filter { it.trackerKey != null }
            .groupBy { it.trackerKey }
            .values
            .forEach { copies ->
                copies.sortedWith(compareByDescending<TaskEntity> { it.isCompleted }.thenBy { it.id })
                    .drop(1)
                    .forEach { extra ->
                        taskRepo.deleteTask(extra)
                        TaskReminderWorker.cancelReminder(context, extra.id)
                    }
            }
    }

    private suspend fun createChallengeIfNeeded(): ChallengeEntity {
        val existing = trackerRepo.getChallenge()
        val challenge = existing ?: ChallengeEntity(startDate = LocalDate.now().toString())
        if (challenge.seeded) {
            if (existing == null) trackerRepo.saveChallenge(challenge)
            return challenge
        }
        if (taskRepo.getTrackerTaskCount() == 0) {
            TrackerPlan.SEED_TASKS.forEach { seed -> taskRepo.insertTask(seedToTask(seed, challenge.savingsGoal)) }
        }
        if (habitRepo.getAllHabits().none { it.trackerKey != null }) {
            TrackerPlan.SEED_HABITS.forEach { seed -> habitRepo.insertHabit(seedToHabit(seed)) }
        }
        val seeded = challenge.copy(seeded = true)
        trackerRepo.saveChallenge(seeded)
        return seeded
    }

    private fun seedToTask(seed: TrackerPlan.SeedTask, savingsGoal: Double): TaskEntity {
        val timeMillis = TaskTiming.millisOf(LocalDate.now().atTime(seed.hour, seed.minute))
        return TaskEntity(
            name = seed.name,
            description = seed.description,
            repetition = RepetitionType.REPEATING,
            categoryType = TaskCategoryType.ACTIVITY,
            activityCategory = seed.category,
            difficulty = seed.difficulty,
            xpReward = EconomyManager.xpForDifficulty(seed.difficulty),
            coinReward = EconomyManager.coinsForDifficulty(seed.difficulty).toInt(),
            isAllDay = false,
            scheduledTimeMillis = timeMillis,
            isRecurring = true,
            notifyEnabled = true,
            notifyAtMillis = timeMillis,
            focusDurationMinutes = seed.focusMinutes,
            savingAmount = if (seed.key == TrackerPlan.SAVINGS_TASK_KEY) dailySavingFor(savingsGoal) else 0.0,
            trackerKey = seed.key
        )
    }

    /** Spreads a 100-day savings goal into a daily amount. */
    fun dailySavingFor(goal: Double): Double =
        if (goal <= 0) 0.0 else Math.ceil(goal / TrackerPlan.TOTAL_DAYS * 100) / 100

    suspend fun updateChallenge(startDate: String? = null, savingsGoal: Double? = null, currency: String? = null) {
        val current = ensureChallenge()
        val updated = current.copy(
            startDate = startDate ?: current.startDate,
            savingsGoal = savingsGoal ?: current.savingsGoal,
            currency = currency ?: current.currency
        )
        trackerRepo.saveChallenge(updated)
        if (savingsGoal != null) {
            // Keep the daily savings task in step with the goal
            taskRepo.getAllTasks()
                .filter { it.trackerKey == TrackerPlan.SAVINGS_TASK_KEY }
                .forEach { taskRepo.updateTask(it.copy(savingAmount = dailySavingFor(savingsGoal))) }
        }
    }

    /** Records how much of today's checklist (tasks + habits) is done. */
    suspend fun snapshotToday() {
        val today = LocalDate.now()
        val tasks = taskRepo.getAllTasks().filter { TaskTiming.countsToward(it, today) }
        val tasksDone = tasks.count { TaskTiming.completedOn(it, today) }

        val habits = habitRepo.observeActiveHabits().first().filter { TaskTiming.isDueOn(it, today) }
        val habitsDone = habits.count { habitRepo.isHabitCompletedToday(it.id) }

        trackerRepo.saveDayProgress(
            DayProgressEntity(date = today.toString(), done = tasksDone + habitsDone, total = tasks.size + habits.size)
        )
    }

    /** Reminder times from Settings: before tasks and habits, and before shopping deadlines. */
    private suspend fun leads(): Pair<List<Int>, List<Int>> {
        val user = userRepo.getUser() ?: com.habitgame.core.database.entity.UserEntity()
        return user.appReminderLeads(context)
    }

    suspend fun rescheduleAllReminders() {
        val (taskLeads, deadlineLeads) = leads()
        taskRepo.getAllTasks().forEach { TaskReminderWorker.schedule(context, it, taskLeads, deadlineLeads) }
        habitRepo.getAllHabits().forEach {
            HabitReminderWorker.schedule(context, it, habitRepo.isHabitCompletedToday(it.id), taskLeads)
        }
    }

    suspend fun scheduleReminder(task: TaskEntity) {
        val (taskLeads, deadlineLeads) = leads()
        TaskReminderWorker.schedule(context, task, taskLeads, deadlineLeads)
    }

    suspend fun scheduleReminder(habit: HabitEntity) {
        HabitReminderWorker.schedule(context, habit, habitRepo.isHabitCompletedToday(habit.id), leads().first)
    }

    /** Tasks and shopping items still not done when their deadline passed (e.g. while the phone was off) are removed. */
    suspend fun removeExpired() {
        val now = System.currentTimeMillis()
        taskRepo.getAllTasks()
            .filter { !it.isRecurring && !it.isCompleted && (it.deadlineAt ?: Long.MAX_VALUE) <= now }
            .forEach { task ->
                taskRepo.deleteTask(task)
                TaskReminderWorker.cancelReminder(context, task.id)
                TaskReminderWorker.dismissNotifications(context, task.id)
            }
    }

    /** Saves an edited task and moves its reminders to the new time. */
    suspend fun saveTask(task: TaskEntity) {
        taskRepo.updateTask(task)
        scheduleReminder(task)
        snapshotToday()
    }

    /** Saves an edited habit and moves its reminders to the new time. */
    suspend fun saveHabit(habit: HabitEntity) {
        habitRepo.updateHabit(habit)
        scheduleReminder(habit)
        snapshotToday()
    }
}
