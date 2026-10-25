package com.habitgame.domain.engine

import com.habitgame.core.database.repository.EconomyRepository
import com.habitgame.core.database.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EconomyManager @Inject constructor(
    private val userRepo: UserRepository,
    private val economyRepo: EconomyRepository,
    private val boosterManager: BoosterManager
) {
    private var consecutiveTaskCount = 0

    suspend fun earnCoins(baseAmount: Long, reason: String, streakMultiplier: Float = 1.0f): Long {
        consecutiveTaskCount++
        // Coin Rush booster from the Shop doubles coins while it runs
        var amount = (baseAmount * streakMultiplier * boosterManager.coinMultiplier()).toLong()
        // Combo bonus: every 3rd consecutive task earns +50%
        if (consecutiveTaskCount % 3 == 0) {
            amount = (amount * 1.5f).toLong()
        }
        userRepo.addCoins(amount)
        economyRepo.recordEarn(amount, reason)
        return amount
    }

    suspend fun spendCoins(amount: Long, reason: String): Boolean {
        val success = userRepo.spendCoins(amount)
        if (success) {
            economyRepo.recordSpend(amount, reason)
        }
        return success
    }

    fun resetCombo() { consecutiveTaskCount = 0 }

    fun currentCombo() = consecutiveTaskCount

    companion object {
        fun coinsForDifficulty(difficulty: com.habitgame.core.database.entity.Difficulty): Long {
            return when (difficulty) {
                com.habitgame.core.database.entity.Difficulty.EASY   -> 10L
                com.habitgame.core.database.entity.Difficulty.MEDIUM -> 25L
                com.habitgame.core.database.entity.Difficulty.HARD   -> 50L
                com.habitgame.core.database.entity.Difficulty.EPIC   -> 100L
            }
        }

        fun xpForDifficulty(difficulty: com.habitgame.core.database.entity.Difficulty): Int {
            return when (difficulty) {
                com.habitgame.core.database.entity.Difficulty.EASY   -> 20
                com.habitgame.core.database.entity.Difficulty.MEDIUM -> 50
                com.habitgame.core.database.entity.Difficulty.HARD   -> 100
                com.habitgame.core.database.entity.Difficulty.EPIC   -> 200
            }
        }

        /** App unlock pricing: duration in minutes -> coin cost */
        val APP_UNLOCK_PRICING = mapOf(30 to 250L, 60 to 400L, 90 to 500L)

        /** Entertainment pricing: duration in minutes -> coin cost */
        val ENTERTAINMENT_PRICING = mapOf(60 to 250L, 120 to 300L)
    }
}
