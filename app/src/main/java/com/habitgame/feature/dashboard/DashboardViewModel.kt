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
    val todayMusic: com.habitgame.domain.music.MusicInfo? = null
) {
    fun dateOf(day: Int): LocalDate = startDate.plusDays((day - 1).toLong())
    val endDate: LocalDate get() = dateOf(TrackerPlan.TOTAL_DAYS)
}

data class DashboardUiState(
    val user: UserEntity = UserEntity(),
    val todayTasks: List<TaskEntity> = emptyList(),
    val anytimeTasks: List<TaskEntity> = emptyList(),   // not done yet and not tied to a day, e.g. shopping
    val todayHabits: List<HabitWithStatus> = emptyList(),
    val xpProgress: Float = 0f,
    val xpRequired: Long = 100,
    val comboCount: Int = 0,
    val showLevelUpDialog: Boolean = false,
    val levelUpTitle: String = "",
    val levelUpLevel: Int = 0,
    val isTierChange: Boolean = false,
    val selectedTab: Int = 0,  // 0 = Tasks, 1 = Habits
    val challenge: ChallengeUi = ChallengeUi(),
    val selectedDay: Int? = null,
    val homeNotes: List<HabitEntity> = emptyList(),  // habits with "Show on Home" turned on
    val sessions: List<com.habitgame.domain.engine.ActiveSession> = emptyList(),  // running Shop timers
    val charts: WeekCharts = WeekCharts()  // this week's XP and savings graphs
)

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
            combine(
                userRepo.observeUser(),
                taskRepo.observeAllTasks(),
                habitRepo.observeActiveHabits()
            ) { user, tasks, allHabits ->
                val u = user ?: UserEntity()
                val today = LocalDate.now()
                val todaysTasks = tasks
                    .filter { TaskTiming.countsToward(it, today) }
                    .sortedWith(compareBy<TaskEntity>({ it.isCompleted }, { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }))
                val anytime = tasks.filter { TaskTiming.isAnytime(it) && !it.isCompleted }.sortedBy { it.deadlineAt ?: Long.MAX_VALUE }
                val habitStatuses = habitManager.filterTodaysHabits(allHabits)
                    .sortedBy { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }
                    .map { habit ->
                    HabitWithStatus(
                        habit = habit,
                        isCompletedToday = habitRepo.isHabitCompletedToday(habit.id),
                        streak = habitRepo.getHabitStreak(habit.id)
                    )
                }
                val notes = allHabits
                    .filter { it.showOnHome && it.description.isNotBlank() && !TaskTiming.repeatRule(it).hasEnded() }
                    .sortedBy { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }
                Triple(u, todaysTasks, habitStatuses) to (notes to anytime)
            }.collect { (data, extra) ->
                val (u, todaysTasks, habitStatuses) = data
                val (notes, anytime) = extra
                _state.update {
                    it.copy(
                        user = u,
                        todayTasks = todaysTasks,
                        anytimeTasks = anytime,
                        todayHabits = habitStatuses,
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
                    todayMusic = journal.firstOrNull { it.date == today.toString() }?.music
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

    fun selectTab(tab: Int) = _state.update { it.copy(selectedTab = tab) }

    fun selectDay(day: Int) = _state.update { it.copy(selectedDay = if (it.selectedDay == day) null else day) }

    fun setStartDate(date: LocalDate) {
        viewModelScope.launch {
            trackerManager.updateChallenge(startDate = date.toString())
            _state.update { it.copy(selectedDay = null) }
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch { trackerManager.saveTask(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
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
