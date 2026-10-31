package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.HabitEntity
import java.time.LocalDate
import java.time.LocalTime

/** How a habit went on one day it was due. */
enum class HabitDayStatus { DONE, SKIPPED, MISSED, OPEN }   // OPEN: today, not done yet

/** Days done and days missed; skipped days count as neither. */
data class HabitRate(val done: Int = 0, val missed: Int = 0) {
    val due: Int get() = done + missed
    val percent: Float? get() = if (due == 0) null else done * 100f / due
    operator fun plus(other: HabitRate) = HabitRate(done + other.done, missed + other.missed)
}

/** Days in a range with nothing done, some done, and everything done. */
data class Performance(val nothing: Int = 0, val partial: Int = 0, val perfect: Int = 0) {
    val total: Int get() = nothing + partial + perfect
}

data class Streaks(val current: Int, val best: Int)

/** Parts of the day, from a habit's reminder time; habits without one are "Any time". */
enum class DayZone(val label: String, val emoji: String) {
    MORNING("Morning", "🌅"), AFTERNOON("Afternoon", "☀️"), EVENING("Evening", "🌙"), ANYTIME("Any time", "🕒");

    companion object {
        fun of(time: LocalTime?): DayZone = when {
            time == null -> ANYTIME
            time.hour < 12 -> MORNING
            time.hour < 18 -> AFTERNOON
            else -> EVENING
        }
    }
}

/** The numbers behind the habit sections of the Progress page, kept apart from the screens. */
object HabitStats {

    /** The day a habit started: when it was made, or its first tick if that's earlier (e.g. after a transfer). */
    fun startOf(habit: HabitEntity, done: Set<String>): LocalDate {
        val made = TaskTiming.localDateOf(habit.createdAt)
        val firstTick = done.minOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (firstTick != null && firstTick.isBefore(made)) firstTick else made
    }

    /**
     * How [habit] went on each of [dates]. Null when it wasn't due: not one of its days, before it
     * started, or still to come. [done] and [skipped] are ISO dates.
     */
    fun days(habit: HabitEntity, dates: List<LocalDate>, today: LocalDate, done: Set<String>, skipped: Set<String>): List<HabitDayStatus?> {
        val start = startOf(habit, done)
        val rule = TaskTiming.repeatRule(habit)
        return dates.map { date ->
            val iso = date.toString()
            when {
                date.isAfter(today) -> null
                iso in done -> HabitDayStatus.DONE
                date.isBefore(start) || !rule.isDueOn(date) -> null
                iso in skipped -> HabitDayStatus.SKIPPED
                date == today -> HabitDayStatus.OPEN
                else -> HabitDayStatus.MISSED
            }
        }
    }

    fun rate(statuses: List<HabitDayStatus?>): HabitRate =
        HabitRate(statuses.count { it == HabitDayStatus.DONE }, statuses.count { it == HabitDayStatus.MISSED })

    /**
     * Share of the habits due each day that were done, 0–100 (skipped ones left out; today's
     * open ones count as not done yet). Null for days with nothing due.
     */
    fun dayScores(perHabit: List<List<HabitDayStatus?>>, days: Int): List<Float?> = (0 until days).map { i ->
        val due = perHabit.mapNotNull { it.getOrNull(i) }.filter { it != HabitDayStatus.SKIPPED }
        if (due.isEmpty()) null else due.count { it == HabitDayStatus.DONE } * 100f / due.size
    }

    fun average(scores: List<Float?>): Float? = scores.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.toFloat()

    fun performance(scores: List<Float?>): Performance {
        val rated = scores.filterNotNull()
        return Performance(
            nothing = rated.count { it <= 0f },
            partial = rated.count { it > 0f && it < 99.5f },
            perfect = rated.count { it >= 99.5f }
        )
    }

    /** Success rate on each weekday, Monday first. */
    fun weeklyRhythm(perHabit: List<List<HabitDayStatus?>>, dates: List<LocalDate>): List<HabitRate> {
        val byDay = Array(7) { HabitRate() }
        perHabit.forEach { statuses ->
            statuses.forEachIndexed { i, status ->
                val day = dates[i].dayOfWeek.value - 1
                when (status) {
                    HabitDayStatus.DONE -> byDay[day] = byDay[day] + HabitRate(done = 1)
                    HabitDayStatus.MISSED -> byDay[day] = byDay[day] + HabitRate(missed = 1)
                    else -> Unit
                }
            }
        }
        return byDay.toList()
    }

    /**
     * Current and best run of due days done in a row, over the whole history. Skipped days and
     * days it isn't due don't break a run, and today doesn't either until it's over.
     */
    fun streaks(habit: HabitEntity, today: LocalDate, done: Set<String>, skipped: Set<String>): Streaks {
        val start = startOf(habit, done)
        if (start.isAfter(today)) return Streaks(0, 0)
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        var run = 0
        var best = 0
        days(habit, dates, today, done, skipped).forEach { status ->
            when (status) {
                HabitDayStatus.DONE -> { run++; best = maxOf(best, run) }
                HabitDayStatus.MISSED -> run = 0
                else -> Unit
            }
        }
        return Streaks(run, best)
    }

    /** The days just before [dates], as many of them, to compare against. */
    fun previous(dates: List<LocalDate>): List<LocalDate> {
        if (dates.isEmpty()) return emptyList()
        val first = dates.first()
        return (dates.size downTo 1).map { first.minusDays(it.toLong()) }
    }
}
