package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A one-off bonus that has been paid, so it's never paid twice, on this phone or (it syncs) any
 * other: "day:2026-10-28" for finishing a day, "streak:2026-10-28:5", "level:6", "google".
 */
@Entity(tableName = "reward_claims")
data class RewardClaimEntity(
    @PrimaryKey val key: String,
    val coins: Long = 0,
    val claimedAt: Long = System.currentTimeMillis()
)
