package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streak_days")
data class StreakDayEntity(
    @PrimaryKey val date: String,   // ISO YYYY-MM-DD
    val wasProductive: Boolean = false,
    val wasFrozen: Boolean = false,
    val tasksCompleted: Int = 0,
    val xpEarned: Int = 0,
    val coinsEarned: Int = 0
)
