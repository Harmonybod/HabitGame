package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_zones")
data class LocationZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 100f,
    val requiredDurationMinutes: Int = 5,
    val iconEmoji: String = "📍"
)
