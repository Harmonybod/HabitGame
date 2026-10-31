package com.habitgame.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.habitgame.core.database.entity.AreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas")
    suspend fun getAll(): List<AreaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(area: AreaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(area: AreaEntity)

    @Query("DELETE FROM areas WHERE name = :name")
    suspend fun delete(name: String)

    /** Moves every habit and task in area [from] to area [to] ("" = no area). */
    @Query("UPDATE habits SET groupName = :to WHERE groupName = :from")
    suspend fun moveHabits(from: String, to: String)

    @Query("UPDATE tasks SET groupName = :to WHERE groupName = :from")
    suspend fun moveTasks(from: String, to: String)

    @Query("DELETE FROM areas")
    suspend fun deleteAll()
}
