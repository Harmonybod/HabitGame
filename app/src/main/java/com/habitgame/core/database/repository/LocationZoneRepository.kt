package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.LocationZoneDao
import com.habitgame.core.database.entity.LocationZoneEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationZoneRepository @Inject constructor(
    private val dao: LocationZoneDao
) {
    fun observeAll(): Flow<List<LocationZoneEntity>> = dao.observeAll()
    suspend fun getById(id: Long): LocationZoneEntity? = dao.getById(id)
    suspend fun insert(zone: LocationZoneEntity): Long = dao.insert(zone)
    suspend fun delete(zone: LocationZoneEntity) = dao.delete(zone)
}
