package com.habitgame.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RepetitionType { ONE_TIME, REPEATING }
/** SHOPPING: something to buy in real life, done whenever it's bought (optionally before a deadline). */
enum class TaskCategoryType { ACTIVITY, LOCATION, SHOPPING }
enum class ActivityCategory { STUDY, GYM, WORK, READING, MEDITATION, CODING, MEAL, SAVINGS, OTHER }
enum class Difficulty { EASY, MEDIUM, HARD, EPIC }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    // Repetition (replaces old "type")
    val repetition: RepetitionType = RepetitionType.ONE_TIME,
    // Category
    val categoryType: TaskCategoryType = TaskCategoryType.ACTIVITY,
    val activityCategory: ActivityCategory = ActivityCategory.OTHER,
    // Difficulty & Rewards
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val xpReward: Int = 50,
    val coinReward: Int = 20,
    // Completion
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // Scheduling
    val scheduledDateMillis: Long? = null,
    val scheduledTimeMillis: Long? = null,
    val isAllDay: Boolean = true,
    // Recurrence (for REPEATING tasks)
    val isRecurring: Boolean = false,
    val recurrenceDays: String = "",  // comma-separated: "MON,WED,FRI"
    // Notification
    val notifyEnabled: Boolean = false,
    val notifyAtMillis: Long? = null,
    // Location (for LOCATION category)
    val locationZoneId: Long? = null,
    // Focus (for ACTIVITY category); 0 = a simple check-off task with no focus timer
    val focusDurationMinutes: Int = 25,
    // Savings (for SAVINGS activity): amount logged to the savings tab when completed
    @ColumnInfo(defaultValue = "0") val savingAmount: Double = 0.0,
    // Set on tasks created from the 100-day tracker so they are only seeded once
    val trackerKey: String? = null,
    // Last day a repeating task repeats (ISO date, inclusive); null = no end
    val repeatEndDate: String? = null,
    // Shopping list items: expected price range (0 = not given) and an optional deadline.
    // An item that isn't bought by its deadline is removed.
    @ColumnInfo(defaultValue = "0") val priceMin: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val priceMax: Double = 0.0,
    val deadlineAt: Long? = null
) {
    val isShopping: Boolean
        get() = categoryType == TaskCategoryType.SHOPPING

    val hasFocusTimer: Boolean
        get() = categoryType == TaskCategoryType.ACTIVITY && focusDurationMinutes > 0
}
