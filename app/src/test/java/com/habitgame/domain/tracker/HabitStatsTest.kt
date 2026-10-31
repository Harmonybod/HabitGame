package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.HabitFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HabitStatsTest {
    private val today = LocalDate.of(2026, 10, 31)   // a Saturday
    private fun habit(start: LocalDate, days: String = "") = HabitEntity(
        id = 1, name = "Read",
        createdAt = TaskTiming.millisOf(start.atStartOfDay()),
        frequency = if (days.isEmpty()) HabitFrequency.DAILY else HabitFrequency.SPECIFIC_DAYS,
        frequencyDays = days
    )
    private fun lastDays(n: Int) = (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

    @Test
    fun daysAreDoneSkippedMissedOrOpenToday() {
        val h = habit(today.minusDays(3))
        val dates = lastDays(5)   // Oct 27..31; started Oct 28
        val done = setOf(today.minusDays(3).toString())
        val skipped = setOf(today.minusDays(2).toString())
        val days = HabitStats.days(h, dates, today, done, skipped)
        assertEquals(listOf(null, HabitDayStatus.DONE, HabitDayStatus.SKIPPED, HabitDayStatus.MISSED, HabitDayStatus.OPEN), days)
        assertEquals(HabitRate(done = 1, missed = 1), HabitStats.rate(days))
        assertEquals(50f, HabitStats.rate(days).percent!!, 0.01f)
    }

    @Test
    fun daysItIsNotDueDontCount() {
        val h = habit(today.minusDays(13), "MON,WED,FRI")
        val days = HabitStats.days(h, lastDays(7), today, emptySet(), emptySet())
        assertEquals(3, days.count { it != null })   // Mon, Wed, Fri of the last week
    }

    @Test
    fun dayScoresLeaveSkippedOutAndCountOpenAsNotDone() {
        val a = listOf(HabitDayStatus.DONE, HabitDayStatus.SKIPPED, HabitDayStatus.OPEN)
        val b = listOf(HabitDayStatus.MISSED, null, HabitDayStatus.DONE)
        val scores = HabitStats.dayScores(listOf(a, b), 3)
        assertEquals(50f, scores[0]!!, 0.01f)
        assertNull(scores[1])
        assertEquals(50f, scores[2]!!, 0.01f)
        assertEquals(Performance(nothing = 0, partial = 2, perfect = 0), HabitStats.performance(scores))
    }

    @Test
    fun weeklyRhythmIsMondayFirst() {
        val h = habit(today.minusDays(20))
        val dates = lastDays(7)
        val done = dates.filter { it.dayOfWeek.value <= 5 }.map { it.toString() }.toSet()
        val rhythm = HabitStats.weeklyRhythm(listOf(HabitStats.days(h, dates, today, done, emptySet())), dates)
        assertEquals(100f, rhythm[0].percent!!, 0.01f)   // Monday
        assertEquals(0f, rhythm[6].percent!!, 0.01f)     // Sunday missed
        assertNull(rhythm[5].percent)                    // Saturday is today, still open
    }

    @Test
    fun streaksSurviveSkipsAndAnOpenToday() {
        val h = habit(today.minusDays(9))
        val done = (1..9).filter { it != 4 && it != 7 }.map { today.minusDays(it.toLong()).toString() }.toSet()
        val skipped = setOf(today.minusDays(4).toString())
        // Day 7 ago missed; then 6,5,(4 skipped),3,2,1 done; today open
        assertEquals(Streaks(current = 5, best = 5), HabitStats.streaks(h, today, done, skipped))
    }

    @Test
    fun zonesComeFromTheReminderTime() {
        assertEquals(DayZone.MORNING, DayZone.of(LocalTime.of(6, 30)))
        assertEquals(DayZone.AFTERNOON, DayZone.of(LocalTime.of(12, 0)))
        assertEquals(DayZone.EVENING, DayZone.of(LocalTime.of(21, 0)))
        assertEquals(DayZone.ANYTIME, DayZone.of(null))
    }

    @Test
    fun previousRangeIsTheSameLengthJustBefore() {
        val dates = lastDays(7)
        val prev = HabitStats.previous(dates)
        assertEquals(7, prev.size)
        assertEquals(dates.first().minusDays(1), prev.last())
    }
}
