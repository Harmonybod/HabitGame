package com.habitgame.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.*
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.LocationZoneRepository
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.notification.deadlineReminderLeads
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val filter: String = "All"
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val locationZoneRepo: LocationZoneRepository,
    private val trackerManager: TrackerManager,
    private val taskCompletionManager: TaskCompletionManager,
    trackerRepo: com.habitgame.core.database.repository.TrackerRepository,
    userRepo: com.habitgame.core.database.repository.UserRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** The savings currency, used for shopping price ranges. */
    val currency: StateFlow<String> = trackerRepo.observeChallenge()
        .map { it?.currency.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** e.g. "1 h, 15 min and 5 min before": when shopping deadline reminders come (Settings → Notifications). */
    val deadlineReminderText: StateFlow<String> = userRepo.observeUser()
        .map { com.habitgame.domain.notification.ReminderTimes.describe((it ?: UserEntity()).deadlineReminderLeads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1 h, 15 min and 5 min before")

    /** Last day of the 100-day challenge, offered as a "repeat until" option. */
    val challengeEnd: StateFlow<java.time.LocalDate?> = trackerRepo.observeChallenge()
        .map { c -> c?.startDate?.let { runCatching { java.time.LocalDate.parse(it).plusDays(99) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch { trackerManager.saveTask(task) }
    }

    private val _filter = MutableStateFlow("All")
    val state: StateFlow<TasksUiState> = combine(
        taskRepo.observeAllTasks(), _filter
    ) { tasks, filter ->
        val filtered = when (filter) {
            "Pending"   -> tasks.filter { !it.isCompleted }
            "Done"      -> tasks.filter { it.isCompleted }
            "Shopping"  -> tasks.filter { it.isShopping }
            "Repeating" -> tasks.filter { it.isRecurring }
            "Activity"  -> tasks.filter { it.categoryType == TaskCategoryType.ACTIVITY }
            "Location"  -> tasks.filter { it.categoryType == TaskCategoryType.LOCATION }
            else        -> tasks
        }
        TasksUiState(tasks = filtered, filter = filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    val locationZones: StateFlow<List<LocationZoneEntity>> = locationZoneRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: String) { _filter.value = filter }

    fun saveLocationZone(name: String, lat: Double, lng: Double, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val zone = LocationZoneEntity(name = name, latitude = lat, longitude = lng)
            val id = locationZoneRepo.insert(zone)
            onSaved(id)
        }
    }

    fun addTask(task: TaskEntity) {
        viewModelScope.launch {
            val id = taskRepo.insertTask(task)
            trackerManager.scheduleReminder(task.copy(id = id))
            trackerManager.snapshotToday()
        }
    }

    fun completeTask(task: TaskEntity) {
        viewModelScope.launch { taskCompletionManager.complete(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepo.deleteTask(task)
            TaskReminderWorker.cancelReminder(context, task.id)
            trackerManager.snapshotToday()
        }
    }
}
