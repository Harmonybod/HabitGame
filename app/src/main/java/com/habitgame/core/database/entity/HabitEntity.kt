package com.habitgame.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class HabitFrequency { DAILY, SPECIFIC_DAYS }

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val iconEmoji: String = "🎯",
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val frequencyDays: String = "",  // comma-separated: "MON,WED,FRI"
    val reminderTimeMillis: Long? = null,  // only the time of day is used; null = no reminder
    val xpReward: Int = 30,
    val coinReward: Int = 15,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    // Last day the habit repeats (ISO date, inclusive); null = no end
    val repeatEndDate: String? = null,
    // Section on the Habits screen, e.g. "Weight gain"
    @ColumnInfo(defaultValue = "") val groupName: String = "",
    // Set on habits created from the 100-day tracker
    val trackerKey: String? = null,
    // Show this habit's description as a card on the Home screen
    @ColumnInfo(defaultValue = "0") val showOnHome: Boolean = false
)
