package com.habitgame.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.engine.ActiveSession
import kotlinx.coroutines.delay

/** The current time, updated every second, for live countdowns. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** "1h 05m left" for long timers, "12:34 left" for the last hour. */
fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m left"
    else "$minutes:${seconds.toString().padStart(2, '0')} left"
}

/** A draining bar with the time left, for one running Shop purchase. */
@Composable
fun SessionCountdown(
    session: ActiveSession,
    now: Long,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
    onExtend: ((ActiveSession) -> Unit)? = null
) {
    val color = if (session.unlocksApps) MaterialTheme.colorScheme.primary else boardColors().sun
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showName) {
                Text(
                    "${session.item.iconEmoji} ${session.item.name}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                formatRemaining(session.remainingMillis(now)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { session.fractionLeft(now) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = boardColors().empty,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {}
        )
        // Nearly over: 5 more hours for 50 coins
        if (onExtend != null && com.habitgame.domain.engine.BoosterManager.canExtend(session, now)) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { onExtend(session) }, modifier = Modifier.fillMaxWidth()) {
                Text("⏳ +5 hours for ${com.habitgame.domain.engine.BoosterManager.EXTEND_COST} 🪙", fontWeight = FontWeight.Bold)
            }
        }
        if (session.countsUsage) {
            // Bought unlock time is only spent inside the locked apps, so it's always paused in here
            Spacer(Modifier.height(4.dp))
            Text(
                "⏸ Paused · only counts down while a locked app is open",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

/** Home: everything currently counting down, shown under the header. */
@Composable
fun ActiveSessionsCard(sessions: List<ActiveSession>, now: Long, modifier: Modifier = Modifier, onExtend: ((ActiveSession) -> Unit)? = null) {
    GameCard(modifier) {
        Text("⏳ Running now", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        sessions.forEach { session ->
            Spacer(Modifier.height(12.dp))
            SessionCountdown(session, now, showName = true, onExtend = onExtend)
        }
    }
}
