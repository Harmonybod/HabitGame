package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun observePendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun completeTask(id: Long, completedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE isCompleted = 1 AND isRecurring = 0")
    suspend fun deleteCompletedNonRecurringTasks()

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    suspend fun getCompletedTaskCount(): Int

    @Query("SELECT * FROM tasks WHERE scheduledDateMillis = :dateMillis ORDER BY createdAt DESC")
    fun observeTasksByDate(dateMillis: Long): Flow<List<TaskEntity>>

    // Phase 2 additions
    @Query("SELECT * FROM tasks WHERE isRecurring = 1")
    fun observeRepeatingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND scheduledDateMillis IS NOT NULL AND scheduledDateMillis < :nowMillis")
    suspend fun getIncompleteOverdueTasks(nowMillis: Long = System.currentTimeMillis()): List<TaskEntity>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("UPDATE tasks SET isCompleted = 0, completedAt = NULL WHERE isRecurring = 1")
    suspend fun resetRecurringTasksForNewDay()

    /** Un-completes repeating tasks that were completed on an earlier day. Safe to call any time. */
    @Query("UPDATE tasks SET isCompleted = 0, completedAt = NULL WHERE isRecurring = 1 AND isCompleted = 1 AND (completedAt IS NULL OR completedAt < :startOfTodayMillis)")
    suspend fun resetStaleRecurringTasks(startOfTodayMillis: Long)

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE trackerKey IS NOT NULL")
    suspend fun getTrackerTaskCount(): Int
}
