package com.habitgame.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val RANGES = listOf(7 to "Week", 15 to "15 days", 30 to "30 days", 60 to "60 days", 100 to "100 days")

/**
 * Progress: how each day went (share of tasks and habits done), XP, focus and savings, over a
 * week up to the whole 100 days, each graph with what it means.
 */
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val dates = state.dates
    val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val longDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    // Day labels: a handful along the bottom whatever the range
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
    val chartHeight = 200.dp

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("📈 Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (state.rangeDays >= 100) "The whole 100-day challenge" else "The last ${state.rangeDays} days, up to today",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { (days, label) -> ChoiceChip(label, state.rangeDays == days) { viewModel.setRange(days) } }
            }
        }

        // ── How each day went ──
        item {
            val rated = state.completion.filterNotNull()
            val average = if (rated.isEmpty()) null else rated.average().roundToInt()
            val perfect = rated.count { it >= 99.5f }
            val best = state.completion.indices.filter { state.completion[it] != null }.maxByOrNull { state.completion[it]!! }
            GameCard {
                SectionTitle("✅", "Daily completion", average?.let { "$it%" } ?: "–", "average")
                Explain(
                    "The share of each day's tasks and habits you finished. ",
                    "It's what fills the circles on the 100-day board: a full circle is 100%. Days with nothing planned are left out, and anything you skipped doesn't count against you."
                )
                StatRow(
                    "Days rated" to "${rated.size}",
                    "All done" to "$perfect",
                    "Best day" to (best?.let { dates[it].format(shortDate) } ?: "–")
                )
                Spacer(Modifier.height(12.dp))
                SeriesChart(
                    series = listOf(ChartSeries(state.completion, SuccessGreen.readable(), filled = true)),
                    height = chartHeight, xLabel = xLabel, yFormat = { "${it.roundToInt()}%" }, maxValue = 100f,
                    details = { i ->
                        listOf(dates[i].format(longDate), state.completion[i]?.let { "${it.roundToInt()}% done" } ?: "Nothing planned")
                    }
                )
            }
        }

        // ── XP ──
        item {
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
                    height = chartHeight, xLabel = xLabel, yFormat = { compactAmount(it) },
                    details = { i -> listOf(dates[i].format(longDate), "${state.xp[i]} XP") }
                )
            }
        }

        // ── Focus ──
        item {
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
                    height = chartHeight, xLabel = xLabel, yFormat = { "${it.roundToInt()}m" },
                    details = { i -> listOf(dates[i].format(longDate), minutesText(state.focusMinutes[i])) }
                )
            }
        }

        // ── Savings ──
        item {
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

        // ── The board ──
        item {
            GameCard {
                SectionTitle("🟣", "The 100-day board", "", "")
                Explain(
                    "One circle per day of the challenge. ",
                    "Each fills up as you finish that day's tasks and habits (the Daily completion above). Any-time tasks and shopping items never count, so they can't leave a day half done. Finishing everything planned for a day pays 100 bonus coins."
                )
            }
        }
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
