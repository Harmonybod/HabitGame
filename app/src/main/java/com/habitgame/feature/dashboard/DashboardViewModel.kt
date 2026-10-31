package com.habitgame.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.engine.HabitManager
import com.habitgame.domain.engine.LevelUpResult
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.engine.UserProgressEngine
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.feature.habits.HabitWithStatus
import com.habitgame.core.database.entity.ActivityCategory
import com.habitgame.ui.components.WeekCharts
import com.habitgame.domain.music.music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** The 100-day challenge as shown on the Home screen. */
data class ChallengeUi(
    val startDate: LocalDate = LocalDate.now(),
    val currentDay: Int = 1,
    val scores: List<Float> = List(TrackerPlan.TOTAL_DAYS) { 0f },
    val savedTotal: Double = 0.0,
    val currency: String = "",
    val journalEntries: Int = 0,
    val todayMusic: com.habitgame.domain.music.MusicInfo? = null,
    val savingsGoal: Double = 0.0,
    val savingsDays: List<com.habitgame.domain.tracker.SavingsDay> = emptyList()   // saved vs the plan, day 1 to 100
) {
    fun dateOf(day: Int): LocalDate = startDate.plusDays((day - 1).toLong())
    val endDate: LocalDate get() = dateOf(TrackerPlan.TOTAL_DAYS)
}

