package com.habitgame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.SavingsDay
import com.habitgame.ui.theme.CyanXp
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** One line on a [SeriesChart]. Null values (nothing that day, or days to come) leave a gap. */
data class ChartSeries(
    val values: List<Float?>,
    val color: Color,
    val dashed: Boolean = false,
    val filled: Boolean = false,
    val thin: Boolean = false
)

/** A ring around one point of a line, e.g. a day extra money went in. */
data class ChartMarker(val index: Int, val value: Float, val color: Color)

/** A level drawn across the chart with a label, e.g. half of the savings goal. */
data class ChartLevel(val value: Float, val label: String)

/**
 * A line chart over any number of days. Tap it to see that day's numbers ([details]); tap again
 * elsewhere to move, or on the same day to hide them.
 */
@Composable
fun SeriesChart(
    series: List<ChartSeries>,
    height: Dp,
    xLabel: (Int) -> String?,
    yFormat: (Float) -> String,
    details: (Int) -> List<String>,
    modifier: Modifier = Modifier,
    maxValue: Float? = null,
    yTicks: Int? = null,
    levels: List<ChartLevel> = emptyList(),
    markers: List<ChartMarker> = emptyList()
) {
    val count = series.maxOfOrNull { it.values.size } ?: 0
    if (count == 0) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val tipStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    val tipTitle = tipStyle.copy(fontWeight = FontWeight.Bold)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val levelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    val tipBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val cursor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val highest = series.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0f
    val top = maxValue?.takeIf { it > 0f && it >= highest } ?: niceTop(if (highest <= 0f) 10f else highest * 1.1f)
    var selected by remember(count) { mutableStateOf<Int?>(null) }
    // Steps that land on round numbers: 5 → 1, 2, 3…; 8 → 2, 4, 6…
    val ticks = yTicks ?: evenTicks(top)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(count) {
                detectTapGestures { offset ->
                    val axis = 46.dp.toPx()
                    val slot = (size.width - axis) / count
                    val index = ((offset.x - axis) / slot).toInt().coerceIn(0, count - 1)
                    selected = if (selected == index) null else index
                }
            }
    ) {
        val axis = 46.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val topY = 10.dp.toPx()
        val slot = (size.width - axis) / count
        fun x(i: Int) = axis + slot * i + slot / 2f
        fun y(v: Float) = bottom - (bottom - topY) * (v / top).coerceIn(0f, 1.05f)

        // Gridlines and the amounts up the side
        for (step in 0..ticks) {
            val value = top * step / ticks
            drawLine(grid, Offset(axis, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(yFormat(value), axisStyle)
            drawText(label, topLeft = Offset(axis - label.size.width - 6.dp.toPx(), y(value) - label.size.height / 2f))
        }
        // Milestones across the chart
        levels.forEach { level ->
            val ly = y(level.value)
            drawLine(levelColor, Offset(axis, ly), Offset(size.width, ly), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val label = measurer.measure(level.label, axisStyle)
            drawText(label, topLeft = Offset(size.width - label.size.width - 2.dp.toPx(), ly - label.size.height - 1.dp.toPx()))
        }
        // Day labels along the bottom
        for (i in 0 until count) {
            val text = xLabel(i) ?: continue
            val label = measurer.measure(text, axisStyle)
            drawText(label, topLeft = Offset((x(i) - label.size.width / 2f).coerceIn(axis, size.width - label.size.width), bottom + 4.dp.toPx()))
        }

        series.forEach { line ->
            // Split into runs without gaps
            val runs = mutableListOf<MutableList<Offset>>()
            var current: MutableList<Offset>? = null
            line.values.forEachIndexed { i, v ->
                if (v == null) { current = null; return@forEachIndexed }
                if (current == null) current = mutableListOf<Offset>().also { runs += it }
                current!!.add(Offset(x(i), y(v)))
            }
            runs.forEach { points ->
                if (points.size == 1) {
                    drawCircle(line.color, radius = 3.dp.toPx(), center = points.first())
                    return@forEach
                }
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                if (line.filled) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, bottom)
                        lineTo(points.first().x, bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(line.color.copy(alpha = 0.32f), line.color.copy(alpha = 0f)), startY = topY, endY = bottom))
                }
                drawPath(
                    path, line.color,
                    style = Stroke(
                        width = (if (line.thin || line.dashed) 1.5.dp else 2.5.dp).toPx(),
                        cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                    )
                )
            }
        }

        // Rings, so the point underneath stays visible
        markers.forEach { m ->
            drawCircle(m.color, radius = 5.dp.toPx(), center = Offset(x(m.index), y(m.value)), style = Stroke(width = 2.dp.toPx()))
        }

        // The tapped day: a line down, its points, and its numbers in a box
        selected?.let { i ->
            drawLine(cursor, Offset(x(i), topY), Offset(x(i), bottom), strokeWidth = 1.dp.toPx())
            series.forEach { line -> line.values.getOrNull(i)?.let { drawCircle(line.color, radius = 4.5.dp.toPx(), center = Offset(x(i), y(it))) } }
            val lines = details(i)
            if (lines.isNotEmpty()) {
                val texts = lines.mapIndexed { n, t -> measurer.measure(t, if (n == 0) tipTitle else tipStyle) }
                val pad = 8.dp.toPx()
                val w = texts.maxOf { it.size.width } + pad * 2
                val h = texts.sumOf { it.size.height } + pad * 2
                val left = (x(i) + 10.dp.toPx()).let { if (it + w > size.width) x(i) - 10.dp.toPx() - w else it }.coerceAtLeast(0f)
                drawRoundRect(tipBackground, topLeft = Offset(left, topY), size = Size(w, h), cornerRadius = CornerRadius(10.dp.toPx()))
                var ty = topY + pad
                texts.forEach { t ->
                    drawText(t, topLeft = Offset(left + pad, ty))
                    ty += t.size.height
                }
            }
        }
    }
}

