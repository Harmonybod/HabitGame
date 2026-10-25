package com.habitgame.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RewardCategory { FUNCTIONAL, BOOSTER, COSMETIC }
enum class RewardSubcategory { APP_UNLOCK, ENTERTAINMENT, BADGE, ICON_PACK, THEME, BOOSTER, GENERAL }

@Entity(tableName = "reward_items")
data class RewardItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val category: RewardCategory,
    val subcategory: RewardSubcategory = RewardSubcategory.GENERAL,
    val cost: Long,
    val iconEmoji: String = "🎁",
    val isOwned: Boolean = false,
    val isActive: Boolean = false,
    val durationMinutes: Int = 0,       // For timed rewards (app unlock, entertainment, boosters)
    val activatedAt: Long? = null,      // When timed reward was started
    val usedMillis: Long = 0,           // App Unlock / Entertainment: time spent in locked apps so far
    // Phase 2 additions
    val boosterEffect: String = "",     // e.g. "DOUBLE_XP", "NO_RESTRICTION", "STREAK_FREEZE"
    val badgeKey: String = "",          // e.g. "🔥", "🏅", "💎" — equips to profile on purchase
    val themeKey: String = "",          // e.g. "neon", "midnight" — unlocks in Settings
    val iconPackKey: String = "",       // e.g. "retro", "elite" — unlocks in Settings
    val isLocked: Boolean = false       // For themes/icon packs: locked until purchased
)
