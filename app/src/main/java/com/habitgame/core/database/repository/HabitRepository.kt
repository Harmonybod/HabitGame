package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.HabitCompletionDao
import com.habitgame.core.database.dao.HabitDao
import com.habitgame.core.database.entity.HabitCompletionEntity
import com.habitgame.core.database.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val completionDao: HabitCompletionDao
) {
    fun observeAllHabits(): Flow<List<HabitEntity>> = habitDao.observeAllHabits()
    fun observeActiveHabits(): Flow<List<HabitEntity>> = habitDao.observeActiveHabits()

    suspend fun getHabitById(id: Long): HabitEntity? = habitDao.getHabitById(id)
    suspend fun getAllHabits(): List<HabitEntity> = habitDao.getAllHabits()
    suspend fun insertHabit(habit: HabitEntity): Long = habitDao.insertHabit(habit)
    suspend fun updateHabit(habit: HabitEntity) = habitDao.updateHabit(habit)
    suspend fun deleteHabit(habit: HabitEntity) = habitDao.deleteHabit(habit)

    // Completions
    fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>> =
        completionDao.observeCompletionsForDate(date)

    /** Every tick ever, for the Progress page. */
    fun observeAllCompletions(): Flow<List<HabitCompletionEntity>> = completionDao.observeAll()

    suspend fun isHabitCompletedToday(habitId: Long): Boolean {
        val today = LocalDate.now().toString()
        return completionDao.getCompletionForHabitOnDate(habitId, today) != null
    }

    suspend fun completeHabit(habitId: Long) {
        val today = LocalDate.now().toString()
        if (completionDao.getCompletionForHabitOnDate(habitId, today) == null) {
            completionDao.insertCompletion(HabitCompletionEntity(habitId = habitId, date = today))
        }
    }

    suspend fun uncompleteHabit(habitId: Long) {
        val today = LocalDate.now().toString()
        completionDao.deleteCompletionForDate(habitId, today)
    }

    suspend fun getHabitStreak(habitId: Long): Int {
        val dates = completionDao.getCompletionDates(habitId)
        if (dates.isEmpty()) return 0
        var streak = 0
        var checkDate = LocalDate.now()
        val dateSet = dates.toHashSet()
        while (dateSet.contains(checkDate.toString())) {
            streak++
            checkDate = checkDate.minusDays(1)
        }
        return streak
    }

    suspend fun getCompletedHabitCountForDate(date: String): Int =
        completionDao.getCompletedHabitCountForDate(date)

    // Phase 2
    suspend fun isHabitCompletedOnDate(habitId: Long, date: LocalDate): Boolean {
        return completionDao.getCompletionForHabitOnDate(habitId, date.toString()) != null
    }

    suspend fun deleteAll() {
        habitDao.deleteAllHabits()
        completionDao.deleteAllCompletions()
    }
}

