package com.habitgame.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.ui.theme.LocalGameColors

/**
 * Colours of the 100-day board. The fill follows the theme's highlight, or the accent colour
 * when one is picked. [glass] adds a light rim and shine to each circle (Glass theme).
 */
data class BoardColors(val sun: Color, val sunInk: Color, val empty: Color, val ring: Color, val dot: Color, val glass: Boolean = false)

@Composable
fun boardColors(): BoardColors {
    val scheme = MaterialTheme.colorScheme
    val game = LocalGameColors.current
    return BoardColors(
        sun = game.highlight,
        sunInk = game.onHighlight,
        empty = if (game.isGlass) (if (game.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f)) else scheme.onSurface.copy(alpha = if (game.isDark) 0.13f else 0.11f),
        ring = scheme.onSurface,
        dot = scheme.onSurface.copy(alpha = 0.55f),
        glass = game.isGlass
    )
}

/**
 * A 10×10 grid of circles, one per day. Each circle fills with gold from the bottom up
 * as that day's checklist gets done. Today gets a ring, unstarted milestone days get a dot
 * in the middle, and future days are faded.
 *
 * @param scores fraction done for each day, index 0 = day 1
 */
@Composable
fun HundredDayBoard(
    scores: List<Float>,
    currentDay: Int,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = boardColors()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val fullDays = scores.count { it >= 1f }
    val gapDp = 5.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = "100-day board. Day $currentDay. $fullDays days fully done." }
            .pointerInput(currentDay) {
                detectTapGestures { pos ->
                    val gap = gapDp.toPx()
                    val cell = (size.width - gap * 9) / 10f
                    val col = (pos.x / (cell + gap)).toInt().coerceIn(0, 9)
                    val row = (pos.y / (cell + gap)).toInt().coerceIn(0, 9)
                    val day = row * 10 + col + 1
                    if (day <= currentDay) onDayClick(day)
                }
            }
    ) {
        val gap = gapDp.toPx()
        val cell = (size.width - gap * 9) / 10f
        for (i in 0 until TrackerPlan.TOTAL_DAYS) {
            val day = i + 1
            val col = i % 10
            val row = i / 10
            val score = scores.getOrElse(i) { 0f }
            val scale = if (day == selectedDay) 1.18f else 1f
            val radius = cell / 2f * scale
            val center = Offset(col * (cell + gap) + cell / 2f, row * (cell + gap) + cell / 2f)
            val alpha = if (day > currentDay) 0.45f else 1f

            drawDayCircle(
                center = center,
                radius = radius,
                fill = (score * reveal.value).coerceIn(0f, 1f),
                colors = colors,
                alpha = alpha,
                milestone = day in TrackerPlan.MILESTONES && score == 0f
            )
            if (day == currentDay) {
                val stroke = 2.5.dp.toPx()
                drawCircle(colors.ring, radius = radius + stroke / 2f, center = center, style = Stroke(stroke))
            }
        }
    }
}

/** The row of ten days that today is in (days 1–10, 11–20, …), drawn like the full board. */
@Composable
fun BoardRow(scores: List<Float>, currentDay: Int, modifier: Modifier = Modifier) {
    val colors = boardColors()
    val first = (currentDay - 1).coerceAtLeast(0) / 10 * 10   // index of the row's first day
    val gapDp = 5.dp
    val ringDp = 2.5.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = (maxWidth - ringDp * 2 - gapDp * 9) / 10
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(cell + ringDp * 2)
                .semantics { contentDescription = "Days ${first + 1} to ${first + 10}. Day $currentDay." }
        ) {
            val gap = gapDp.toPx()
            val edge = ringDp.toPx()
            val size = cell.toPx()
            for (col in 0 until 10) {
                val day = first + col + 1
                val score = scores.getOrElse(day - 1) { 0f }
                val center = Offset(edge + col * (size + gap) + size / 2f, edge + size / 2f)
                drawDayCircle(center, size / 2f, score.coerceIn(0f, 1f), colors, if (day > currentDay) 0.45f else 1f, day in TrackerPlan.MILESTONES && score == 0f)
                if (day == currentDay) drawCircle(colors.ring, radius = size / 2f + edge / 2f, center = center, style = Stroke(edge))
            }
        }
    }
}

private fun DrawScope.drawDayCircle(
    center: Offset,
    radius: Float,
    fill: Float,
    colors: BoardColors,
    alpha: Float,
    milestone: Boolean
) {
    if (fill >= 1f) {
        drawCircle(colors.sun, radius, center, alpha = alpha)
    } else {
        drawCircle(colors.empty, radius, center, alpha = alpha)
        if (fill > 0f) {
            val bounds = Rect(center, radius)
            val circle = Path().apply { addOval(bounds) }
            clipPath(circle) {
                val top = bounds.bottom - bounds.height * fill
                drawRect(colors.sun, topLeft = Offset(bounds.left, top), size = Size(bounds.width, bounds.bottom - top), alpha = alpha)
            }
        }
        if (milestone) {
            drawCircle(colors.dot, radius * 0.24f, center, alpha = alpha)
        }
    }
    if (colors.glass) drawGlassShine(center, radius, alpha)
}

/** Glass theme: a thin light rim and a soft shine on the upper left, like a glass bead. */
private fun DrawScope.drawGlassShine(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f)), startY = center.y - radius, endY = center.y + radius),
        radius = radius - 0.5.dp.toPx(), center = center, alpha = alpha, style = Stroke(1.dp.toPx())
    )
    val shine = Offset(center.x - radius * 0.32f, center.y - radius * 0.38f)
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = shine, radius = radius * 0.55f),
        topLeft = Offset(shine.x - radius * 0.45f, shine.y - radius * 0.28f),
        size = Size(radius * 0.9f, radius * 0.56f),
        alpha = alpha
    )
}

/** One small board circle, used in the legend. */
@Composable
fun BoardDot(fill: Float, size: androidx.compose.ui.unit.Dp = 10.dp, milestone: Boolean = false) {
    val colors = boardColors()
    Canvas(Modifier.size(size)) {
        drawDayCircle(Offset(this.size.width / 2, this.size.height / 2), this.size.minDimension / 2, fill, colors, 1f, milestone)
    }
}

@Composable
fun BoardLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(1f, "All done")
        LegendItem(0.5f, "Partly done")
        LegendItem(0f, "Not yet")
    }
}

@Composable
private fun LegendItem(fill: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BoardDot(fill)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Round tick-off button in the style of the tracker page's checklist. */
@Composable
fun RoundCheck(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = boardColors()
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.border(2.dp, colors.sun, CircleShape)
                else Modifier.border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 1f), CircleShape)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.matchParentSize()) { drawCircle(colors.sun) }
            Text("✓", color = colors.sunInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

/** A circular progress ring with content in the middle (savings goal). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = boardColors()
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) { animated.animateTo(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.empty, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                colors.sun, -90f, 360f * animated.value, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        content()
    }
}
