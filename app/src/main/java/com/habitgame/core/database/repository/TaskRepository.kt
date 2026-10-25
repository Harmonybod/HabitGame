package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.TaskDao
import com.habitgame.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(private val dao: TaskDao) {
    fun observeAllTasks(): Flow<List<TaskEntity>> = dao.observeAllTasks()
    fun observePendingTasks(): Flow<List<TaskEntity>> = dao.observePendingTasks()
    suspend fun getTaskById(id: Long): TaskEntity? = dao.getTaskById(id)
    suspend fun insertTask(task: TaskEntity): Long = dao.insertTask(task)
    suspend fun updateTask(task: TaskEntity) = dao.updateTask(task)
    suspend fun completeTask(id: Long) = dao.completeTask(id)
    suspend fun deleteTask(task: TaskEntity) = dao.deleteTask(task)
    suspend fun getCompletedTaskCount(): Int = dao.getCompletedTaskCount()
    fun observeTasksByDate(dateMillis: Long) = dao.observeTasksByDate(dateMillis)
    fun observeRepeatingTasks() = dao.observeRepeatingTasks()
    // Phase 2
    suspend fun getIncompleteOverdueTasks(): List<TaskEntity> = dao.getIncompleteOverdueTasks()
    suspend fun deleteAll() = dao.deleteAllTasks()
    suspend fun resetRecurringTasksForNewDay() = dao.resetRecurringTasksForNewDay()
    suspend fun resetStaleRecurringTasks() = dao.resetStaleRecurringTasks(
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    suspend fun getAllTasks(): List<TaskEntity> = dao.getAllTasks()
    suspend fun getTrackerTaskCount(): Int = dao.getTrackerTaskCount()
}
