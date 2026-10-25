package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE wasCompleted = 1 ORDER BY startedAt DESC LIMIT 20")
    fun observeCompletedSessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT SUM(actualDurationSeconds) FROM focus_sessions WHERE wasCompleted = 1")
    suspend fun getTotalFocusSeconds(): Long?

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE wasCompleted = 1")
    suspend fun getCompletedSessionCount(): Int

    /** Minutes of finished focus per day (local dates, "YYYY-MM-DD") between [from] and [to]. */
    @Query(
        "SELECT date(completedAt / 1000, 'unixepoch', 'localtime') AS date, SUM(actualDurationSeconds) / 60 AS minutes " +
            "FROM focus_sessions WHERE wasCompleted = 1 AND completedAt IS NOT NULL " +
            "AND date(completedAt / 1000, 'unixepoch', 'localtime') BETWEEN :from AND :to GROUP BY 1"
    )
    fun observeDailyFocus(from: String, to: String): Flow<List<DailyFocus>>
}

data class DailyFocus(val date: String, val minutes: Long)

