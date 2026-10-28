package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A task or habit skipped for one day (swiped right to left on Home): it doesn't count toward
 * that day, sends no reminders and isn't penalised. [key] is e.g. "task:12:2026-10-28".
 */
@Entity(tableName = "day_skips")
data class DaySkipEntity(
    @PrimaryKey val key: String,
    val kind: String,                   // KIND_TASK or KIND_HABIT
    val itemId: Long,
    val date: String,                   // ISO date YYYY-MM-DD
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_TASK = "task"
        const val KIND_HABIT = "habit"

        fun key(kind: String, itemId: Long, date: String) = "$kind:$itemId:$date"

        fun of(kind: String, itemId: Long, date: String) = DaySkipEntity(key(kind, itemId, date), kind, itemId, date)
    }
}
