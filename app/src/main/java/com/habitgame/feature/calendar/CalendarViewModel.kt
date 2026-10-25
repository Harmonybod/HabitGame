package com.habitgame.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.domain.engine.HabitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksForDate: List<TaskEntity> = emptyList(),
    val habitsForDate: List<HabitEntity> = emptyList(),
    val completedHabitIds: Set<Long> = emptySet()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val habitRepo: HabitRepository,
    private val habitManager: HabitManager
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init { selectDate(LocalDate.now()) }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }

        viewModelScope.launch {
            // Combine one-time tasks on that exact date + repeating tasks scheduled for that day-of-week
            combine(
                taskRepo.observeTasksByDate(date.toMillis()),
                taskRepo.observeRepeatingTasks()
            ) { exactTasks, repeatingTasks ->
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() // e.g. "MON"
                val matchingRepeating = repeatingTasks.filter { task ->
                    task.recurrenceDays.split(",").map { it.trim() }.contains(dayName)
                }
                // Merge, removing duplicates by id
                val combined = (exactTasks + matchingRepeating).distinctBy { it.id }
                combined
            }.collect { tasks ->
                _state.update { it.copy(tasksForDate = tasks) }
            }
        }

        viewModelScope.launch {
            habitRepo.observeActiveHabits().collect { allHabits ->
                val today = LocalDate.now()
                val dayHabits = habitManager.filterHabitsForDate(allHabits, date)
                val completedIds = if (date.isAfter(today)) {
                    // Future dates: always uncompleted
                    emptySet()
                } else {
                    // Past/today: check actual completion in DB
                    dayHabits.filter { habit ->
                        habitRepo.isHabitCompletedOnDate(habit.id, date)
                    }.map { it.id }.toSet()
                }
                _state.update { it.copy(habitsForDate = dayHabits, completedHabitIds = completedIds) }
            }
        }
    }

    fun changeMonth(delta: Int) {
        val newMonth = _state.value.currentMonth.plusMonths(delta.toLong())
        _state.update { it.copy(currentMonth = newMonth) }
    }

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
