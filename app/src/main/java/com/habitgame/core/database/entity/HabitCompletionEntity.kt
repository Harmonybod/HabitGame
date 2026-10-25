package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_completions")
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,  // ISO YYYY-MM-DD
    val completedAt: Long = System.currentTimeMillis()
)
