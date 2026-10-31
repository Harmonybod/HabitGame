package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A folder for habits and tasks, e.g. "Weight gain" or "Take care of body". Habits and tasks
 * point at it by name ([HabitEntity.groupName], [TaskEntity.groupName]).
 */
@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey val name: String,
    val iconEmoji: String = "📁",
    val colorHex: String = "#7C4DFF",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
