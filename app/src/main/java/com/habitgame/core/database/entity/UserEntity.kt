package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Hero",
    val level: Int = 1,
    val currentXp: Long = 0L,
    val totalCoins: Long = 0L,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val title: String = "Beginner",
    val lastActiveDate: String = "",      // ISO date YYYY-MM-DD
    val totalTasksDone: Int = 0,
    val totalFocusMinutes: Long = 0L,
    val selectedTheme: String = "default",
    val selectedBadge: String = "",
    val profileImageUri: String = "",
    val selectedIconPack: String = "default",
    // Phase 2 additions
    val notificationLeadMinutes: Int = 5, // Old single reminder time; reminderLeadTimes replaces it
    val accentColorHex: String = "",      // User-selected accent color (empty = use theme default)
    val glassEnabled: Boolean = false,    // Glass look on top of whichever theme is picked
    val backgroundImagePath: String = "", // Blurred photo behind the app while Glass is on
    // Minutes before a task or habit to remind, e.g. "10,5,1" (see ReminderTimes)
    val reminderLeadTimes: String = "5",
    // Minutes before a shopping item's deadline to remind
    val deadlineReminderTimes: String = "60,15,5"
)
