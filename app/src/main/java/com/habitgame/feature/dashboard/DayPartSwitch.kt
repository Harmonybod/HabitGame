package com.habitgame.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.tracker.DayBounds
import com.habitgame.domain.tracker.DayZone
import kotlinx.coroutines.launch

private val PARTS = listOf(DayZone.MORNING, DayZone.AFTERNOON, DayZone.EVENING)

/**
 * The part-of-day switch on Home: a sky with a sun that slides from sunrise to midday and turns into the moon
 * at night. Tap a part or drag the sun. "All" beside it shows the whole day (the sky then sits on the current part).
 */
@Composable
internal fun DayPartSwitch(
    selected: DayZone?,
    bounds: DayBounds,
    openCount: (DayZone?) -> Int,
    onSelect: (DayZone?) -> Unit
) {
    val now = bounds.current()
    val target = PARTS.indexOf(selected ?: now).coerceAtLeast(0).toFloat()
    val pos = remember { Animatable(target) }
    var dragging by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    LaunchedEffect(target) { if (!dragging) pos.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 220f)) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .semantics { contentDescription = "Part of the day: ${(selected ?: now).label}" }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val i = (tap.x / size.width * 3).toInt().coerceIn(0, 2)
                            select(PARTS[i])
                        }
                    }
                    .pointerInput(Unit) {
                        // Dragging moves the sun along; letting go snaps it to the nearest part
                        fun at(x: Float): Float {
                            val r = size.height / 2f
                            return ((x - r) / (size.width - 2 * r) * 2f).coerceIn(0f, 2f)
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; scope.launch { pos.snapTo(at(it.x)) } },
                            onHorizontalDrag = { change, _ -> change.consume(); scope.launch { pos.snapTo(at(change.position.x)) } },
                            onDragEnd = {
                                dragging = false
                                val i = Math.round(pos.value).coerceIn(0, 2)
                                scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = 0.72f, stiffness = 220f)) }
                                select(PARTS[i])
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { pos.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 220f)) }
                            }
                        )
                    }
            ) { drawSky(pos.value) }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                PARTS.forEach { part ->
                    val on = selected == part
                    val count = openCount(part)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "Show the ${part.label.lowercase()}") { select(part) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${part.emoji} ${part.label}",
                            fontSize = 13.sp, maxLines = 1,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (part == now) "now · $count left" else "$count left",
                            fontSize = 11.sp, maxLines = 1,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))
        val all = selected == null
        Column(
            Modifier
                .width(64.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClickLabel = "Show the whole day") { select(null) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📋", fontSize = 20.sp)
            Text(
                "All", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (all) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${openCount(null)}", fontSize = 10.sp, textAlign = TextAlign.Center,
                color = if (all) MaterialTheme.colorScheme.onPrimary.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Sky top/bottom and the sun for sunrise, midday and night
private val SKY_TOP = listOf(Color(0xFF8DB6E8), Color(0xFF3D8BDB), Color(0xFF161A33))
private val SKY_BOTTOM = listOf(Color(0xFFFFC49A), Color(0xFF9AD0F7), Color(0xFF3B4170))
private val SUN = listOf(Color(0xFFFF7429), Color(0xFFFFD23F), Color(0xFFE4E6EC))

private fun pick(colors: List<Color>, p: Float): Color =
    if (p <= 1f) lerp(colors[0], colors[1], p) else lerp(colors[1], colors[2], p - 1f)

/** Draws the switch for [p] from 0 (morning) through 1 (afternoon) to 2 (evening). */
private fun DrawScope.drawSky(p: Float) {
    val w = size.width
    val h = size.height
    val pad = h * 0.08f
    val r = h / 2f - pad
    val night = (p - 1f).coerceIn(0f, 1f)
    val cx = pad + r + p / 2f * (w - 2 * (pad + r))
    val cy = h / 2f
    val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(h / 2f))) }

    clipPath(pill) {
        drawRect(Brush.verticalGradient(listOf(pick(SKY_TOP, p), pick(SKY_BOTTOM, p))))

        // Rings of light spreading out from the sun or moon
        for (k in 3 downTo 1) drawCircle(Color.White.copy(alpha = 0.07f + 0.02f * (3 - k)), r * (1f + 0.62f * k), Offset(cx, cy))

        // Stars come out at night, on the side away from the moon
        if (night > 0f) {
            val stars = listOf(0.08f to 0.30f to 0.11f, 0.16f to 0.68f to 0.08f, 0.27f to 0.22f to 0.06f, 0.34f to 0.55f to 0.10f,
                0.44f to 0.28f to 0.05f, 0.52f to 0.70f to 0.07f, 0.60f to 0.20f to 0.09f, 0.22f to 0.45f to 0.04f)
            stars.forEach { (xy, s) -> sparkle(Offset(w * xy.first, h * xy.second), h * s, Color.White.copy(alpha = night)) }
        }

        // Clouds along the bottom by day; they sink away as night falls
        val day = 1f - night
        if (day > 0f) {
            val sink = night * h * 0.5f
            val back = Color.White.copy(alpha = 0.55f * day)
            val front = Color.White.copy(alpha = 0.95f * day)
            listOf(0.30f, 0.62f, 0.95f).forEach { x -> cloud(Offset(w * x, h * 0.86f + sink), h * 0.62f, back) }
            listOf(0.12f, 0.46f, 0.80f).forEach { x -> cloud(Offset(w * x, h * 1.0f + sink), h * 0.70f, front) }
        }

        // A soft shadow along the top edge so the sky looks set into the switch
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.3f to Color.Transparent))
    }

    // The sun, or the moon with its craters
    drawCircle(Color.Black.copy(alpha = 0.25f), r, Offset(cx + r * 0.06f, cy + r * 0.12f))
    if (night < 1f) drawCircle(pick(SUN, p).copy(alpha = 0.35f * (1f - night)), r * 1.18f, Offset(cx, cy))
    val body = pick(SUN, p)
    drawCircle(Brush.radialGradient(listOf(lerp(body, Color.White, 0.35f), body), Offset(cx - r * 0.3f, cy - r * 0.3f), r * 1.6f), r, Offset(cx, cy))
    if (night > 0f) {
        val crater = Color(0xFFA7ACBA).copy(alpha = night)
        drawCircle(crater, r * 0.26f, Offset(cx + r * 0.22f, cy - r * 0.22f))
        drawCircle(crater, r * 0.17f, Offset(cx - r * 0.34f, cy + r * 0.05f))
        drawCircle(crater, r * 0.13f, Offset(cx + r * 0.05f, cy + r * 0.42f))
    }
    drawCircle(Color.White.copy(alpha = 0.5f), r, Offset(cx, cy), style = Stroke(r * 0.05f))

    drawPath(pill, Color.White.copy(alpha = 0.35f), style = Stroke(h * 0.03f))
}

/** A four-pointed twinkle. */
private fun DrawScope.sparkle(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticTo(c.x, c.y, c.x + s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + s)
        quadraticTo(c.x, c.y, c.x - s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - s)
        close()
    }
    drawPath(path, color)
}

/** A puffy cloud of [width] sitting on [base]. */
private fun DrawScope.cloud(base: Offset, width: Float, color: Color) {
    val u = width / 2f
    // One shape, so a see-through cloud has no darker overlaps
    val path = Path().apply {
        fun puff(x: Float, y: Float, rad: Float) = addOval(Rect(Offset(base.x + x * u, base.y + y * u), rad * u))
        puff(-0.55f, -0.15f, 0.45f)
        puff(0f, -0.35f, 0.62f)
        puff(0.58f, -0.12f, 0.48f)
        addRect(Rect(base.x - u * 0.55f, base.y - u * 0.15f, base.x + u * 0.58f, base.y + u * 0.45f))
    }
    drawPath(path, color)
}
