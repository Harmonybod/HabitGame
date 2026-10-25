package com.habitgame.core.database.dao

import androidx.room.*
import com.habitgame.core.database.entity.LocationZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationZoneDao {
    @Query("SELECT * FROM location_zones")
    fun observeAll(): Flow<List<LocationZoneEntity>>

    @Query("SELECT * FROM location_zones WHERE id = :id")
    suspend fun getById(id: Long): LocationZoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: LocationZoneEntity): Long

    @Delete
    suspend fun delete(zone: LocationZoneEntity)
}
