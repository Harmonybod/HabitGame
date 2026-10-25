package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.StreakDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDayDao {
    @Query("SELECT * FROM streak_days ORDER BY date DESC LIMIT 30")
    fun observeRecentDays(): Flow<List<StreakDayEntity>>

    @Query("SELECT * FROM streak_days WHERE date = :date")
    suspend fun getDayByDate(date: String): StreakDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(day: StreakDayEntity)

    @Query("UPDATE streak_days SET wasProductive = 1, tasksCompleted = tasksCompleted + 1, xpEarned = xpEarned + :xp, coinsEarned = coinsEarned + :coins WHERE date = :date")
    suspend fun markProductive(date: String, xp: Int, coins: Int)

    @Query("SELECT * FROM streak_days WHERE wasProductive = 1 ORDER BY date DESC")
    suspend fun getAllProductiveDays(): List<StreakDayEntity>
}
