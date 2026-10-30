package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.DayProgressEntity
import com.habitgame.core.database.entity.SavingsEntryEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One day of the 100-day savings chart. [actual] is null for days still to come; [extra] is money
 * added by hand that day, on top of what savings tasks put aside.
 */
data class SavingsDay(
    val day: Int,
    val date: LocalDate,
    val actual: Double?,
    val target: Double,
    val extra: Double = 0.0
) {
    /** Ahead (positive) or behind (negative) the target; null for days still to come. */
    val difference: Double? get() = actual?.let { it - target }
}

/** The numbers behind the Progress page and the savings chart, kept apart from the screens. */
object ProgressStats {

    /** The days a range covers, oldest first, ending today: the last [days] days, or the whole challenge for 100. */
    fun range(days: Int, today: LocalDate, challengeStart: LocalDate): List<LocalDate> =
        if (days >= TrackerPlan.TOTAL_DAYS) (0 until TrackerPlan.TOTAL_DAYS).map { challengeStart.plusDays(it.toLong()) }
        else (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

    /**
     * Share of each day's tasks and habits that were done, 0–100. Null for days with nothing
     * planned and for days still to come.
     */
    fun completionRates(dates: List<LocalDate>, progress: List<DayProgressEntity>, today: LocalDate): List<Float?> {
        val byDate = progress.associateBy { it.date }
        return dates.map { date ->
            val p = byDate[date.toString()]
            if (date.isAfter(today) || p == null || p.total <= 0) null else p.fraction * 100f
        }
    }

    /**
     * Cumulative savings against the plan for each of the 100 challenge days.
     * Target: [goal] spread evenly, i.e. min(day × goal/100, goal). Actual: every amount saved up to
     * and including that day (money saved before the start counts from day 1); withdrawals, saved as
     * negative amounts, bring it down. Days after [today] have no actual amount yet.
     */
    fun savings(start: LocalDate, goal: Double, entries: List<SavingsEntryEntity>, today: LocalDate): List<SavingsDay> {
        val daily = if (goal <= 0) 0.0 else goal / TrackerPlan.TOTAL_DAYS
        // Each entry lands on its challenge day (before the start = day 1)
        val perDay = DoubleArray(TrackerPlan.TOTAL_DAYS)
        val extraPerDay = DoubleArray(TrackerPlan.TOTAL_DAYS)
        entries.forEach { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@forEach
            val index = ChronoUnit.DAYS.between(start, date).toInt().coerceAtLeast(0)
            if (index < TrackerPlan.TOTAL_DAYS) {
                perDay[index] += entry.amount
                // Added by hand rather than by a savings task
                if (entry.taskId == null && entry.amount > 0) extraPerDay[index] += entry.amount
            }
        }
        var running = 0.0
        return (0 until TrackerPlan.TOTAL_DAYS).map { i ->
            val date = start.plusDays(i.toLong())
            running += perDay[i]
            SavingsDay(
                day = i + 1,
                date = date,
                actual = if (date.isAfter(today)) null else running,
                target = minOf(daily * (i + 1), goal),
                extra = extraPerDay[i]
            )
        }
    }
}
