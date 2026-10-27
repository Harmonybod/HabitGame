package com.habitgame.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.habitgame.domain.engine.Celebration
import com.habitgame.domain.engine.Celebrations
import com.habitgame.ui.Rank
import com.habitgame.ui.theme.GoldCoin
import com.habitgame.ui.theme.NeonPink
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CelebrationsEntryPoint {
    fun celebrations(): Celebrations
}

/** Shows earned bonuses one at a time, over whatever screen is open. */
@Composable
fun CelebrationHost() {
    val context = LocalContext.current
    val celebrations = remember { EntryPointAccessors.fromApplication(context.applicationContext, CelebrationsEntryPoint::class.java).celebrations() }
    val queue by celebrations.queue.collectAsState()
    val current = queue.firstOrNull() ?: return
    key(current.id) { CelebrationOverlay(current) { celebrations.dismiss(current) } }
}

/** Confetti, the reward popping in, and the coins counting up. */
@Composable
private fun CelebrationOverlay(celebration: Celebration, onDismiss: () -> Unit) {
    // Over the whole screen, not in a Dialog: a Dialog's window is only as big as the card, leaving no room for confetti
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Taps outside the card do nothing; only Collect closes it
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Confetti(Modifier.fillMaxSize())

        val pop = remember { Animatable(0.5f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        val coins = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(300)
            coins.animateTo(celebration.coins.toFloat(), tween(1_000, easing = FastOutSlowInEasing))
        }
        val wobble by rememberInfiniteTransition(label = "wobble").animateFloat(
            initialValue = -8f, targetValue = 8f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "wobble"
        )

        Column(
            Modifier
                .padding(32.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val level = celebration.level
            if (level != null) {
                Image(
                    painterResource(Rank.forLevel(level).icon), contentDescription = null,
                    modifier = Modifier.size(104.dp).graphicsLayer { rotationZ = wobble / 2 }
                )
            } else {
                Text(celebration.emoji, fontSize = 64.sp, modifier = Modifier.graphicsLayer { rotationZ = wobble })
            }
            Text(
                celebration.title, color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center
            )
            Text(
                if (level != null) "${celebration.message} · ${Rank.forLevel(level).label}" else celebration.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.habitgame.ui.PackEmoji("🪙", 34.dp)
                Text("+${coins.value.toInt()}", color = GoldCoin.readable(), fontWeight = FontWeight.Black, fontSize = 38.sp)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Collect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

private class Piece(val x: Float, val delay: Float, val speed: Float, val size: Float, val spin: Float, val sway: Float, val color: Color)

/** A burst of confetti falling from the top for a few seconds. */
@Composable
private fun Confetti(modifier: Modifier) {
    val colors = listOf(GoldCoin, NeonPink, SuccessGreen, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    val pieces = remember {
        List(90) {
            Piece(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.8f, speed = 0.25f + Random.nextFloat() * 0.35f,
                size = 6f + Random.nextFloat() * 10f, spin = Random.nextFloat() * 720f - 360f,
                sway = Random.nextFloat() * 6f, color = colors[it % colors.size]
            )
        }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (seconds < 5f) {
            withFrameNanos { seconds = (it - start) / 1_000_000_000f }
        }
    }
    Canvas(modifier) {
        pieces.forEach { p ->
            val t = seconds - p.delay
            if (t < 0f) return@forEach
            val y = (-0.05f + p.speed * t) * size.height
            if (y > size.height) return@forEach
            val x = p.x * size.width + sin((t * p.sway).toDouble()).toFloat() * 24.dp.toPx()
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x - p.size, y - p.size / 2), size = Size(p.size * 2, p.size))
            }
        }
    }
}
