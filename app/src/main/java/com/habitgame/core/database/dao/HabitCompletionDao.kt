package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.HabitCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCompletionDao {
    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    fun observeCompletionsForHabit(habitId: Long): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getCompletionForHabitOnDate(habitId: Long, date: String): HabitCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletionEntity): Long

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletionForDate(habitId: Long, date: String)

    @Query("SELECT COUNT(DISTINCT date) FROM habit_completions WHERE habitId = :habitId")
    suspend fun getTotalCompletionDays(habitId: Long): Int

    @Query("SELECT DISTINCT date FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    suspend fun getCompletionDates(habitId: Long): List<String>

    @Query("SELECT COUNT(DISTINCT habitId) FROM habit_completions WHERE date = :date")
    suspend fun getCompletedHabitCountForDate(date: String): Int

    @Query("DELETE FROM habit_completions")
    suspend fun deleteAllCompletions()
}

