package com.habitgame.domain.engine

import com.habitgame.core.database.dao.CompletionReceiptDao
import com.habitgame.core.database.entity.CompletionReceiptEntity
import com.habitgame.core.database.entity.DaySkipEntity
import com.habitgame.core.database.repository.EconomyRepository
import com.habitgame.core.database.repository.UserRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers what finishing a task or habit paid today, so that undoing a mistaken tick takes
 * back exactly those coins and that XP (dropping a level again if it caused one).
 */
@Singleton
class CompletionRewards @Inject constructor(
    private val receiptDao: CompletionReceiptDao,
    private val userRepo: UserRepository,
    private val economyRepo: EconomyRepository,
    private val progressEngine: UserProgressEngine
) {
    private fun key(kind: String, id: Long) = DaySkipEntity.key(kind, id, LocalDate.now().toString())

    suspend fun remember(kind: String, id: Long, xp: Int, coins: Long) {
        receiptDao.put(CompletionReceiptEntity(key(kind, id), xp, coins))
    }

    /**
     * Takes back what finishing it paid today. Without a receipt (e.g. finished on another phone)
     * the base amounts for its difficulty are taken back instead.
     */
    suspend fun takeBack(kind: String, id: Long, label: String, fallbackXp: Int, fallbackCoins: Long) {
        val key = key(kind, id)
        val receipt = receiptDao.get(key)
        receiptDao.delete(key)
        val coins = receipt?.coins ?: fallbackCoins
        val xp = receipt?.xp ?: fallbackXp
        if (coins > 0) {
            userRepo.takeBackCoins(coins)
            economyRepo.recordSpend(coins, "Undone: $label")
        }
        if (xp > 0) progressEngine.removeXp(xp)
    }
}
