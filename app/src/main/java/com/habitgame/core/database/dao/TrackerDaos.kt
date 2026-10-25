package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.ChallengeEntity
import com.habitgame.core.database.entity.DayProgressEntity
import com.habitgame.core.database.entity.JournalEntryEntity
import com.habitgame.core.database.entity.SavingsEntryEntity
import com.habitgame.core.database.entity.XpLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge WHERE id = 1")
    fun observe(): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenge WHERE id = 1")
    suspend fun get(): ChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: ChallengeEntity)

    @Query("DELETE FROM challenge")
    suspend fun deleteAll()
}

@Dao
interface DayProgressDao {
    @Query("SELECT * FROM day_progress")
    fun observeAll(): Flow<List<DayProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DayProgressEntity)

    @Query("DELETE FROM day_progress")
    suspend fun deleteAll()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE date = :date")
    suspend fun get(date: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<SavingsEntryEntity>>

    @Insert
    suspend fun insert(entry: SavingsEntryEntity): Long

    @Delete
    suspend fun delete(entry: SavingsEntryEntity)

    @Query("DELETE FROM savings_entries")
    suspend fun deleteAll()
}

/** Total XP per day. */
data class DailyXp(val date: String, val xp: Int)

@Dao
interface XpLogDao {
    @Insert
    suspend fun insert(entry: XpLogEntity)

    @Query("SELECT date, SUM(amount) AS xp FROM xp_log WHERE date >= :from AND date <= :to GROUP BY date ORDER BY date")
    fun observeDaily(from: String, to: String): Flow<List<DailyXp>>

    @Query("DELETE FROM xp_log")
    suspend fun deleteAll()
}
