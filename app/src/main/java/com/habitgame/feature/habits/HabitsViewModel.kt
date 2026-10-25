package com.habitgame.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.engine.HabitCompletionResult
import com.habitgame.domain.engine.HabitManager
import com.habitgame.domain.engine.UserProgressEngine
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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

/** A titled group of today's habits, e.g. "Weight gain". */
data class HabitSection(val title: String, val habits: List<HabitWithStatus>)

data class HabitsUiState(
    val sections: List<HabitSection> = emptyList(),
    val notToday: List<HabitEntity> = emptyList(),
    val showLevelUp: Boolean = false,
    val levelUpTitle: String = "",
    val levelUpLevel: Int = 0,
    val isTierChange: Boolean = false,
    val completionResult: HabitCompletionResult? = null
) {
    val habits: List<HabitWithStatus> get() = sections.flatMap { it.habits }
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
    private val habitManager: HabitManager,
    private val progressEngine: UserProgressEngine,
    private val trackerManager: TrackerManager,
    trackerRepo: TrackerRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()

    /** Last day of the 100-day challenge, offered as a "repeat until" option. */
    val challengeEnd: StateFlow<LocalDate?> = trackerRepo.observeChallenge()
        .map { c -> c?.startDate?.let { runCatching { LocalDate.parse(it).plusDays(99) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _refresh = MutableStateFlow(0)

    init { observeHabits() }

    private fun observeHabits() {
        viewModelScope.launch {
            combine(
                habitRepo.observeActiveHabits(),
                _refresh
            ) { habits, _ ->
                val today = LocalDate.now()
                val byTime = compareBy<HabitEntity> { TaskTiming.timeOfDay(it) ?: LocalTime.MAX }
                val (due, notDue) = habits.partition { TaskTiming.isDueOn(it, today) }
                val sections = due
                    .sortedWith(byTime)
                    .map { habit ->
                        HabitWithStatus(
                            habit = habit,
                            isCompletedToday = habitRepo.isHabitCompletedToday(habit.id),
                            streak = habitRepo.getHabitStreak(habit.id)
                        )
                    }
                    .groupBy { it.habit.groupName }
                    .entries
                    .sortedBy { it.key.isNotEmpty() }  // ungrouped habits first, then e.g. "Weight gain"
                    .map { (group, list) -> HabitSection(group.ifEmpty { "Today" }, list) }
                sections to notDue.sortedWith(byTime)
            }.collect { (sections, notToday) ->
                _state.update { it.copy(sections = sections, notToday = notToday) }
            }
        }
    }

    fun toggleHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val isCompleted = habitRepo.isHabitCompletedToday(habit.id)
            if (isCompleted) {
                habitManager.uncompleteHabit(habit.id)
            } else {
                val result = habitManager.completeHabit(habit)
                if (result.didLevelUp) {
                    val previousTitle = UserProgressEngine.titleForLevel(result.newLevel - 1)
                    _state.update { it.copy(
                        showLevelUp = true,
                        levelUpTitle = result.newTitle,
                        levelUpLevel = result.newLevel,
                        isTierChange = previousTitle != result.newTitle,
                        completionResult = result
                    ) }
                }
            }
            _refresh.value++
        }
    }

    fun addHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val id = habitRepo.insertHabit(habit)
            trackerManager.scheduleReminder(habit.copy(id = id))
            trackerManager.snapshotToday()
        }
    }

    fun updateHabit(habit: HabitEntity) {
        viewModelScope.launch {
            trackerManager.saveHabit(habit)
            _refresh.value++
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            habitRepo.deleteHabit(habit)
            HabitReminderWorker.cancelReminders(context, habit.id)
            HabitReminderWorker.dismissNotifications(context, habit.id)
            trackerManager.snapshotToday()
        }
    }

    fun dismissLevelUp() = _state.update { it.copy(showLevelUp = false, completionResult = null) }
}
