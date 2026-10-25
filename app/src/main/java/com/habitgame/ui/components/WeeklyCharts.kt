package com.habitgame.ui.components

import com.habitgame.ui.AppIcon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import com.habitgame.ui.theme.GoldCoin
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** This week's numbers for the Home graphs, Sunday first. */
data class WeekCharts(
    val weekStart: LocalDate = startOfWeek(LocalDate.now()),
    val xp: List<Int> = List(7) { 0 },
    val focusMinutes: List<Int> = List(7) { 0 },         // finished focus time each day
    val savedPlanned: List<Double> = List(7) { 0.0 },  // from savings tasks
    val savedExtra: List<Double> = List(7) { 0.0 },    // extra money added by hand
    val dailyTarget: Double = 0.0,                       // the daily amount the user set
    val currency: String = ""
) {
    fun money(amount: Double): String = currency + TaskReminderWorker.formatAmount(amount)

    /** Index of today in the week (0 = Sunday); 7 for a past week, -1 for a week still to come. */
    val todayIndex: Int
        get() {
            val today = LocalDate.now()
            return when {
                today.isBefore(weekStart) -> -1
                today.isAfter(weekStart.plusDays(6)) -> 7
                else -> (0..6).first { weekStart.plusDays(it.toLong()) == today }
            }
        }

    val isThisWeek: Boolean get() = todayIndex in 0..6

    /** "this week", or the dates, e.g. "Oct 18 – 24" or "Sep 28 – Oct 4". */
    val weekLabel: String
        get() {
            if (isThisWeek) return "this week"
            val end = weekStart.plusDays(6)
            val month = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            val endFormat = if (end.month == weekStart.month) DateTimeFormatter.ofPattern("d") else month
            return weekStart.format(month) + " – " + end.format(endFormat)
        }

    companion object {
        fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
}

/** One line on a chart. Null values (days still to come) aren't drawn. */
private data class Line(
    val values: List<Float?>,
    val color: Color,
    val dashed: Boolean = false,
    val filled: Boolean = false,
    val showPoints: Boolean = true
)

/** A dot drawn on top of a line point, e.g. a day with extra savings, with an optional label. */
private data class Marker(val index: Int, val value: Float, val color: Color, val label: String)

/** XP page of the Home card: XP earned each day of the week as a line. */
@Composable
fun XpChartPage(charts: WeekCharts, chartHeight: Dp, onPickWeek: () -> Unit, onThisWeek: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val total = charts.xp.sum()
    val best = charts.xp.indices.maxByOrNull { charts.xp[it] }?.takeIf { charts.xp[it] > 0 }
    val shown = charts.xp.mapIndexed { i, xp -> if (i <= charts.todayIndex) xp.toFloat() else null }
    Column {
        PageHeader("$total", "XP", charts, onPickWeek, onThisWeek)
        PageNote(
            if (best != null) "Best day: ${dayName(charts.weekStart, best)}." else "Nothing yet.",
            "Complete tasks, habits and focus sessions to earn XP."
        )
        WeekLineChart(
            weekStart = charts.weekStart,
            lines = listOf(Line(shown, color, filled = true)),
            emptyMax = 100f,
            height = chartHeight,
            pointLabel = { if (it > 0f) compact(it) else "" }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine(color, "XP earned each day")
        }
    }
}

/** Focus page of the Home card: minutes of finished focus each day of the week. */
@Composable
fun FocusChartPage(charts: WeekCharts, chartHeight: Dp, onPickWeek: () -> Unit, onThisWeek: () -> Unit) {
    val color = MaterialTheme.colorScheme.tertiary
    val total = charts.focusMinutes.sum()
    val best = charts.focusMinutes.indices.maxByOrNull { charts.focusMinutes[it] }?.takeIf { charts.focusMinutes[it] > 0 }
    val shown = charts.focusMinutes.mapIndexed { i, m -> if (i <= charts.todayIndex) m.toFloat() else null }
    val totalText = if (total >= 60) "${total / 60}h" + (if (total % 60 > 0) " ${total % 60}m" else "") else "${total}m"
    Column {
        PageHeader(totalText, "focus", charts, onPickWeek, onThisWeek)
        PageNote(
            if (best != null) "Best day: ${dayName(charts.weekStart, best)}." else "No focus yet.",
            "Finish focus sessions to fill this in."
        )
        WeekLineChart(
            weekStart = charts.weekStart,
            lines = listOf(Line(shown, color, filled = true)),
            emptyMax = 60f,
            height = chartHeight,
            pointLabel = { if (it > 0f) compact(it) + "m" else "" }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine(color, "Minutes of focus each day")
        }
    }
}

/**
 * Savings page of the Home card: money saved so far this week as a line, the plan the user
 * set as a dashed line, and green dots on days extra money was added.
 */
@Composable
fun SavingsChartPage(charts: WeekCharts, chartHeight: Dp, onPickWeek: () -> Unit, onThisWeek: () -> Unit) {
    val saved = MaterialTheme.colorScheme.primary
    // Extra funds in green, or gold if the accent itself is greenish
    val extra = (if (closeTo(saved, SuccessGreen)) GoldCoin else SuccessGreen).readable()
    val plan = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val totalExtra = charts.savedExtra.sum()

    // Running totals for the week
    var running = 0.0
    val cumulative = (0..6).map { i ->
        running += charts.savedPlanned[i] + charts.savedExtra[i]
        if (i <= charts.todayIndex) running.toFloat() else null
    }
    // Where the plan says you should be by each day, up to today
    val planLine = (0..6).map { i -> if (i <= charts.todayIndex) (charts.dailyTarget * (i + 1)).toFloat() else null }
    val markers = (0..6).filter { charts.savedExtra[it] > 0 && cumulative[it] != null }.map { i ->
        Marker(i, cumulative[i]!!, extra, "+" + compact(charts.savedExtra[i].toFloat()))
    }

    Column {
        PageHeader(charts.money(running), "saved", charts, onPickWeek, onThisWeek)
        PageNote(
            if (totalExtra > 0) "Includes ${charts.money(totalExtra)} extra." else "On plan so far.",
            if (charts.dailyTarget > 0) "Your plan: ${charts.money(charts.dailyTarget)} a day." else "Set a goal in the Savings tab."
        )
        WeekLineChart(
            weekStart = charts.weekStart,
            lines = buildList {
                if (charts.dailyTarget > 0) add(Line(planLine, plan, dashed = true, showPoints = false))
                add(Line(cumulative, saved, filled = true))
            },
            markers = markers,
            emptyMax = 10f,
            height = chartHeight,
            pointLabel = { "" }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine(saved, "Saved")
            if (charts.dailyTarget > 0) LegendLine(plan, "Plan", dashed = true)
            LegendRing(extra, "Extra funds")
        }
    }
}

/**
 * Big number and label, in the same style as "2 of 100 days", with the week underneath
 * the label and a calendar button to pick another week.
 */
@Composable
private fun PageHeader(value: String, label: String, charts: WeekCharts, onPickWeek: () -> Unit, onThisWeek: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontSize = if (value.length > 4) 44.sp else 64.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp,
            lineHeight = 60.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 6.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                charts.weekLabel,
                color = if (charts.isThisWeek) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                fontWeight = if (charts.isThisWeek) FontWeight.Normal else FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        if (!charts.isThisWeek) {
            IconButton(onClick = onThisWeek) {
                Icon(Icons.Filled.Today, contentDescription = "Back to this week", tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onPickWeek) {
            AppIcon(
                "CalendarMonth",
                contentDescription = "Pick a week",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PageNote(bold: String, rest: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("$bold ") }
            append(rest)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
    )
}

@Composable
private fun LegendLine(color: Color, label: String, dashed: Boolean = false) {
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

@Composable
private fun LegendRing(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).border(2.dp, color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Line chart over the week, Sunday to Saturday, with the amount on the y-axis.
 * Lines stop at today; today's day name is in bold.
 */
@Composable
private fun WeekLineChart(
    weekStart: LocalDate,
    lines: List<Line>,
    emptyMax: Float,
    height: Dp,
    pointLabel: (Float) -> String,
    markers: List<Marker> = emptyList()
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val dayStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val todayStyle = dayStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    val valueStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val pointHole = MaterialTheme.colorScheme.surface
    val todayIndex = (0..6).firstOrNull { weekStart.plusDays(it.toLong()) == LocalDate.now() }
    val highest = lines.flatMap { it.values }.filterNotNull().maxOrNull() ?: 0f
    val maxValue = niceMax(if (highest <= 0f) emptyMax else highest * 1.1f)
    val dayNames = (0..6).map { dayName(weekStart, it) }

    Canvas(Modifier.fillMaxWidth().height(height)) {
        val axisWidth = 34.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 16.dp.toPx()
        val chartHeight = bottom - top
        val slot = (size.width - axisWidth) / 7f
        fun x(i: Int) = axisWidth + slot * i + slot / 2f
        fun y(value: Float) = bottom - chartHeight * (value / maxValue)

        // Gridlines and y-axis values
        for (step in 0..4) {
            val value = maxValue * step / 4f
            val lineY = y(value)
            drawLine(grid, Offset(axisWidth, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(compact(value), axisStyle)
            drawText(label, topLeft = Offset(axisWidth - label.size.width - 6.dp.toPx(), lineY - label.size.height / 2f))
        }

        // Day names, today in bold
        dayNames.forEachIndexed { i, name ->
            val text = measurer.measure(name, if (i == todayIndex) todayStyle else dayStyle)
            drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, bottom + 6.dp.toPx()))
        }

        lines.forEach { line ->
            val points = line.values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
            if (points.isEmpty()) return@forEach
            // A plan with only one day so far: mark that day's target with a small dot
            if (points.size == 1 && !line.showPoints) drawCircle(line.color, radius = 3.dp.toPx(), center = points.first())
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            // Soft fill under the line
            if (line.filled && points.size > 1) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, bottom)
                    lineTo(points.first().x, bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(line.color.copy(alpha = 0.30f), line.color.copy(alpha = 0f)), startY = top, endY = bottom))
            }
            drawPath(
                path, line.color,
                style = Stroke(
                    width = (if (line.dashed) 1.5.dp else 3.dp).toPx(),
                    cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                )
            )
            if (line.showPoints) {
                line.values.forEachIndexed { i, v ->
                    v ?: return@forEachIndexed
                    val p = Offset(x(i), y(v))
                    drawCircle(line.color, radius = 5.dp.toPx(), center = p)
                    drawCircle(pointHole, radius = 2.5.dp.toPx(), center = p)
                    val text = pointLabel(v)
                    if (text.isNotEmpty()) {
                        val label = measurer.measure(text, valueStyle)
                        drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height - 7.dp.toPx()))
                    }
                }
            }
        }

        // Rings around the days extra money went in, so the point underneath stays visible
        markers.forEach { m ->
            val p = Offset(x(m.index), y(m.value))
            drawCircle(m.color, radius = 8.dp.toPx(), center = p, style = Stroke(width = 2.5.dp.toPx()))
            val label = measurer.measure(m.label, valueStyle.copy(color = m.color))
            drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height - 8.dp.toPx()))
        }
    }
}

private fun dayName(weekStart: LocalDate, index: Int): String =
    weekStart.plusDays(index.toLong()).dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())

/** Rounds up to a tidy number (1, 2, 4, 5, 8 or 10 × a power of ten) for the top of the axis. */
private fun niceMax(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble()))).toFloat()
    val nice = when (val n = value / magnitude) {
        in 0f..1f -> 1f
        in 1f..2f -> 2f
        in 2f..4f -> 4f
        in 4f..5f -> 5f
        else -> if (n <= 8f) 8f else 10f
    }
    return nice * magnitude
}

/** 1250 → "1.3k", 12.5 → "12.5", 40 → "40". */
private fun compact(value: Float): String = when {
    value >= 1000f -> String.format(Locale.US, "%.1fk", value / 1000f).replace(".0k", "k")
    value % 1f == 0f -> value.toInt().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

private fun closeTo(a: Color, b: Color): Boolean =
    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.45f
