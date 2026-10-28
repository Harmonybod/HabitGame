package com.habitgame.feature.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.*
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.domain.tracker.TrackerPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject

data class SavingsUiState(
    val goal: Double = 0.0,
    val currency: String = "",
    val total: Double = 0.0,
    val savedToday: Double = 0.0,
    val daysLeft: Int = TrackerPlan.TOTAL_DAYS,
    val entries: List<SavingsEntryEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val days: List<com.habitgame.domain.tracker.SavingsDay> = emptyList()   // saved vs the plan, day 1 to 100
) {
    val progress: Float get() = if (goal <= 0) 0f else (total / goal).toFloat().coerceIn(0f, 1f)
    val remaining: Double get() = (goal - total).coerceAtLeast(0.0)
    val perDayToFinish: Double get() = if (daysLeft <= 0) remaining else remaining / daysLeft
    fun money(amount: Double): String = currency + TaskReminderWorker.formatAmount(amount)
}

@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val trackerRepo: TrackerRepository,
    private val taskRepo: TaskRepository,
    private val trackerManager: TrackerManager,
    private val taskCompletionManager: TaskCompletionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<SavingsUiState> = combine(
        trackerRepo.observeChallenge(),
        trackerRepo.observeSavings(),
        taskRepo.observeAllTasks()
    ) { challenge, entries, tasks ->
        val today = LocalDate.now()
        val day = challenge?.let { TrackerManager.dayNumber(it.startDate, today) } ?: 1
        SavingsUiState(
            goal = challenge?.savingsGoal ?: 0.0,
            currency = challenge?.currency.orEmpty(),
            total = entries.sumOf { it.amount },
            savedToday = entries.filter { it.date == today.toString() }.sumOf { it.amount },
            daysLeft = TrackerPlan.TOTAL_DAYS - day + 1,
            entries = entries,
            days = com.habitgame.domain.tracker.ProgressStats.savings(
                challenge?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today,
                challenge?.savingsGoal ?: 0.0, entries, today
            ),
            tasks = tasks
                .filter { it.activityCategory == ActivityCategory.SAVINGS }
                .sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }))
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SavingsUiState())

    init {
        viewModelScope.launch { trackerManager.ensureChallenge() }
    }

    fun setGoal(goal: Double, currency: String) {
        viewModelScope.launch { trackerManager.updateChallenge(savingsGoal = goal, currency = currency.trim().take(4)) }
    }

    fun addDeposit(amount: Double, note: String) {
        if (amount <= 0) return
        viewModelScope.launch {
            trackerRepo.addSaving(
                SavingsEntryEntity(amount = amount, note = note.trim().ifBlank { "Money put aside" }, date = LocalDate.now().toString())
            )
        }
    }

    fun deleteDeposit(entry: SavingsEntryEntity) {
        viewModelScope.launch { trackerRepo.deleteSaving(entry) }
    }

    fun completeTask(task: TaskEntity, amount: Double) {
        viewModelScope.launch { taskCompletionManager.complete(task, savedAmount = amount) }
    }

    fun addSavingsTask(name: String, amount: Double, time: LocalTime, repeatDaily: Boolean) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            // A one-time task whose time has already passed today goes on tomorrow
            val date = if (!repeatDaily && !now.toLocalTime().isBefore(time)) now.toLocalDate().plusDays(1) else now.toLocalDate()
            val timeMillis = TaskTiming.millisOf(date.atTime(time))
            val task = TaskEntity(
                name = name.trim().ifBlank { "Put money aside" },
                repetition = if (repeatDaily) RepetitionType.REPEATING else RepetitionType.ONE_TIME,
                categoryType = TaskCategoryType.ACTIVITY,
                activityCategory = ActivityCategory.SAVINGS,
                difficulty = Difficulty.EASY,
                xpReward = EconomyManager.xpForDifficulty(Difficulty.EASY),
                coinReward = EconomyManager.coinsForDifficulty(Difficulty.EASY).toInt(),
                isAllDay = false,
                scheduledDateMillis = if (repeatDaily) null else date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                scheduledTimeMillis = timeMillis,
                isRecurring = repeatDaily,
                notifyEnabled = true,
                notifyAtMillis = timeMillis,
                focusDurationMinutes = 0,
                savingAmount = amount
            )
            val id = taskRepo.insertTask(task)
            trackerManager.scheduleReminder(task.copy(id = id))
            trackerManager.snapshotToday()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
            TaskReminderWorker.cancelReminder(context, task.id)
            trackerManager.snapshotToday()
        }
    }
}
