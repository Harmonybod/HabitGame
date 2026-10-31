package com.habitgame.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.DayZone
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.WarningAmber
import com.habitgame.ui.theme.readable
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val RANGES = listOf(7 to "7 days", 14 to "14 days", 28 to "28 days", 60 to "60 days", 100 to "100 days")

/**
 * Progress: a calendar of how each day went, the average daily score, patterns (weekdays, parts of
 * the day, missed habits), areas and every habit, then XP, focus and savings over the same days.
 * Pick an area at the top to see only its habits.
 */
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val dates = state.dates
    val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val longDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val every = when {
        dates.size > 60 -> 20
        dates.size > 30 -> 10
        dates.size > 10 -> 5
        else -> 1
    }
    val xLabel = { i: Int ->
        if (dates.size <= 7) dates.getOrNull(i)?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
        else if (i == 0 || i % every == 0) dates.getOrNull(i)?.format(shortDate) else null
    }
    fun pastOnly(values: List<Int>): List<Float?> = values.mapIndexed { i, v -> if (dates.getOrNull(i)?.isAfter(state.today) == true) null else v.toFloat() }
    var showPatterns by rememberSaveable { mutableStateOf(true) }
    var showAreas by rememberSaveable { mutableStateOf(true) }
    var showHabits by rememberSaveable { mutableStateOf(true) }
    var showCharts by rememberSaveable { mutableStateOf(true) }
    val primary = MaterialTheme.colorScheme.primary

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Range and area ──
        item(key = "head") {
            Text("Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (if (state.rangeDays >= 100) "The whole 100-day challenge" else "The last ${state.rangeDays} days") +
                    (state.area?.let { " · $it" } ?: " · everything"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { (days, label) -> ChoiceChip(label, state.rangeDays == days) { viewModel.setRange(days) } }
            }
            if (state.areas.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("All areas", state.area == null) { viewModel.setArea(null) }
                    state.areas.forEach { area ->
                        ChoiceChip("${area.iconEmoji} ${area.name}", state.area == area.name) { viewModel.setArea(area.name) }
                    }
                }
            }
        }

        // ── Calendar ──
        item(key = "calendar") {
            GameCard {
                SectionTitle("📅", "Each day", "", "")
                Explain(
                    "How much of each day got done. ",
                    if (state.area == null) "Tasks and habits planned for the day, as on the 100-day board. Tap a day for its score."
                    else "The habits in ${state.area} that were due that day. Tap a day for its score."
                )
                CalendarHeatmap(dates, state.scores, state.today)
            }
        }

        // ── Average daily score ──
        item(key = "score") {
            GameCard {
                Text("Average daily score", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.average?.let { "${(it * 10).roundToInt() / 10f}%" } ?: "–",
                        fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    ChangeBadge(state.average, state.previousAverage)
                }
                Explain(
                    "The average of each day's score. ",
                    "A day's score is the share of what was planned that got done; skipped ones don't count against you. The arrow compares it with the ${state.rangeDays} days before."
                )
                SeriesChart(
                    series = listOf(ChartSeries(state.scores, SuccessGreen.readable(), filled = true)),
                    height = 140.dp, xLabel = xLabel, yFormat = { "${it.roundToInt()}%" }, maxValue = 100f,
                    details = { i -> listOf(dates[i].format(longDate), state.scores[i]?.let { "${it.roundToInt()}% done" } ?: "Nothing planned") }
                )
                Spacer(Modifier.height(12.dp))
                Text("Performance", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                PerformanceBar(state.performance)
            }
        }

        // ── Patterns ──
        item(key = "patterns_head") { SectionHeader("Patterns", showPatterns) { showPatterns = !showPatterns } }
        if (showPatterns) {
            val left = ProgressUi.PATTERN_DAYS - state.daysTracked
            if (state.loaded && left > 0) {
                item(key = "patterns_locked") {
                    GameCard {
                        Text("🔒 Gathering your patterns…", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Track $left more day${if (left == 1) "" else "s"} to unlock your weekly rhythm, best part of the day and missed habits.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                item(key = "rhythm") {
                    GameCard {
                        SectionTitle("📆", "Weekly rhythm", "", "")
                        Explain("How often habits get done on each weekday. ", "The darkest bar is your best day.")
                        WeekdayBars(state.rhythm, primary)
                    }
                }
                if (state.zones.isNotEmpty()) item(key = "zones") {
                    GameCard {
                        SectionTitle("🕰️", "Best part of the day", "", "")
                        Explain("How often habits get done, by when they're set for. ", "Morning is before noon, afternoon until 6 PM, then evening; habits without a time are \"Any time\".")
                        state.zones.forEach { z ->
                            val color = when (z.zone) {
                                DayZone.MORNING -> areaColor("#26A69A")
                                DayZone.AFTERNOON -> areaColor("#FF9800")
                                DayZone.EVENING -> areaColor("#5E35B1")
                                DayZone.ANYTIME -> MaterialTheme.colorScheme.primary
                            }
                            ZoneBar(z.zone.emoji, z.zone.label, z.habits, z.rate, color)
                        }
                    }
                }
                item(key = "missed") {
                    GameCard {
                        SectionTitle("🙈", "Missed habits", "", "")
                        Explain("Days a habit was skipped or not done. ", "Skipping (swipe left on Home) doesn't hurt your score; missing does.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LegendDot(WarningAmber, "Skipped", state.missed.skipped)
                                LegendDot(MaterialTheme.colorScheme.error, "Missed", state.missed.missed)
                                state.missed.mostSkipped?.let { (name, n) -> MostLine("Most skipped", name, n) }
                                state.missed.mostMissed?.let { (name, n) -> MostLine("Most missed", name, n) }
                                if (state.missed.skipped + state.missed.missed == 0) {
                                    Text("🎉 Nothing missed in these days.", color = SuccessGreen.readable(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            MissedDonut(state.missed.skipped, state.missed.missed)
                        }
                    }
                }
            }
        }

        // ── Areas ──
        if (state.areaCards.isNotEmpty()) {
            item(key = "areas_head") { SectionHeader("Areas", showAreas) { showAreas = !showAreas } }
            if (showAreas) item(key = "areas") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.areaCards, key = { it.area.name }) { card ->
                        val color = areaColor(card.area.colorHex)
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(color.copy(alpha = 0.16f))
                                .clickable(onClickLabel = "Show only ${card.area.name}") { viewModel.setArea(card.area.name) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(card.area.iconEmoji, fontSize = 22.sp)
                                Spacer(Modifier.weight(1f))
                                PercentRing(card.rate.percent, color)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(card.area.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${card.habits} habit${if (card.habits == 1) "" else "s"} · ${card.rate.done}/${card.rate.due} days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // ── Habits ──
        if (state.habitCards.isNotEmpty()) {
            item(key = "habits_head") { SectionHeader("Habits", showHabits) { showHabits = !showHabits } }
            if (showHabits) items(state.habitCards, key = { "habit_${it.habit.id}" }) { card ->
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.habit.iconEmoji, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(card.habit.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (card.streaks.current > 0) Text("🔥 ${card.streaks.current}d", color = WarningAmber.readable(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("SCORE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(card.rate.percent?.let { "${it.roundToInt()}%" } ?: "–", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(6.dp))
                                ChangeBadge(card.rate.percent, card.previous.percent)
                            }
                        }
                        MiniStat("DONE", "${card.rate.done}/${card.rate.due} days")
                        MiniStat("BEST STREAK", "${card.streaks.best}d")
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusStrip(card.strip)
                }
            }
        }

        // ── Our charts: XP, focus, savings ──
        item(key = "charts_head") { SectionHeader("XP, focus & savings", showCharts) { showCharts = !showCharts } }
        if (showCharts) {
            item(key = "xp") {
                val total = state.xp.sum()
                val pastDays = dates.count { !it.isAfter(state.today) }.coerceAtLeast(1)
                val best = state.xp.indices.maxByOrNull { state.xp[it] }?.takeIf { state.xp[it] > 0 }
                GameCard {
                    SectionTitle("⭐", "XP earned", compactAmount(total.toFloat()), "XP")
                    Explain(
                        "XP from everything you finish. ",
                        "Tasks pay by difficulty (Easy 20, Medium 50, Hard 100, Epic 200), habits and focus sessions add more, your streak multiplies it and Double XP from the Shop doubles it. Each level needs 100 XP more than the last."
                    )
                    StatRow(
                        "Per day" to "${total / pastDays}",
                        "Best day" to (best?.let { "${state.xp[it]}" } ?: "–"),
                        "On" to (best?.let { dates[it].format(shortDate) } ?: "–")
                    )
                    Spacer(Modifier.height(12.dp))
                    SeriesChart(
                        series = listOf(ChartSeries(pastOnly(state.xp), MaterialTheme.colorScheme.primary, filled = true)),
                        height = 200.dp, xLabel = xLabel, yFormat = { compactAmount(it) },
                        details = { i -> listOf(dates[i].format(longDate), "${state.xp[i]} XP") }
                    )
                }
            }
            item(key = "focus") {
                val total = state.focusMinutes.sum()
                val pastDays = dates.count { !it.isAfter(state.today) }.coerceAtLeast(1)
                val focusDays = state.focusMinutes.count { it > 0 }
                GameCard {
                    SectionTitle("⏱", "Focus time", minutesText(total), "focused")
                    Explain(
                        "Minutes of finished focus each day. ",
                        "Only completed focus blocks count (breaks don't). Longer focus earns more: 4 XP and 2 coins a minute, boosted by your streak."
                    )
                    StatRow(
                        "Per day" to minutesText(total / pastDays),
                        "Days focused" to "$focusDays",
                        "Longest" to minutesText(state.focusMinutes.maxOrNull() ?: 0)
                    )
                    Spacer(Modifier.height(12.dp))
                    SeriesChart(
                        series = listOf(ChartSeries(pastOnly(state.focusMinutes), MaterialTheme.colorScheme.tertiary, filled = true)),
                        height = 200.dp, xLabel = xLabel, yFormat = { "${it.roundToInt()}m" },
                        details = { i -> listOf(dates[i].format(longDate), minutesText(state.focusMinutes[i])) }
                    )
                }
            }
            item(key = "savings") {
                val todayPoint = state.savings.lastOrNull { it.actual != null }
                val money = { v: Double -> state.currency + TaskReminderWorker.formatAmount(v) }
                GameCard {
                    SectionTitle("💰", "Savings", money(todayPoint?.actual ?: 0.0), if (state.goal > 0) "of ${money(state.goal)}" else "saved")
                    Explain(
                        "Everything saved so far, added up day by day. ",
                        if (state.goal > 0) "The dashed line is the plan: ${money(state.goal / 100)} a day, reaching ${money(state.goal)} on day 100. " +
                            "Bigger deposits make the line climb faster; a day without saving keeps it flat. Tap a day to compare it with the plan."
                        else "Set a savings goal in the Savings tab to see the plan alongside it."
                    )
                    todayPoint?.difference?.takeIf { state.goal > 0 }?.let { diff ->
                        Text(
                            when {
                                abs(diff) < 0.5 -> "🎯 Right on plan today."
                                diff > 0 -> "🚀 ${money(diff)} ahead of plan."
                                else -> "⏳ ${money(-diff)} behind plan."
                            },
                            color = if (diff >= 0) SuccessGreen.readable() else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    SavingsProgressChart(
                        days = state.savings, goal = state.goal, currency = state.currency, height = 240.dp,
                        firstDay = state.savingsDays.first, lastDay = state.savingsDays.last
                    )
                }
            }
            item(key = "board") {
                GameCard {
                    SectionTitle("🟣", "The 100-day board", "", "")
                    Explain(
                        "One circle per day of the challenge. ",
                        "Each fills up as you finish that day's tasks and habits (the calendar above, with everything picked). Any-time tasks and shopping items never count, so they can't leave a day half done. Finishing everything planned for a day pays 100 bonus coins."
                    )
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

/** "Patterns ▾": a section title you can fold away. */
@Composable
private fun SectionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = if (open) "Fold" else "Unfold", onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(if (open) "ExpandMore" else "ChevronRight", contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label · $count day${if (count == 1) "" else "s"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MostLine(label: String, name: String, days: Int) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label: ") }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" (${days}d)") }
        },
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(Modifier.padding(start = 12.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionTitle(emoji: String, title: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.habitgame.ui.PackEmoji(emoji, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Explain(bold: String, rest: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(bold) }
            append(rest)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
}

@Composable
private fun StatRow(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth()) {
        stats.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun minutesText(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h" + (if (minutes % 60 > 0) " ${minutes % 60}m" else "") else "${minutes}m"
