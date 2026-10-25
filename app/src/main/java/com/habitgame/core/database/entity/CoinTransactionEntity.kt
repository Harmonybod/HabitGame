package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType { EARN, SPEND }

@Entity(tableName = "coin_transactions")
data class CoinTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
