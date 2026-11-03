package com.habitgame.feature.shop

import com.habitgame.core.database.entity.RewardItemEntity

/** Which Shop items need something besides coins, and the order themes are listed in. */
object ShopRules {
    const val GLASS_LEVEL = 5

    /** Why [item] can't be bought yet, or null when coins are all it takes. */
    fun lockReason(item: RewardItemEntity, level: Int, signedIn: Boolean): String? = when {
        item.isOwned -> null
        item.themeKey == "amoled" && !signedIn -> "Sign in with Google first (Settings → General)"
        item.themeKey == "glass" && level < GLASS_LEVEL -> "Reach level $GLASS_LEVEL to buy it (you're level $level)"
        else -> null
    }

    /** AMOLED and Glass at the top of the themes, the rest as they were. */
    fun themeRank(item: RewardItemEntity): Int = when (item.themeKey) {
        "amoled" -> 0
        "glass" -> 1
        else -> 2
    }
}
