package com.habitgame.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.core.database.entity.Difficulty
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.engine.RewardManager
import com.habitgame.domain.focus.FocusTimer
import com.habitgame.ui.components.GameCard

/** The "?" on Home: the ways to earn coins, and why boosters and levelling up are worth it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinHelpSheet(onDismiss: () -> Unit) {
    fun coins(d: Difficulty) = EconomyManager.coinsForDifficulty(d)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🪙 How to earn coins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            HelpCard("✅", "Finish tasks and habits") {
                Line("Easy ${coins(Difficulty.EASY)} · Medium ${coins(Difficulty.MEDIUM)} · Hard ${coins(Difficulty.HARD)} · Epic ${coins(Difficulty.EPIC)} coins each.")
                Line("Pick the difficulty honestly: harder ones pay more.")
                Line("Every 3rd one in a row pays +50% (combo).")
            }
            HelpCard("⏱️", "Focus") {
                Line("${FocusTimer.coinsFor(1)} coins for every minute of finished focus: a 25-minute block pays ${FocusTimer.coinsFor(25)}.")
            }
            HelpCard("🎯", "Bonuses") {
                Line("Finish everything planned for a day: +${RewardManager.DAY_COMPLETE_COINS} coins.")
                Line("Streaks: +${RewardManager.streakCoins(5)} at 5 days, then +${RewardManager.streakCoins(10)} every 5 days.")
                Line("Connect your Google account once: +${RewardManager.SIGN_IN_COINS} coins.")
            }
            HelpCard("🔥", "Keep your streak") {
                Line("Your streak multiplies the coins and XP of everything you finish:")
                Line("3 days ×1.1 · 7 days ×1.25 · 14 days ×1.5 · 30 days ×2")
                Line("A Streak Freeze from the Shop protects it on a day you can't make it.")
            }

            Text("⚡ Why boosters help", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            HelpCard("🪙", "Coin Rush") {
                Line("${BoosterManager.COIN_MULTIPLIER}× coins for an hour. Save your hardest tasks or a long focus session for it:")
                Line("an hour of focus pays ${FocusTimer.coinsFor(60)} coins normally, ${(FocusTimer.coinsFor(60) * BoosterManager.COIN_MULTIPLIER).toLong()} during Coin Rush.")
            }
            HelpCard("⭐", "Double XP") {
                Line("2× XP for an hour, so you level up sooner, and every level pays coins too (below).")
            }

            Text("🏆 Why levelling up helps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            HelpCard("📈", "Levels pay and unlock") {
                Line("Each new level pays coins: ${RewardManager.levelUpCoins(2)} at level 2, ${RewardManager.levelUpCoins(5)} at level 5, ${RewardManager.levelUpCoins(10)} at level 10, 25 more each level.")
                Line("Level 5 unlocks the Glass theme in the Shop.")
                Line("You get a new title as you climb. Tap the level bar on Home to see them all.")
            }
            HelpCard("☁️", "Sign in with Google") {
                Line("Keeps your progress safe on every phone, and unlocks the AMOLED theme in the Shop.")
            }
        }
    }
}

@Composable
private fun HelpCard(emoji: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun Line(text: String) {
    Text("• $text", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
