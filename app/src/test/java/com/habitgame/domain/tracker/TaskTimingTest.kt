package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TaskTimingTest {

    private val today = LocalDate.of(2026, 10, 24) // a Saturday

    private fun dailyAt(hour: Int, minute: Int, completedAt: LocalDateTime? = null, days: String = "") = TaskEntity(
        name = "Breakfast",
        isAllDay = false,
        isRecurring = true,
        recurrenceDays = days,
        notifyEnabled = true,
        scheduledTimeMillis = TaskTiming.millisOf(today.minusDays(30).atTime(hour, minute)),
        isCompleted = completedAt != null,
        completedAt = completedAt?.let { TaskTiming.millisOf(it) }
    )

    @Test
    fun `repeating task later today fires today`() {
        val next = TaskTiming.nextOccurrence(dailyAt(8, 0), today.atTime(7, 0))
        assertEquals(today.atTime(8, 0), next)
    }

    @Test
    fun `repeating task already past fires tomorrow`() {
        val next = TaskTiming.nextOccurrence(dailyAt(8, 0), today.atTime(9, 0))
        assertEquals(today.plusDays(1).atTime(8, 0), next)
    }

    @Test
    fun `repeating task done early today skips to tomorrow`() {
        val task = dailyAt(20, 0, completedAt = today.atTime(18, 0))
        val next = TaskTiming.nextOccurrence(task, today.atTime(19, 0))
        assertEquals(today.plusDays(1).atTime(20, 0), next)
    }

    @Test
    fun `repeating task respects chosen weekdays`() {
        val task = dailyAt(18, 0, days = "MON,WED")
        val next = TaskTiming.nextOccurrence(task, today.atTime(7, 0))
        assertEquals(LocalDate.of(2026, 10, 26).atTime(18, 0), next) // Monday
    }

    @Test
    fun `one-time task uses its picked date`() {
        val date = today.plusDays(3)
        val task = TaskEntity(
            name = "Dentist",
            isAllDay = false,
            scheduledDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            scheduledTimeMillis = TaskTiming.millisOf(today.atTime(15, 30)),
            notifyEnabled = true
        )
        assertEquals(date.atTime(15, 30), TaskTiming.nextOccurrence(task, today.atTime(10, 0)))
    }

    @Test
    fun `completed or all-day one-time tasks have no reminder`() {
        val base = TaskEntity(name = "x", isAllDay = false, scheduledTimeMillis = TaskTiming.millisOf(today.atTime(23, 0)))
        assertNull(TaskTiming.nextOccurrence(base.copy(isCompleted = true), today.atTime(10, 0)))
        assertNull(TaskTiming.nextOccurrence(base.copy(isAllDay = true), today.atTime(10, 0)))
    }

    @Test
    fun `repeating task stops after its end date`() {
        val task = dailyAt(8, 0).copy(repeatEndDate = today.toString())
        assertTrue(TaskTiming.isDueOn(task, today))
        assertTrue(!TaskTiming.isDueOn(task, today.plusDays(1)))
        assertNull(TaskTiming.nextOccurrence(task, today.atTime(9, 0)))
    }

    @Test
    fun `habit reminders follow days and end date`() {
        val habit = com.habitgame.core.database.entity.HabitEntity(
            name = "Lunch",
            reminderTimeMillis = TaskTiming.millisOf(today.atTime(13, 0)),
            frequency = com.habitgame.core.database.entity.HabitFrequency.SPECIFIC_DAYS,
            frequencyDays = "SAT,SUN",
            repeatEndDate = today.plusDays(1).toString()
        )
        // Saturday morning: today at 1 PM
        assertEquals(today.atTime(13, 0), TaskTiming.nextOccurrence(habit, completedToday = false, now = today.atTime(9, 0)))
        // Done today: Sunday at 1 PM
        assertEquals(today.plusDays(1).atTime(13, 0), TaskTiming.nextOccurrence(habit, completedToday = true, now = today.atTime(9, 0)))
        // After Sunday it has ended
        assertNull(TaskTiming.nextOccurrence(habit, completedToday = false, now = today.plusDays(1).atTime(14, 0)))
        assertTrue(!TaskTiming.isDueOn(habit, today.plusDays(2)))
    }

    @Test
    fun `repeat rule describes itself`() {
        assertEquals("Every day", RepeatRule().describe())
        assertEquals("Weekdays", RepeatRule(setOf("MON", "TUE", "WED", "THU", "FRI")).describe())
    }

    @Test
    fun `day number is clamped to the 100 days`() {
        assertEquals(1, TrackerManager.dayNumber("2026-10-24", today))
        assertEquals(10, TrackerManager.dayNumber("2026-10-15", today))
        assertEquals(100, TrackerManager.dayNumber("2025-01-01", today))
        assertEquals(1, TrackerManager.dayNumber("2026-12-01", today))
    }

    @Test
    fun `plan content covers every day`() {
        for (day in 1..TrackerPlan.TOTAL_DAYS) {
            assertTrue(TrackerPlan.phaseFor(day).name.isNotBlank())
            assertTrue(TrackerPlan.promptFor(day).isNotBlank())
        }
    }

    @Test
    fun `only tasks given a day count toward that day's circle`() {
        val dated = TaskEntity(name = "Dentist", scheduledDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        assertTrue(TaskTiming.countsToward(dated, today))
        assertTrue(!TaskTiming.countsToward(dated, today.plusDays(1)))

        // A one-time task with a time but no date belongs to the day of that time
        val timed = TaskEntity(name = "Call", isAllDay = false, scheduledTimeMillis = TaskTiming.millisOf(today.atTime(15, 0)))
        assertTrue(TaskTiming.countsToward(timed, today))

        // "Any time" tasks never count, open or done
        val car = TaskEntity(name = "Buy a car", categoryType = com.habitgame.core.database.entity.TaskCategoryType.SHOPPING)
        val someday = TaskEntity(name = "Clean garage")
        val boughtToday = car.copy(isCompleted = true, completedAt = TaskTiming.millisOf(today.atTime(10, 0)))
        listOf(car, someday, boughtToday).forEach {
            assertTrue(TaskTiming.isAnytime(it))
            assertTrue(!TaskTiming.countsToward(it, today))
        }
        // Even a shopping item with a deadline today isn't planned for today
        val due = car.copy(deadlineAt = TaskTiming.millisOf(today.atTime(18, 0)))
        assertTrue(!TaskTiming.countsToward(due, today))
        assertTrue(TaskTiming.countsToward(dailyAt(9, 0), today))
    }
}
