package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.DaySkipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DaySkipDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun skip(skip: DaySkipEntity)

    @Query("DELETE FROM day_skips WHERE `key` = :key")
    suspend fun unskip(key: String)

    @Query("SELECT COUNT(*) > 0 FROM day_skips WHERE `key` = :key")
    suspend fun isSkipped(key: String): Boolean

    @Query("SELECT * FROM day_skips WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DaySkipEntity>>

    @Query("SELECT * FROM day_skips WHERE date = :date")
    suspend fun forDate(date: String): List<DaySkipEntity>

    @Query("SELECT * FROM day_skips")
    fun observeAll(): Flow<List<DaySkipEntity>>

    @Query("SELECT * FROM day_skips WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: String, to: String): Flow<List<DaySkipEntity>>

    @Query("DELETE FROM day_skips")
    suspend fun deleteAll()
}
