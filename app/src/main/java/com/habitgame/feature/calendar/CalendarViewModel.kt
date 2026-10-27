package com.habitgame.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.domain.tracker.TaskTiming
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksForDate: List<TaskEntity> = emptyList(),
    val doneTaskIds: Set<Long> = emptySet(),
    val habitsForDate: List<HabitEntity> = emptyList(),
    val completedHabitIds: Set<Long> = emptySet()
)

/** The calendar: a month to pick from, and the picked day's tasks and habits. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    private val selected = MutableStateFlow(LocalDate.now())

    init {
        // Only the picked day is followed: picking another stops the old one
        viewModelScope.launch {
            selected.flatMapLatest { date ->
                combine(taskRepo.observeAllTasks(), habitRepo.observeActiveHabits()) { tasks, habits -> Triple(date, tasks, habits) }
            }.mapLatest { (date, tasks, habits) ->
                // The day's own tasks (and repeats), anything finished that day, and deadlines falling on it
                val dayTasks = tasks.filter { task ->
                    TaskTiming.isDueOn(task, date) || TaskTiming.completedOn(task, date) ||
                        (!task.isCompleted && task.deadlineAt?.let { TaskTiming.localDateOf(it) } == date)
                }.sortedWith(compareBy({ TaskTiming.timeOfDay(it) == null }, { TaskTiming.timeOfDay(it) }))
                val dayHabits = habits.filter { TaskTiming.isDueOn(it, date) }
                val doneHabits = if (date.isAfter(LocalDate.now())) emptySet()
                    else dayHabits.filter { habitRepo.isHabitCompletedOnDate(it.id, date) }.map { it.id }.toSet()
                _state.value.copy(
                    selectedDate = date,
                    tasksForDate = dayTasks,
                    doneTaskIds = dayTasks.filter { TaskTiming.completedOn(it, date) }.map { it.id }.toSet(),
                    habitsForDate = dayHabits,
                    completedHabitIds = doneHabits
                )
            }.collect { newState -> _state.update { newState.copy(currentMonth = it.currentMonth) } }
        }
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
        _state.update { it.copy(selectedDate = date) }
    }

    fun changeMonth(delta: Int) {
        val newMonth = _state.value.currentMonth.plusMonths(delta.toLong())
        _state.update { it.copy(currentMonth = newMonth) }
    }
}
