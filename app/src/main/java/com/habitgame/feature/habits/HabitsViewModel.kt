package com.habitgame.feature.habits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.dao.ChecklistDao
import com.habitgame.core.database.dao.DaySkipDao
import com.habitgame.core.database.entity.*
import com.habitgame.core.database.repository.AreaRepository
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.Checklist
import com.habitgame.domain.engine.ChecklistManager
import com.habitgame.domain.engine.HabitManager
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.notification.deadlineReminderLeads
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class HabitWithStatus(
    val habit: HabitEntity,
    val isCompletedToday: Boolean,
    val streak: Int
)

/** A habit or a task on the Habits page, with its checklist. */
data class PlanItem(
    val task: TaskEntity? = null,
    val habit: HabitEntity? = null,
    val done: Boolean,
    val skipped: Boolean = false,
    val streak: Int = 0,
    val steps: List<ChecklistItemEntity> = emptyList(),
    val stepsDone: Int = 0
) {
    val key: String get() = task?.let { "task_${it.id}" } ?: "habit_${habit!!.id}"
    val name: String get() = task?.name ?: habit!!.name
    val area: String get() = task?.groupName ?: habit!!.groupName
    val time: LocalTime? get() = task?.let { TaskTiming.timeOfDay(it) } ?: habit?.let { TaskTiming.timeOfDay(it) }
    /** Steps tick off one by one: habits and repeating tasks each day, a one-time task until it's done. */
    val repeats: Boolean get() = habit != null || task?.isRecurring == true
}

/** A folder on the Habits page: an area, the habits and tasks with no area, or the shopping list. */
data class PlanFolder(
    val key: String,
    val title: String,
    val emoji: String,
    val colorHex: String?,
    val items: List<PlanItem>,
    val area: AreaEntity? = null
) {
    val doneCount: Int get() = items.count { it.done }
}

/** What the chips at the top show. */
sealed class PlanFilter(val label: String) {
    data object All : PlanFilter("All")
    data object Habits : PlanFilter("Habits")
    data object Tasks : PlanFilter("Tasks")
    data object Shopping : PlanFilter("Shopping")
    data class Area(val name: String) : PlanFilter(name)
}

data class HabitsUiState(
    val filter: PlanFilter = PlanFilter.All,
    val areas: List<AreaEntity> = emptyList(),
    val folders: List<PlanFolder> = emptyList(),
    val notToday: List<PlanItem> = emptyList(),   // habits and repeating tasks not due today, tasks planned later
    val doneBefore: List<PlanItem> = emptyList(), // one-time tasks finished on an earlier day
    val loaded: Boolean = false
)

