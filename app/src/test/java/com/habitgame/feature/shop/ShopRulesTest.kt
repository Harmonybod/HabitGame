package com.habitgame.feature.shop

import com.habitgame.core.database.entity.RewardCategory
import com.habitgame.core.database.entity.RewardItemEntity
import com.habitgame.core.database.entity.RewardSubcategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShopRulesTest {
    private fun theme(key: String, owned: Boolean = false) = RewardItemEntity(
        name = key, description = "", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
        cost = 500, iconEmoji = "🎨", themeKey = key, isOwned = owned
    )

    @Test
    fun amoledNeedsGoogleSignIn() {
        assertNotNull(ShopRules.lockReason(theme("amoled"), level = 10, signedIn = false))
        assertNull(ShopRules.lockReason(theme("amoled"), level = 1, signedIn = true))
    }

    @Test
    fun glassNeedsLevelFive() {
        assertNotNull(ShopRules.lockReason(theme("glass"), level = 4, signedIn = true))
        assertNull(ShopRules.lockReason(theme("glass"), level = 5, signedIn = false))
    }

    @Test
    fun ownedOnesAndOtherThemesAreNeverLocked() {
        assertNull(ShopRules.lockReason(theme("glass", owned = true), level = 1, signedIn = false))
        assertNull(ShopRules.lockReason(theme("midnight"), level = 1, signedIn = false))
    }

    @Test
    fun amoledAndGlassComeFirst() {
        val sorted = listOf(theme("midnight"), theme("glass"), theme("forest"), theme("amoled")).sortedBy { ShopRules.themeRank(it) }
        assertEquals(listOf("amoled", "glass", "midnight", "forest"), sorted.map { it.themeKey })
    }
}
