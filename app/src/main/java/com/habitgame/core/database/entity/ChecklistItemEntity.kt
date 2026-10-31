package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One step of a task's or habit's checklist. Once every step is ticked, the task or habit is done.
 * [checkedDate] is the day it was ticked: habits and repeating tasks start fresh each day, a one-time
 * task keeps its ticks.
 */
@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerKind: String,              // DaySkipEntity.KIND_TASK or KIND_HABIT
    val ownerId: Long,
    val text: String,
    val sortOrder: Int = 0,
    val checkedDate: String? = null     // ISO date YYYY-MM-DD
) {
    val ownerKey: String get() = "$ownerKind:$ownerId"
}