/** A small ring and its name, under a chart. */
@Composable
fun ChartLegendRing(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(11.dp)) {
            drawCircle(color, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small coloured line and its name, under a chart. */
@Composable
fun ChartLegend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 3.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Savings so far against the plan over the 100 days: a cyan area for what's been saved, a dashed
 * grey line for where the plan says you should be, and quarter-goal milestones. Tap a day for its numbers.
 * [firstDay]/[lastDay] (1-based) show part of the challenge.
 */
@Composable
fun SavingsProgressChart(
    days: List<SavingsDay>,
    goal: Double,
    currency: String,
    height: Dp,
    modifier: Modifier = Modifier,
    firstDay: Int = 1,
    lastDay: Int = days.size
) {
    if (days.isEmpty()) return
    val shown = days.subList((firstDay - 1).coerceIn(0, days.size - 1), lastDay.coerceIn(firstDay, days.size))
    val target = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val money = { v: Double -> currency + TaskReminderWorker.formatAmount(v) }
    val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    // Day labels: every 10th day over the whole challenge, fewer on short stretches
    val every = when {
        shown.size > 60 -> 20
        shown.size > 30 -> 10
        shown.size > 10 -> 5
        else -> 1
    }
    val highestSaved = shown.mapNotNull { it.actual }.maxOrNull() ?: 0.0
    val extraColor = SuccessGreen.readable()
    Column(modifier) {
        SeriesChart(
            series = listOf(
                ChartSeries(shown.map { it.target.toFloat() }, target, dashed = true),
                ChartSeries(shown.map { it.actual?.toFloat() }, CyanXp, filled = true)
            ),
            height = height,
            xLabel = { i -> shown[i].day.let { d -> if (i == 0 || d % every == 0) "D$d" else null } },
            yFormat = { String.format(Locale.US, "%,d", it.roundToInt()) },
            maxValue = if (goal > 0 && highestSaved <= goal && lastDay - firstDay >= 60) goal.toFloat() else null,
            yTicks = 5,
            levels = if (goal > 0) listOf(0.25, 0.5, 0.75).map { ChartLevel((goal * it).toFloat(), "${(it * 100).roundToInt()}%") } else emptyList(),
            // Days money was added by hand, on top of savings tasks
            markers = shown.mapIndexedNotNull { i, d -> if (d.extra > 0 && d.actual != null) ChartMarker(i, d.actual.toFloat(), extraColor) else null },
            details = { i ->
                val d = shown[i]
                buildList {
                    add("Day ${d.day} · ${d.date.format(dateFormat)}")
                    if (d.actual != null) add("Saved: ${money(d.actual)}") else add("Still to come")
                    if (d.extra > 0) add("Extra funds: +${money(d.extra)}")
                    add("Plan: ${money(d.target)}")
                    d.difference?.let { diff ->
                        add(
                            when {
                                abs(diff) < 0.5 -> "Right on plan"
                                diff > 0 -> "Ahead by ${money(diff)}"
                                else -> "Behind by ${money(-diff)}"
                            }
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartLegend(CyanXp, "Saved so far")
            ChartLegend(target, "Plan", dashed = true)
            ChartLegendRing(extraColor, "Extra funds")
        }
    }
}

/** 15000 → "15k", 3000 → "3k", 750 → "750". */
fun compactAmount(value: Float): String = when {
    value >= 1000f -> String.format(Locale.US, "%.1fk", value / 1000f).replace(".0k", "k")
    value % 1f == 0f -> value.toInt().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

/** How many steps divide [top] into round numbers. */
private fun evenTicks(top: Float): Int {
    val lead = top / 10.0.pow(floor(log10(top.toDouble()))).toFloat()
    return if (abs(lead - 5f) < 0.01f || abs(lead - 10f) < 0.01f) 5 else 4
}

/** Rounds up to a tidy number for the top of the axis. */
private fun niceTop(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble()))).toFloat()
    val nice = when (val n = value / magnitude) {
        in 0f..1f -> 1f
        in 1f..2f -> 2f
        in 2f..5f -> 5f
        else -> if (n <= 8f) 8f else 10f
    }
    return nice * magnitude
}