/** One row of today's list on Home: a task or a habit, in time order. */
data class DayItem(
    val task: TaskEntity? = null,
    val habit: HabitWithStatus? = null,
    val done: Boolean,
    val skipped: Boolean
) {
    val key: String get() = task?.let { "task_${it.id}" } ?: "habit_${habit!!.habit.id}"
    val time: LocalTime? get() = task?.let { TaskTiming.timeOfDay(it) } ?: habit?.let { TaskTiming.timeOfDay(it.habit) }
    val name: String get() = task?.name ?: habit!!.habit.name

    companion object {
        /** By time of day; ones without a time come last. */
        val ORDER: Comparator<DayItem> = compareBy({ it.time == null }, { it.time })
    }
}

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val dayItems: List<DayItem> = emptyList(),          // today's tasks and habits, by time
    val anytimeTasks: List<TaskEntity> = emptyList(),   // not done yet and not tied to a day, e.g. shopping
    val xpProgress: Float = 0f,
    val xpRequired: Long = 100,
    val comboCount: Int = 0,
    val showLevelUpDialog: Boolean = false,
    val levelUpTitle: String = "",
    val levelUpLevel: Int = 0,
    val isTierChange: Boolean = false,
    val challenge: ChallengeUi = ChallengeUi(),
    val selectedDay: Int? = null,
    val homeNotes: List<HabitEntity> = emptyList(),  // habits with "Show on Home" turned on
    val sessions: List<com.habitgame.domain.engine.ActiveSession> = emptyList(),  // running Shop timers
    val charts: WeekCharts = WeekCharts(),  // this week's XP and savings graphs
    val areas: List<com.habitgame.core.database.entity.AreaEntity> = emptyList(),
    val steps: Map<String, List<com.habitgame.core.database.entity.ChecklistItemEntity>> = emptyMap()  // checklists by "task:3" / "habit:5"
) {
    /** "2 of 5" for a row's checklist, or null without one. */
    fun stepsOf(kind: String, id: Long, repeats: Boolean): Pair<Int, Int>? =
        steps["$kind:$id"]?.takeIf { it.isNotEmpty() }?.let {
            com.habitgame.domain.engine.Checklist.progress(it, repeats, LocalDate.now().toString()) to it.size
        }

    fun stepsFor(item: DayItem): Pair<Int, Int>? =
        item.task?.let { stepsOf("task", it.id, it.isRecurring) } ?: item.habit?.let { stepsOf("habit", it.habit.id, true) }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val trackerRepo: TrackerRepository,
    private val progressEngine: UserProgressEngine,
    private val economyManager: EconomyManager,
    private val habitManager: HabitManager,
    private val taskCompletionManager: TaskCompletionManager,
    private val trackerManager: TrackerManager,
    private val boosterManager: com.habitgame.domain.engine.BoosterManager,
    private val focusRepo: com.habitgame.core.database.repository.FocusSessionRepository,
    private val focusTimer: com.habitgame.domain.focus.FocusTimer,
    private val skipDao: com.habitgame.core.database.dao.DaySkipDao,
    private val areaRepo: com.habitgame.core.database.repository.AreaRepository,
    private val checklistManager: com.habitgame.domain.engine.ChecklistManager,
    checklistDao: com.habitgame.core.database.dao.ChecklistDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    private val _chartWeek = MutableStateFlow(WeekCharts.startOfWeek(LocalDate.now()))  // Sunday of the week the graphs show
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    /** A running focus session (shown at the top of Home) or one that just finished. */
    val focus: StateFlow<com.habitgame.domain.focus.FocusTimerState> = focusTimer.state

    fun pauseFocus() { focusTimer.pause() }
    fun resumeFocus() { focusTimer.resume() }
    fun dismissFocusResult() = focusTimer.dismissResult()

    init {
        viewModelScope.launch { trackerManager.runDailyMaintenance() }
        observeData()
        observeChallenge()
        observeWeekCharts()
        viewModelScope.launch {
            boosterManager.observeActiveSessions().collect { sessions -> _state.update { it.copy(sessions = sessions) } }
        }
        viewModelScope.launch { areaRepo.areas.collect { areas -> _state.update { it.copy(areas = areas) } } }
        viewModelScope.launch {
            checklistDao.observeAll().collect { items -> _state.update { it.copy(steps = items.groupBy { item -> item.ownerKey }) } }
        }
    }

    /** Called by the screen when a countdown reaches zero. */
    /** 5 more hours of a No Restriction Day that's nearly over, for 50 coins. */
    fun extendSession(session: com.habitgame.domain.engine.ActiveSession) {
        viewModelScope.launch {
            val message = com.habitgame.domain.notification.SessionEndWorker.extend(context, economyManager, boosterManager, session)
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun endFinishedSessions() {
        viewModelScope.launch {
            com.habitgame.domain.notification.SessionEndWorker.notifyEnded(context, boosterManager.endFinishedSessions())
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            val today = LocalDate.now()
            combine(
                userRepo.observeUser(),
                taskRepo.observeAllTasks(),
                habitRepo.observeActiveHabits(),
                habitRepo.observeCompletionsForDate(today.toString()),
                skipDao.observeForDate(today.toString())
            ) { user, tasks, allHabits, _, skips ->
                val u = user ?: UserEntity()
                val skipped = skips.map { it.key }.toSet()
                fun isSkipped(kind: String, id: Long) =
                    com.habitgame.core.database.entity.DaySkipEntity.key(kind, id, today.toString()) in skipped
                // Today's tasks and habits in one list, by time
                val taskItems = tasks.filter { TaskTiming.countsToward(it, today) }.map { task ->
                    DayItem(task = task, done = TaskTiming.completedOn(task, today), skipped = isSkipped("task", task.id))
                }
                val habitItems = habitManager.filterTodaysHabits(allHabits).map { habit ->
                    val done = habitRepo.isHabitCompletedToday(habit.id)
                    DayItem(
                        habit = HabitWithStatus(habit = habit, isCompletedToday = done, streak = habitRepo.getHabitStreak(habit.id)),
                        done = done, skipped = isSkipped("habit", habit.id)
                    )
                }
                val dayItems = (taskItems + habitItems).sortedWith(DayItem.ORDER)
                val anytime = tasks.filter { TaskTiming.isAnytime(it) && !it.isCompleted }.sortedBy { it.deadlineAt ?: Long.MAX_VALUE }
                val notes = allHabits
                    .filter { it.showOnHome && it.description.isNotBlank() && !TaskTiming.repeatRule(it).hasEnded() }
                    .sortedBy { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }
                Triple(u, dayItems, notes) to anytime
            }.collect { (data, anytime) ->
                val (u, dayItems, notes) = data
                _state.update {
                    it.copy(
                        user = u,
                        dayItems = dayItems,
                        anytimeTasks = anytime,
                        homeNotes = notes,
                        xpProgress = progressEngine.progressFraction(u),
                        xpRequired = UserProgressEngine.xpRequired(u.level),
                        comboCount = economyManager.currentCombo()
                    )
                }
            }
        }
    }

    private fun observeChallenge() {
        viewModelScope.launch {
            combine(
                trackerRepo.observeChallenge(),
                trackerRepo.observeDayProgress(),
                trackerRepo.observeSavings(),
                trackerRepo.observeJournal()
            ) { challenge, progress, savings, journal ->
                val today = LocalDate.now()
                val start = challenge?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
                val byDate = progress.associateBy { it.date }
                ChallengeUi(
                    startDate = start,
                    currentDay = TrackerManager.dayNumber(start.toString(), today),
                    scores = List(TrackerPlan.TOTAL_DAYS) { i -> byDate[start.plusDays(i.toLong()).toString()]?.fraction ?: 0f },
                    savedTotal = savings.sumOf { it.amount },
                    currency = challenge?.currency.orEmpty(),
                    journalEntries = journal.count { it.text.isNotBlank() || it.mood != null },
                    todayMusic = journal.firstOrNull { it.date == today.toString() }?.music,
                    savingsGoal = challenge?.savingsGoal ?: 0.0,
                    savingsDays = com.habitgame.domain.tracker.ProgressStats.savings(start, challenge?.savingsGoal ?: 0.0, savings, today)
                )
            }.collect { ui -> _state.update { it.copy(challenge = ui) } }
        }
    }

    /** Shows the graphs for the week (Sunday to Saturday) that [date] falls in. */
    fun showWeekOf(date: LocalDate) {
        _chartWeek.value = WeekCharts.startOfWeek(date)
    }

    /** XP earned and money saved each day of the chosen week, Sunday to Saturday. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeWeekCharts() = viewModelScope.launch {
        _chartWeek.flatMapLatest { weekStart ->
            val days = (0..6).map { weekStart.plusDays(it.toLong()).toString() }
            combine(
                trackerRepo.observeDailyXp(days.first(), days.last()),
                trackerRepo.observeSavings(),
                trackerRepo.observeChallenge(),
                taskRepo.observeAllTasks(),
                focusRepo.observeDailyFocus(days.first(), days.last())
            ) { xp, savings, challenge, tasks, focus ->
                val xpByDay = xp.associate { it.date to it.xp }
                val focusByDay = focus.associate { it.date to it.minutes.toInt() }
                val week = savings.filter { it.date in days }
                // The daily amount the user set: their repeating savings tasks, or the goal spread over 100 days
                val planned = tasks
                    .filter { it.activityCategory == ActivityCategory.SAVINGS && it.isRecurring && !TaskTiming.repeatRule(it).hasEnded() }
                    .sumOf { it.savingAmount }
                    .takeIf { it > 0 } ?: trackerManager.dailySavingFor(challenge?.savingsGoal ?: 0.0)
                WeekCharts(
                    weekStart = weekStart,
                    xp = days.map { xpByDay[it] ?: 0 },
                    focusMinutes = days.map { focusByDay[it] ?: 0 },
                    savedPlanned = days.map { d -> week.filter { it.date == d && it.taskId != null }.sumOf { it.amount } },
                    savedExtra = days.map { d -> week.filter { it.date == d && it.taskId == null }.sumOf { it.amount } },
                    dailyTarget = planned,
                    currency = challenge?.currency.orEmpty()
                )
            }
        }.collect { charts -> _state.update { it.copy(charts = charts) } }
    }

    /** Swiped left to right: done. Habits already done stay done. */
    fun completeItem(item: DayItem) {
        item.task?.let { if (!it.isCompleted) completeTask(it) }
        item.habit?.let { if (!it.isCompletedToday) toggleHabit(it.habit) }
    }

    /** Swiped right to left (and confirmed): skipped for today. */
    fun skipItem(item: DayItem) {
        viewModelScope.launch {
            item.task?.let { trackerManager.skip(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, it.id) }
            item.habit?.let { trackerManager.skip(com.habitgame.core.database.entity.DaySkipEntity.KIND_HABIT, it.habit.id) }
        }
    }

    fun unskipItem(item: DayItem) {
        viewModelScope.launch {
            item.task?.let { trackerManager.unskip(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, it.id) }
            item.habit?.let { trackerManager.unskip(com.habitgame.core.database.entity.DaySkipEntity.KIND_HABIT, it.habit.id) }
        }
    }

    fun selectDay(day: Int) = _state.update { it.copy(selectedDay = if (it.selectedDay == day) null else day) }

    fun setStartDate(date: LocalDate) {
        viewModelScope.launch {
            trackerManager.updateChallenge(startDate = date.toString())
            _state.update { it.copy(selectedDay = null) }
        }
    }

    fun updateTask(task: TaskEntity, steps: List<String>? = null) {
        viewModelScope.launch {
            areaRepo.ensure(task.groupName)
            steps?.let { checklistManager.save(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id, it) }
            trackerManager.saveTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
            checklistManager.delete(com.habitgame.core.database.entity.DaySkipEntity.KIND_TASK, task.id)
            com.habitgame.domain.notification.TaskReminderWorker.cancelReminder(context, task.id)
            trackerManager.snapshotToday()
        }
    }

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch {
            val oldTitle = _state.value.user.title
            taskCompletionManager.complete(task)?.let { showLevelUpIfNeeded(it, oldTitle) }
        }
    }

    fun toggleHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val isCompleted = habitRepo.isHabitCompletedToday(habit.id)
            if (isCompleted) {
                habitManager.uncompleteHabit(habit.id)
            } else {
                val oldTitle = _state.value.user.title
                val result = habitManager.completeHabit(habit)
                if (result.didLevelUp) {
                    _state.update { it.copy(
                        showLevelUpDialog = true,
                        levelUpTitle = result.newTitle,
                        levelUpLevel = result.newLevel,
                        isTierChange = oldTitle != result.newTitle
                    ) }
                }
            }
        }
    }

    private fun showLevelUpIfNeeded(result: LevelUpResult, oldTitle: String) {
        if (!result.didLevelUp) return
        _state.update { it.copy(
            showLevelUpDialog = true,
            levelUpTitle = result.newTitle,
            levelUpLevel = result.newLevel,
            isTierChange = oldTitle != result.newTitle
        ) }
    }

    fun dismissLevelUp() = _state.update { it.copy(showLevelUpDialog = false) }
}
