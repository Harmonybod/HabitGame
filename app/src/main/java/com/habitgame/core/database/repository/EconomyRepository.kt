package com.habitgame.core.database.repository

import com.habitgame.core.database.dao.CoinTransactionDao
import com.habitgame.core.database.dao.RewardItemDao
import com.habitgame.core.database.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EconomyRepository @Inject constructor(
    private val coinDao: CoinTransactionDao,
    private val rewardDao: RewardItemDao
) {
    private val seedLock = Mutex()  // Shop and Settings both seed; never insert the same item twice

    fun observeRecentTransactions(): Flow<List<CoinTransactionEntity>> =
        coinDao.observeRecentTransactions()

    fun observeAllRewards(): Flow<List<RewardItemEntity>> = rewardDao.observeAllRewards()

    suspend fun countTaskRewards(): Int = coinDao.countTaskRewards()
    suspend fun totalCoinsEarned(): Long = coinDao.totalEarned()

    fun observeBySubcategory(subcategory: String): Flow<List<RewardItemEntity>> =
        rewardDao.observeBySubcategory(subcategory)

    suspend fun recordEarn(amount: Long, reason: String) =
        coinDao.insertTransaction(CoinTransactionEntity(amount = amount, type = TransactionType.EARN, reason = reason))

    suspend fun recordSpend(amount: Long, reason: String) =
        coinDao.insertTransaction(CoinTransactionEntity(amount = amount, type = TransactionType.SPEND, reason = reason))

    /** Adds any Shop item that isn't there yet, so items added in an update reach existing installs too. */
    suspend fun seedDefaultShop() {
        val defaults = listOf(
            // ──────────────────── APP UNLOCK ────────────────────
            RewardItemEntity(name = "30 Min App Unlock", description = "Use your locked apps for 30 minutes. Time only counts while they are open", category = RewardCategory.FUNCTIONAL, subcategory = RewardSubcategory.APP_UNLOCK, cost = 250, iconEmoji = "📱", durationMinutes = 30),
            RewardItemEntity(name = "1 Hour App Unlock", description = "Use your locked apps for 1 hour. Time only counts while they are open", category = RewardCategory.FUNCTIONAL, subcategory = RewardSubcategory.APP_UNLOCK, cost = 400, iconEmoji = "📱", durationMinutes = 60),
            RewardItemEntity(name = "1.5 Hours App Unlock", description = "Use your locked apps for 1.5 hours. Time only counts while they are open", category = RewardCategory.FUNCTIONAL, subcategory = RewardSubcategory.APP_UNLOCK, cost = 500, iconEmoji = "📱", durationMinutes = 90),

            // ──────────────────── ENTERTAINMENT ────────────────────
            RewardItemEntity(name = "1 Hour Entertainment", description = "Enjoy 1 hour of movies or TV shows. Time only counts while your apps are open", category = RewardCategory.FUNCTIONAL, subcategory = RewardSubcategory.ENTERTAINMENT, cost = 250, iconEmoji = "🎬", durationMinutes = 60),
            RewardItemEntity(name = "2 Hours Entertainment", description = "Enjoy 2 hours of movies or TV shows. Time only counts while your apps are open", category = RewardCategory.FUNCTIONAL, subcategory = RewardSubcategory.ENTERTAINMENT, cost = 300, iconEmoji = "🎬", durationMinutes = 120),

            // ──────────────────── BOOSTERS ────────────────────
            RewardItemEntity(
                name = "Double XP (1 Hour)", description = "Earn 2× XP for the next 60 minutes",
                category = RewardCategory.BOOSTER, subcategory = RewardSubcategory.BOOSTER,
                cost = 200, iconEmoji = "⚡", durationMinutes = 60,
                boosterEffect = "DOUBLE_XP"
            ),
            RewardItemEntity(
                name = "Streak Freeze", description = "Protect your streak for one day — XP penalty still applies",
                category = RewardCategory.BOOSTER, subcategory = RewardSubcategory.BOOSTER,
                cost = 180, iconEmoji = "🧊", durationMinutes = 0,
                boosterEffect = "STREAK_FREEZE"
            ),
            RewardItemEntity(
                name = "No Restriction Day", description = "All app locks off for today — apps become accessible",
                category = RewardCategory.BOOSTER, subcategory = RewardSubcategory.BOOSTER,
                cost = 600, iconEmoji = "🔓", durationMinutes = 1440, // 24h
                boosterEffect = "NO_RESTRICTION"
            ),
            RewardItemEntity(
                name = "Coin Rush (30 Min)", description = "Earn 2× coins for the next 30 minutes",
                category = RewardCategory.BOOSTER, subcategory = RewardSubcategory.BOOSTER,
                cost = 150, iconEmoji = "🪙", durationMinutes = 30,
                boosterEffect = "DOUBLE_COINS"
            ),

            // ──────────────────── THEMES (Locked until purchased) ────────────────────
            // Minimal is NOT in the shop — it's free in Settings
            RewardItemEntity(
                name = "Neon Theme", description = "Vibrant neon color theme — unlock in Settings after purchase",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
                cost = 500, iconEmoji = "🌈", isLocked = true, themeKey = "neon"
            ),
            RewardItemEntity(
                name = "Midnight Theme", description = "Deep dark variant theme — unlock in Settings after purchase",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
                cost = 400, iconEmoji = "🌙", isLocked = true, themeKey = "midnight"
            ),
            RewardItemEntity(
                name = "AMOLED Dark Theme", description = "True black everywhere. Easy on the eyes at night and saves battery on AMOLED screens",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
                cost = 500, iconEmoji = "🖤", isLocked = true, themeKey = "amoled"
            ),
            RewardItemEntity(
                name = "Glass Theme", description = "Frosted glass look that works on top of any theme. Can use your own photo as the background",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
                cost = 600, iconEmoji = "🔮", isLocked = true, themeKey = "glass"
            ),

            // ──────────────────── BADGES ────────────────────
            RewardItemEntity(
                name = "Gold Badge", description = "Display the gold champion badge on your profile",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE,
                cost = 350, iconEmoji = "🏅", badgeKey = "🏅"
            ),
            RewardItemEntity(
                name = "Diamond Badge", description = "The prestigious diamond badge — for the elite",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE,
                cost = 600, iconEmoji = "💎", badgeKey = "💎"
            ),
            RewardItemEntity(
                name = "Fire Badge", description = "Show your streak dedication with the fire badge",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE,
                cost = 300, iconEmoji = "🔥", badgeKey = "🔥"
            ),

            RewardItemEntity(name = "Crown Badge", description = "Rule your habits like royalty", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 800, iconEmoji = "👑", badgeKey = "👑"),
            RewardItemEntity(name = "Trophy Badge", description = "For the ones who finish what they start", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 500, iconEmoji = "🏆", badgeKey = "🏆"),
            RewardItemEntity(name = "Medal Badge", description = "A medal for steady effort", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 400, iconEmoji = "🎖️", badgeKey = "🎖️"),
            RewardItemEntity(name = "Star Badge", description = "A bright little star next to your name", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 200, iconEmoji = "⭐", badgeKey = "⭐"),
            RewardItemEntity(name = "Lightning Badge", description = "Fast, focused, unstoppable", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 250, iconEmoji = "⚡", badgeKey = "⚡"),
            RewardItemEntity(name = "Rocket Badge", description = "Always on the way up", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 300, iconEmoji = "🚀", badgeKey = "🚀"),
            RewardItemEntity(name = "Target Badge", description = "You hit the goals you set", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 250, iconEmoji = "🎯", badgeKey = "🎯"),
            RewardItemEntity(name = "Brain Badge", description = "For the deep thinkers and learners", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 300, iconEmoji = "🧠", badgeKey = "🧠"),
            RewardItemEntity(name = "Book Badge", description = "A reader's badge", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 200, iconEmoji = "📚", badgeKey = "📚"),
            RewardItemEntity(name = "Muscle Badge", description = "Strong body, strong habits", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 250, iconEmoji = "💪", badgeKey = "💪"),
            RewardItemEntity(name = "Shield Badge", description = "Defender of your streak", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 350, iconEmoji = "🛡️", badgeKey = "🛡️"),
            RewardItemEntity(name = "Swords Badge", description = "A warrior against procrastination", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 450, iconEmoji = "⚔️", badgeKey = "⚔️"),
            RewardItemEntity(name = "Lion Badge", description = "Brave and proud", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 500, iconEmoji = "🦁", badgeKey = "🦁"),
            RewardItemEntity(name = "Wolf Badge", description = "Runs with discipline", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 450, iconEmoji = "🐺", badgeKey = "🐺"),
            RewardItemEntity(name = "Eagle Badge", description = "Sees the big picture", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 450, iconEmoji = "🦅", badgeKey = "🦅"),
            RewardItemEntity(name = "Dragon Badge", description = "Legendary power, rarely seen", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 900, iconEmoji = "🐉", badgeKey = "🐉"),
            RewardItemEntity(name = "Owl Badge", description = "Wise about how you spend your time", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 350, iconEmoji = "🦉", badgeKey = "🦉"),
            RewardItemEntity(name = "Unicorn Badge", description = "One of a kind", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 700, iconEmoji = "🦄", badgeKey = "🦄"),
            RewardItemEntity(name = "Clover Badge", description = "A little luck never hurts", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 200, iconEmoji = "🍀", badgeKey = "🍀"),
            RewardItemEntity(name = "Moon Badge", description = "For the night owls", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 250, iconEmoji = "🌙", badgeKey = "🌙"),
            RewardItemEntity(name = "Sun Badge", description = "For the early risers", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 250, iconEmoji = "☀️", badgeKey = "☀️"),
            RewardItemEntity(name = "Wave Badge", description = "Calm and steady flow", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 300, iconEmoji = "🌊", badgeKey = "🌊"),
            RewardItemEntity(name = "Volcano Badge", description = "Unstoppable energy", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 550, iconEmoji = "🌋", badgeKey = "🌋"),
            RewardItemEntity(name = "Snowflake Badge", description = "Cool under pressure", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 300, iconEmoji = "❄️", badgeKey = "❄️"),
            RewardItemEntity(name = "Gem Badge", description = "Polished by daily practice", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 650, iconEmoji = "💠", badgeKey = "💠"),
            RewardItemEntity(name = "Ninja Badge", description = "Silent, quick and consistent", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 600, iconEmoji = "🥷", badgeKey = "🥷"),
            RewardItemEntity(name = "Wizard Badge", description = "Master of your own time", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 700, iconEmoji = "🧙", badgeKey = "🧙"),
            RewardItemEntity(name = "Alien Badge", description = "Out of this world", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 400, iconEmoji = "👽", badgeKey = "👽"),
            RewardItemEntity(name = "Robot Badge", description = "Runs like clockwork", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 400, iconEmoji = "🤖", badgeKey = "🤖"),
            RewardItemEntity(name = "Rainbow Badge", description = "Every colour of effort", category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.BADGE, cost = 350, iconEmoji = "🌈", badgeKey = "🌈"),

            // ──────────────────── ICON PACKS ────────────────────
            RewardItemEntity(
                name = "Elite Icon Pack", description = "Sleek elite icons for navigation and UI elements",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.ICON_PACK,
                cost = 450, iconEmoji = "🎨", isLocked = true, iconPackKey = "elite"
            ),
            RewardItemEntity(
                name = "Retro Icon Pack", description = "Pixel retro-styled icons for the whole app",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.ICON_PACK,
                cost = 400, iconEmoji = "👾", isLocked = true, iconPackKey = "retro"
            ),
            RewardItemEntity(
                name = "Game Icon Pack", description = "Colourful game pictures for the tabs, coins, streak, buttons and task icons",
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.ICON_PACK,
                cost = 500, iconEmoji = "🎮", isLocked = true, iconPackKey = "game"
            ),
        ) + com.habitgame.ui.theme.ThemeCatalog.realtime.map { theme ->
            // The Realtime Colors themes: 200 coins each
            RewardItemEntity(
                name = "${theme.name} Theme", description = theme.description,
                category = RewardCategory.COSMETIC, subcategory = RewardSubcategory.THEME,
                cost = com.habitgame.ui.theme.ThemeCatalog.PRICE, iconEmoji = theme.emoji, isLocked = true, themeKey = theme.id
            )
        }
        seedLock.withLock {
            defaults.filter { rewardDao.countByName(it.name) == 0 }.forEach { rewardDao.insertReward(it) }
        }
    }

    suspend fun purchaseReward(item: RewardItemEntity) = rewardDao.markOwned(item.id)
    suspend fun activateReward(id: Long) = rewardDao.activateReward(id)
    suspend fun deactivateReward(id: Long) = rewardDao.deactivateReward(id)
    suspend fun deleteAllRewards() = rewardDao.deleteAll()
    suspend fun getActiveBooster(effect: String) = rewardDao.getActiveBooster(effect)
    suspend fun getOwnedBySubcategory(sub: String) = rewardDao.getOwnedBySubcategory(sub)
}
