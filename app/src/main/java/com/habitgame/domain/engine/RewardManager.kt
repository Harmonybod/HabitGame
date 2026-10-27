package com.habitgame.domain.engine

import com.habitgame.core.database.dao.RewardClaimDao
import com.habitgame.core.database.entity.RewardClaimEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A reward to celebrate on screen. [level] is set for level-ups. */
data class Celebration(
    val emoji: String,
    val title: String,
    val message: String,
    val coins: Long,
    val level: Int? = null,
    val id: Long = System.nanoTime()
)

/**
 * Rewards waiting to be celebrated. They queue up, so one earned while the app was in the
 * background (e.g. from a notification's Done button) is shown when it's next opened.
 */
@Singleton
class Celebrations @Inject constructor() {
    private val _queue = MutableStateFlow<List<Celebration>>(emptyList())
    val queue: StateFlow<List<Celebration>> = _queue.asStateFlow()

    fun show(celebration: Celebration) = _queue.update { it + celebration }

    fun dismiss(celebration: Celebration) = _queue.update { list -> list.filterNot { it.id == celebration.id } }
}

/**
 * The bonus coins: finishing a day's tasks and habits, streak milestones, level-ups and
 * connecting Google. Each is paid once (claims sync between phones) and celebrated.
 */
@Singleton
class RewardManager @Inject constructor(
    private val claims: RewardClaimDao,
    private val economy: EconomyManager,
    private val celebrations: Celebrations
) {
    companion object {
        const val DAY_COMPLETE_COINS = 100L
        const val SIGN_IN_COINS = 100L

        /** Coins for reaching [level]: 100 for level 2, then 25 more for each level after (125, 150, …). */
        fun levelUpCoins(level: Int): Long = if (level < 2) 0L else 100L + 25L * (level - 2)

        /** Every 5 days of streak: 50 coins at 5 days, 100 at 10, 15, 20 and on. */
        fun streakCoins(streak: Int): Long = when {
            streak <= 0 || streak % 5 != 0 -> 0L
            streak == 5 -> 50L
            else -> 100L
        }
    }

    /** Pays [coins] once for [key]. Returns false if it was already paid. */
    private suspend fun payOnce(key: String, coins: Long, reason: String): Boolean {
        if (claims.claim(RewardClaimEntity(key, coins)) == -1L) return false
        economy.earnBonus(coins, reason)
        return true
    }

    /** Everything planned for [date] is done: 100 coins, once a day. */
    suspend fun dayComplete(date: LocalDate = LocalDate.now()) {
        if (payOnce("day:$date", DAY_COMPLETE_COINS, "🎉 Finished the day's tasks and habits")) {
            celebrations.show(Celebration("🎉", "Day complete!", "Every task and habit for today is done.", DAY_COMPLETE_COINS))
        }
    }

    suspend fun streakReached(streak: Int, date: LocalDate = LocalDate.now()) {
        val coins = streakCoins(streak)
        if (coins == 0L) return
        if (payOnce("streak:$date:$streak", coins, "🔥 Streak milestone: $streak days!")) {
            celebrations.show(Celebration("🔥", "$streak-day streak!", "$streak days in a row. Keep it going!", coins))
        }
    }

    /** From [fromLevel] up to [toLevel]: each new level's coins, celebrated together. */
    suspend fun levelsReached(fromLevel: Int, toLevel: Int, title: String) {
        var total = 0L
        for (level in fromLevel + 1..toLevel) {
            val coins = levelUpCoins(level)
            if (payOnce("level:$level", coins, "⬆️ Reached level $level")) total += coins
        }
        if (total > 0) celebrations.show(Celebration("⬆️", "Level $toLevel!", title, total, level = toLevel))
    }

    /** The first time this account connects Google. */
    suspend fun signedIn() {
        if (payOnce("google", SIGN_IN_COINS, "☁️ Connected your Google account")) {
            celebrations.show(Celebration("☁️", "Welcome aboard!", "Your progress is now backed up to your Google account.", SIGN_IN_COINS))
        }
    }
}
