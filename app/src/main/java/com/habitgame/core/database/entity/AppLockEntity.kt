package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_locks")
data class AppLockEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isUnlocked: Boolean = false,
    val unlockDurationMinutes: Int = 0,
    val unlockedAt: Long? = null,
    val coinCost: Long = 0
)
