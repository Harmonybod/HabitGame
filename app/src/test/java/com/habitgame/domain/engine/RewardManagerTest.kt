package com.habitgame.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardManagerTest {
    @Test
    fun levelUpsPayTwentyFiveMoreEachLevel() {
        assertEquals(0L, RewardManager.levelUpCoins(1))
        assertEquals(100L, RewardManager.levelUpCoins(2))
        assertEquals(125L, RewardManager.levelUpCoins(3))
        assertEquals(150L, RewardManager.levelUpCoins(4))
        assertEquals(175L, RewardManager.levelUpCoins(5))
    }

    @Test
    fun streaksPayEveryFiveDays() {
        assertEquals(0L, RewardManager.streakCoins(4))
        assertEquals(50L, RewardManager.streakCoins(5))
        assertEquals(0L, RewardManager.streakCoins(7))
        assertEquals(100L, RewardManager.streakCoins(10))
        assertEquals(100L, RewardManager.streakCoins(15))
        assertEquals(100L, RewardManager.streakCoins(20))
    }
}
