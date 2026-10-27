package com.habitgame.feature.profile

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.engine.RewardManager
import com.habitgame.domain.engine.UserProgressEngine
import com.habitgame.ui.Rank
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.XpProgressBar
import com.habitgame.ui.theme.cardSurface
import com.habitgame.ui.theme.GoldCoin
import com.habitgame.ui.theme.readable

/** Every level in the game, tier by tier, with its shield and title. Levels not reached yet are blurred. */
@Composable
fun LevelsScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val level = state.user.level
    val grid = rememberLazyGridState()
    // Opens on the tier you're in
    LaunchedEffect(Unit) {
        val tierIndex = ((level - 1) / Rank.STEPS).coerceIn(0, Rank.TIERS.size - 1)
        grid.scrollToItem(1 + tierIndex * (Rank.STEPS + 1))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = grid,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GameCard {
                Text("Your level", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                XpProgressBar(level, state.user.title, state.xpProgress, state.user.currentXp, state.xpRequired)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Every new level pays coins: 100 for level 2, then 25 more each level.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Rank.TIERS.forEachIndexed { tierIndex, tier ->
            val first = tierIndex * Rank.STEPS + 1
            item(span = { GridItemSpan(maxLineSpan) }, key = "tier_$tier") {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(tier, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Levels $first–${first + Rank.STEPS - 1}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            items(Rank.STEPS, key = { "level_${first + it}" }) { step -> LevelCell(first + step, level) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Past level 120 you keep the Master XII shield.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun LevelCell(cellLevel: Int, userLevel: Int) {
    val rank = Rank.forLevel(cellLevel)
    val reached = cellLevel <= userLevel
    val current = cellLevel == userLevel
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .clip(shape)
            .cardSurface(shape)
            .then(if (current) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painterResource(rank.icon), contentDescription = if (reached) rank.label else "Locked",
                modifier = Modifier
                    .size(56.dp)
                    // Levels still to reach: blurred (faded on phones too old to blur)
                    .then(if (reached) Modifier else Modifier.blur(6.dp).alpha(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.75f else 0.3f))
            )
            if (!reached) Text("🔒", fontSize = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Level $cellLevel", fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(rank.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            UserProgressEngine.titleForLevel(cellLevel), fontSize = 11.sp, maxLines = 1,
            color = if (reached) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        when {
            current -> Text("You're here", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            !reached && cellLevel > 1 -> Text("+${RewardManager.levelUpCoins(cellLevel)} 🪙", fontSize = 10.sp, color = GoldCoin.readable())
        }
    }
}
