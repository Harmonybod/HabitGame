package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What finishing a task or habit paid that day, so undoing it takes back exactly that.
 * [key] is e.g. "task:12:2026-11-07" (see [DaySkipEntity.key]).
 */
@Entity(tableName = "completion_receipts")
data class CompletionReceiptEntity(
    @PrimaryKey val key: String,
    val xp: Int,
    val coins: Long,
    val createdAt: Long = System.currentTimeMillis()
)
