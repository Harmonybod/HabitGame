package com.habitgame.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.tracker.HabitDayStatus
import com.habitgame.domain.tracker.HabitRate
import com.habitgame.domain.tracker.Performance
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.WarningAmber
import com.habitgame.ui.theme.readable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The parts of the Progress page drawn from habits: heatmap, performance bar, weekday bars and so on. */

private val WEEK_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * A calendar of [dates] (Monday first), each day filled by its score (0–100): empty, part-filled
 * or full. Days to come stay blank and today is outlined. Tap a day to see its score.
 */
@Composable
fun CalendarHeatmap(dates: List<LocalDate>, scores: List<Float?>, today: LocalDate, modifier: Modifier = Modifier) {
    if (dates.isEmpty()) return
    val colors = boardColors()
    val lead = dates.first().dayOfWeek.value - 1                  // blank cells before the first day
    val cells: List<Int?> = List(lead) { null } + dates.indices.toList()
    val weeks = cells.chunked(7)
    val cellHeight: Dp = if (weeks.size > 6) 16.dp else 28.dp
    var picked by remember(dates) { mutableStateOf<Int?>(null) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            WEEK_LETTERS.forEach { d ->
                Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { col ->
                    val index = week.getOrNull(col)
                    val date = index?.let { dates[it] }
                    val score = index?.let { scores.getOrNull(it) }
                    val future = date?.isAfter(today) == true
                    val fill = when {
                        date == null || future -> Color.Transparent
                        score == null -> colors.empty.copy(alpha = 0.18f)   // nothing planned that day
                        else -> lerp(colors.empty, colors.sun, (score / 100f).coerceIn(0f, 1f))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .clip(RoundedCornerShape(5.dp))
                            .background(fill)
                            .then(if (date == today) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp)) else Modifier)
                            .then(if (date != null && !future) Modifier.clickable { picked = if (picked == index) null else index } else Modifier)
                    )
                }
            }
        }
        picked?.let { i ->
            Text(
                dates[i].format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) + ": " +
                    (scores.getOrNull(i)?.let { "${it.roundToInt()}% done" } ?: "nothing planned"),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HeatLegend(colors.empty, "Nothing")
            HeatLegend(lerp(colors.empty, colors.sun, 0.5f), "Some")
            HeatLegend(colors.sun, "All done")
        }
    }
}

@Composable
private fun HeatLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One bar split into days with nothing done, some done and everything done, with the counts under it. */
@Composable
fun PerformanceBar(performance: Performance) {
    val colors = boardColors()
    val parts = listOf(
        Triple(performance.nothing, colors.empty, "Nothing"),
        Triple(performance.partial, lerp(colors.empty, colors.sun, 0.5f), "Partial"),
        Triple(performance.perfect, colors.sun, "Perfect")
    )
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.empty.copy(alpha = 0.4f))) {
        parts.filter { it.first > 0 }.forEach { (count, color, _) ->
            Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        parts.forEach { (count, color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Spacer(Modifier.width(5.dp))
                Text("$count $label", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Success rate for each weekday, Monday first, as bars from 0 to 100%. */
@Composable
fun WeekdayBars(rates: List<HabitRate>, barColor: Color = MaterialTheme.colorScheme.primary) {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val best = rates.mapNotNull { it.percent }.maxOrNull()
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        rates.forEachIndexed { i, rate ->
            val pct = rate.percent
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(pct?.let { "${it.roundToInt()}%" } ?: "–", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height((100 * ((pct ?: 0f) / 100f)).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (pct != null && pct == best) barColor else barColor.copy(alpha = 0.45f))
                )
                Spacer(Modifier.height(4.dp))
                Text(names[i], fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One bar per part of the day: how often habits there get done. */
@Composable
fun ZoneBar(emoji: String, label: String, habits: Int, rate: HabitRate, color: Color) {
    val pct = rate.percent
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(
                (pct?.let { "${it.roundToInt()}%" } ?: "N/A") + " · $habits habit" + if (habits == 1) "" else "s",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(((pct ?: 0f) / 100f).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

/** A ring split into skipped and missed days, the total in the middle. */
@Composable
fun MissedDonut(skipped: Int, missed: Int, size: Dp = 96.dp) {
    val skipColor = WarningAmber
    val missColor = MaterialTheme.colorScheme.error
    val track = boardColors().empty
    val total = skipped + missed
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (total > 0) {
                val skipSweep = 360f * skipped / total
                drawArc(skipColor, -90f, skipSweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                drawArc(missColor, -90f + skipSweep, 360f - skipSweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("days", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A habit's recent days as small squares: done, missed, skipped, still open today, or not due. */
@Composable
fun StatusStrip(statuses: List<HabitDayStatus?>) {
    val colors = boardColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        statuses.forEach { status ->
            val color = when (status) {
                HabitDayStatus.DONE -> colors.sun
                HabitDayStatus.MISSED -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                HabitDayStatus.SKIPPED -> WarningAmber.copy(alpha = 0.55f)
                HabitDayStatus.OPEN -> colors.empty
                null -> colors.empty.copy(alpha = 0.35f)
            }
            Box(
                Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(color)
                    .then(if (status == HabitDayStatus.OPEN) Modifier.border(1.dp, colors.sun, RoundedCornerShape(3.dp)) else Modifier)
            )
        }
    }
}

/** "↑ 12%" in green or "↓ 4%" in red: the change from the range before. */
@Composable
fun ChangeBadge(now: Float?, before: Float?) {
    if (now == null || before == null) return
    val diff = now - before
    if (kotlin.math.abs(diff) < 0.5f) {
        Text("= same as before", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val up = diff > 0
    val color = if (up) SuccessGreen.readable() else MaterialTheme.colorScheme.error
    Text(
        (if (up) "↑ " else "↓ ") + "${kotlin.math.abs(diff).roundToInt()}%",
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** A small ring with a percentage in it, for area cards. */
@Composable
fun PercentRing(percent: Float?, color: Color, size: Dp = 54.dp) {
    val track = color.copy(alpha = 0.18f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * ((percent ?: 0f) / 100f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(percent?.let { "${it.roundToInt()}%" } ?: "–", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
