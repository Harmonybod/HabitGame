package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedDurationMinutes: Int,
    val actualDurationSeconds: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val wasCompleted: Boolean = false,
    val xpEarned: Int = 0,
    val coinsEarned: Int = 0,
    val xpMultiplier: Float = 1.0f,
    val taskId: Long? = null
)