/** Today's habits and tasks together, in folders by area. */
@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
    private val taskRepo: TaskRepository,
    private val areaRepo: AreaRepository,
    private val habitManager: HabitManager,
    private val taskCompletionManager: TaskCompletionManager,
    private val checklistManager: ChecklistManager,
    private val trackerManager: TrackerManager,
    checklistDao: ChecklistDao,
    skipDao: DaySkipDao,
    trackerRepo: TrackerRepository,
    userRepo: UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val filter = MutableStateFlow<PlanFilter>(PlanFilter.All)

    /** Last day of the 100-day challenge, offered as a "repeat until" option. */
    val challengeEnd: StateFlow<LocalDate?> = trackerRepo.observeChallenge()
        .map { c -> c?.startDate?.let { runCatching { LocalDate.parse(it).plusDays(99) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The savings currency, for shopping prices. */
    val currency: StateFlow<String> = trackerRepo.observeChallenge()
        .map { it?.currency.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** e.g. "1 h, 15 min and 5 min before": when deadline reminders come. */
    val deadlineReminders: StateFlow<String> = userRepo.observeUser()
        .map { com.habitgame.domain.notification.ReminderTimes.describe((it ?: UserEntity()).deadlineReminderLeads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1 h, 15 min and 5 min before")

    private data class Inputs(
        val habits: List<HabitEntity>,
        val tasks: List<TaskEntity>,
        val skips: Set<String>,
        val steps: Map<String, List<ChecklistItemEntity>>,
        val areas: List<AreaEntity>
    )

    val state: StateFlow<HabitsUiState> = run {
        val today = LocalDate.now().toString()
        val inputs = combine(
            habitRepo.observeActiveHabits(),
            taskRepo.observeAllTasks(),
            habitRepo.observeCompletionsForDate(today),   // re-runs when a habit is ticked
            skipDao.observeForDate(today),
            checklistDao.observeAll()
        ) { habits, tasks, _, skips, steps ->
            Inputs(habits, tasks, skips.map { it.key }.toSet(), steps.groupBy { it.ownerKey }, emptyList())
        }.combine(areaRepo.areas) { inputs, areas -> inputs.copy(areas = areas) }
        inputs.combine(filter) { inputs, filter -> build(inputs, filter) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())
    }

    private suspend fun build(inputs: Inputs, filter: PlanFilter): HabitsUiState {
        val today = LocalDate.now()
        val iso = today.toString()
        fun steps(kind: String, id: Long) = inputs.steps["$kind:$id"].orEmpty()

        suspend fun habitItem(habit: HabitEntity): PlanItem {
            val done = habitRepo.isHabitCompletedToday(habit.id)
            val steps = steps(DaySkipEntity.KIND_HABIT, habit.id)
            return PlanItem(
                habit = habit, done = done,
                skipped = DaySkipEntity.key(DaySkipEntity.KIND_HABIT, habit.id, iso) in inputs.skips,
                streak = habitRepo.getHabitStreak(habit.id),
                steps = steps, stepsDone = Checklist.progress(steps, true, iso)
            )
        }
        fun taskItem(task: TaskEntity): PlanItem {
            val steps = steps(DaySkipEntity.KIND_TASK, task.id)
            return PlanItem(
                task = task,
                done = if (task.isRecurring) TaskTiming.completedOn(task, today) else task.isCompleted,
                skipped = DaySkipEntity.key(DaySkipEntity.KIND_TASK, task.id, iso) in inputs.skips,
                steps = steps, stepsDone = Checklist.progress(steps, task.isRecurring, iso)
            )
        }

        val (habitsToday, habitsLater) = inputs.habits.partition { TaskTiming.isDueOn(it, today) }
        // Today's tasks: due today, done today, overdue, or any-time ones still open
        fun overdue(task: TaskEntity) = !task.isRecurring && !task.isCompleted && TaskTiming.assignedDate(task)?.isBefore(today) == true
        val shopping = inputs.tasks.filter { it.isShopping && (!it.isCompleted || TaskTiming.completedOn(it, today)) }
        val tasksToday = inputs.tasks.filter { task ->
            !task.isShopping && (TaskTiming.countsToward(task, today) || TaskTiming.completedOn(task, today) || overdue(task) ||
                (TaskTiming.isAnytime(task) && !task.isCompleted))
        }
        val doneBefore = inputs.tasks.filter { !it.isRecurring && it.isCompleted && !TaskTiming.completedOn(it, today) }
        val tasksLater = inputs.tasks - tasksToday.toSet() - shopping.toSet() - doneBefore.toSet()

        val order = compareBy<PlanItem>({ it.done }, { it.time == null }, { it.time })
        val showHabits = filter !is PlanFilter.Tasks && filter !is PlanFilter.Shopping
        val showTasks = filter !is PlanFilter.Habits && filter !is PlanFilter.Shopping
        val todayItems = (if (showHabits) habitsToday.map { habitItem(it) } else emptyList()) +
            (if (showTasks) tasksToday.map { taskItem(it) } else emptyList())
        val byArea = todayItems.groupBy { it.area }

        val folders = mutableListOf<PlanFolder>()
        if (filter !is PlanFilter.Shopping) {
            inputs.areas.forEach { area ->
                if (filter is PlanFilter.Area && filter.name != area.name) return@forEach
                val items = byArea[area.name].orEmpty().sortedWith(order)
                // Empty areas show under All (and when picked), so a new one can be filled
                if (items.isNotEmpty() || filter is PlanFilter.All || filter is PlanFilter.Area) {
                    folders += PlanFolder("area_${area.name}", area.name, area.iconEmoji, area.colorHex, items, area)
                }
            }
            val loose = byArea[""].orEmpty().sortedWith(order)
            if (loose.isNotEmpty() && filter !is PlanFilter.Area) folders += PlanFolder("other", "No area", "📋", null, loose)
        }
        if ((filter is PlanFilter.All || filter is PlanFilter.Shopping) && (shopping.isNotEmpty() || filter is PlanFilter.Shopping)) {
            folders += PlanFolder("shopping", "Shopping list", "🛒", null, shopping.map { taskItem(it) }.sortedWith(compareBy({ it.done }, { it.task?.deadlineAt ?: Long.MAX_VALUE })))
        }

        fun inFilter(item: PlanItem): Boolean = when (filter) {
            PlanFilter.All -> true
            PlanFilter.Habits -> item.habit != null
            PlanFilter.Tasks -> item.task?.isShopping == false
            PlanFilter.Shopping -> item.task?.isShopping == true
            is PlanFilter.Area -> item.area == filter.name
        }
        val later = (habitsLater.map { PlanItem(habit = it, done = false) } + tasksLater.map { taskItem(it) })
            .filter(::inFilter).sortedWith(order)
        val before = doneBefore.map { taskItem(it) }.filter(::inFilter).sortedByDescending { it.task?.completedAt ?: 0L }

        return HabitsUiState(filter, inputs.areas, folders, later, before, loaded = true)
    }

    fun setFilter(value: PlanFilter) { filter.value = value }

    // ── Ticking ──────────────────────────────────────────────────────────────

    /** Ticks a habit (or unticks it) or completes a task. A checklist ticks along with it. */
    fun toggle(item: PlanItem) {
        viewModelScope.launch {
            item.habit?.let { habit ->
                if (habitRepo.isHabitCompletedToday(habit.id)) habitManager.uncompleteHabit(habit.id) else habitManager.completeHabit(habit)
            }
            item.task?.let { task -> if (!item.done) taskCompletionManager.complete(task) }
        }
    }

    fun toggleStep(step: ChecklistItemEntity) {
        viewModelScope.launch { checklistManager.toggle(step.id) }
    }

    // ── Habits ───────────────────────────────────────────────────────────────

    fun addHabit(habit: HabitEntity, steps: List<String>) {
        viewModelScope.launch {
            areaRepo.ensure(habit.groupName)
            val id = habitRepo.insertHabit(habit)
            checklistManager.save(DaySkipEntity.KIND_HABIT, id, steps)
            trackerManager.scheduleReminder(habit.copy(id = id))
            trackerManager.snapshotToday()
        }
    }

    fun updateHabit(habit: HabitEntity, steps: List<String>) {
        viewModelScope.launch {
            areaRepo.ensure(habit.groupName)
            checklistManager.save(DaySkipEntity.KIND_HABIT, habit.id, steps)
            trackerManager.saveHabit(habit)
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepo.deleteHabit(habit)
            checklistManager.delete(DaySkipEntity.KIND_HABIT, habit.id)
            HabitReminderWorker.cancelReminders(context, habit.id)
            HabitReminderWorker.dismissNotifications(context, habit.id)
            trackerManager.snapshotToday()
        }
    }

    // ── Tasks ────────────────────────────────────────────────────────────────

    fun addTask(task: TaskEntity, steps: List<String>) {
        viewModelScope.launch {
            areaRepo.ensure(task.groupName)
            val id = taskRepo.insertTask(task)
            checklistManager.save(DaySkipEntity.KIND_TASK, id, steps)
            trackerManager.scheduleReminder(task.copy(id = id))
            trackerManager.snapshotToday()
        }
    }

    fun updateTask(task: TaskEntity, steps: List<String>? = null) {
        viewModelScope.launch {
            areaRepo.ensure(task.groupName)
            steps?.let { checklistManager.save(DaySkipEntity.KIND_TASK, task.id, it) }
            trackerManager.saveTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
            checklistManager.delete(DaySkipEntity.KIND_TASK, task.id)
            TaskReminderWorker.cancelReminder(context, task.id)
            trackerManager.snapshotToday()
        }
    }

    // ── Areas ────────────────────────────────────────────────────────────────

    fun saveArea(area: AreaEntity, oldName: String?) {
        viewModelScope.launch {
            areaRepo.save(area, oldName)
            if (oldName != null && (filter.value as? PlanFilter.Area)?.name == oldName) filter.value = PlanFilter.Area(area.name)
        }
    }

    fun deleteArea(name: String) {
        viewModelScope.launch {
            areaRepo.delete(name)
            if ((filter.value as? PlanFilter.Area)?.name == name) filter.value = PlanFilter.All
        }
    }
}
