package com.habitgame.ui.components

import com.habitgame.ui.theme.cardSurface
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.ui.theme.*

// ────────────────────────────────────────────────────────────────────
// XP Progress Bar
// ────────────────────────────────────────────────────────────────────
@Composable
fun XpProgressBar(
    level: Int,
    title: String,
    progress: Float,
    currentXp: Long,
    xpRequired: Long,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "xpProgress"
    )
    val rank = com.habitgame.ui.Rank.forLevel(level)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Rank shield for this level, with the level number on a small tag underneath
            Box(Modifier.size(56.dp), contentAlignment = Alignment.BottomCenter) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(rank.icon),
                    contentDescription = "Rank ${rank.label}",
                    modifier = Modifier.fillMaxSize().padding(bottom = 6.dp)
                )
                Text(
                    "LV $level",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$title · ${rank.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currentXp / $xpRequired XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// Coin Balance Chip
// ────────────────────────────────────────────────────────────────────
@Composable
fun CoinBalanceChip(coins: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, GoldDim.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        com.habitgame.ui.PackEmoji("🪙", 20.dp)
        Text(
            coins.toString(),
            fontWeight = FontWeight.Bold,
            color = GoldCoin.readable(),
            fontSize = 15.sp
        )
    }
}

/** Small streak counter for the top of Home, next to the coins. */
@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (streak > 0) WarningAmber.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        com.habitgame.ui.PackEmoji("🔥", 18.dp)
        Text("$streak", fontWeight = FontWeight.Bold, color = WarningAmber.readable(), fontSize = 15.sp)
    }
}

// ────────────────────────────────────────────────────────────────────
// Streak Badge
// ────────────────────────────────────────────────────────────────────
@Composable
fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (streak > 0) WarningAmber.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (streak > 0) GoldDim.copy(0.4f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        com.habitgame.ui.PackEmoji(if (streak > 0) "🔥" else "💤", 20.dp)
        Text(
            "$streak day${if (streak != 1) "s" else ""}",
            fontWeight = FontWeight.SemiBold,
            color = if (streak > 0) WarningAmber.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

// ────────────────────────────────────────────────────────────────────
// Game Card Container
// ────────────────────────────────────────────────────────────────────
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .cardSurface(RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

// ────────────────────────────────────────────────────────────────────
// Difficulty Badge
// ────────────────────────────────────────────────────────────────────
@Composable
fun DifficultyBadge(label: String, badgeColor: Color) {
    val color = badgeColor.readable()
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}
