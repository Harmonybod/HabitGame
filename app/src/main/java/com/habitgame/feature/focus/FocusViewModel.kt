package com.habitgame.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.domain.focus.FocusPlan
import com.habitgame.domain.focus.FocusTimer
import com.habitgame.domain.focus.FocusTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Focus screen: picks the plan (focus length, break, rounds) and drives the shared [FocusTimer]. */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val timer: FocusTimer,
    private val taskRepo: TaskRepository
) : ViewModel() {

    val timerState: StateFlow<FocusTimerState> = timer.state

    private val _plan = MutableStateFlow(timer.lastPlan)
    val plan: StateFlow<FocusPlan> = _plan.asStateFlow()

    /** The task the session was opened from, if any. */
    private val _taskName = MutableStateFlow<String?>(null)
    val taskName: StateFlow<String?> = _taskName.asStateFlow()
    private var taskId: Long? = null

    /** Opened from a task's Start button: use that task's focus length. */
    fun setLinkedTaskId(id: Long?) {
        taskId = id
        if (id == null) {
            _taskName.value = null
            return
        }
        viewModelScope.launch {
            val task = taskRepo.getTaskById(id) ?: return@launch
            _taskName.value = task.name
            if (task.focusDurationMinutes > 0 && timer.state.value.run == null) {
                _plan.update { it.copy(focusMinutes = task.focusDurationMinutes) }
            }
        }
    }

    fun setFocusMinutes(minutes: Int) = _plan.update { it.copy(focusMinutes = minutes).valid() }
    fun setBreakMinutes(minutes: Int) = _plan.update { it.copy(breakMinutes = minutes).valid() }
    fun setRounds(rounds: Int) = _plan.update { it.copy(rounds = rounds).valid() }

    fun start() = timer.start(_plan.value, taskId)
    fun pause() { timer.pause() }
    fun resume() { timer.resume() }
    fun skipBreak() { timer.skipBreak() }
    fun stop() = timer.stop()
    fun dismissResult() = timer.dismissResult()
}
