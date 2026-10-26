package com.habitgame.domain.calendar

import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.HabitFrequency
import com.habitgame.core.database.entity.TaskCategoryType
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.domain.notes.SharedNotes
import com.habitgame.domain.tracker.RepeatRule
import com.habitgame.domain.tracker.TaskTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarEventsTest {
    private val zone = ZoneId.of("Africa/Addis_Ababa")   // UTC+3, no daylight saving
    private val day = LocalDate.of(2026, 10, 26)          // a Monday

    @Test
    fun repeatingTaskBecomesARepeatingEventWithReminders() {
        val task = TaskEntity(
            id = 7, name = "Gym", isRecurring = true, recurrenceDays = "MON,WED,FRI", isAllDay = false,
            scheduledTimeMillis = TaskTiming.millisOf(day.minusDays(1).atTime(18, 0)), focusDurationMinutes = 45
        )
        val e = CalendarEvents.forTask(task, listOf(10, 5), emptyList(), zone)!!
        assertEquals("task 7", e.key)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", e.rrule)
        // Created on a Sunday, so it starts on the next Monday
        assertEquals(day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(), e.start)
        assertEquals(45, e.durationMinutes)
        assertEquals(listOf(10, 5), e.reminders)
        assertEquals("task 7", CalendarEvents.keyIn(e.description))
    }

    @Test
    fun endDatesAndEveryDayRules() {
        assertEquals("FREQ=DAILY", CalendarEvents.rrule(RepeatRule(), zone, LocalTime.of(9, 0)))
        // Until Oct 30 at 09:00 in UTC+3 = 06:00 UTC
        assertEquals("FREQ=DAILY;UNTIL=20261030T060000Z", CalendarEvents.rrule(RepeatRule(endDate = LocalDate.of(2026, 10, 30)), zone, LocalTime.of(9, 0)))
    }

    @Test
    fun oneTimeTasks() {
        val dated = TaskEntity(id = 1, name = "Dentist", scheduledDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val allDay = CalendarEvents.forTask(dated, listOf(5), emptyList(), zone)!!
        assertTrue(allDay.allDay)
        assertTrue(allDay.reminders.isEmpty())

        val done = CalendarEvents.forTask(dated.copy(isCompleted = true, completedAt = 1L), listOf(5), emptyList(), zone)!!
        assertEquals("✅ Dentist", done.title)

        // No day, no time: nothing to put in a calendar
        assertNull(CalendarEvents.forTask(TaskEntity(id = 2, name = "Someday"), listOf(5), emptyList(), zone))

        // No day but a deadline: an event at the deadline, with the deadline reminders
        val deadline = TaskTiming.millisOf(day.atTime(17, 0))
        val due = CalendarEvents.forTask(TaskEntity(id = 3, name = "Report", deadlineAt = deadline), listOf(5), listOf(60, 15), zone)!!
        assertEquals(deadline, due.start)
        assertEquals("⏳ Deadline: Report", due.title)
        assertEquals(listOf(60, 15), due.reminders)
        assertNull(CalendarEvents.forTask(TaskEntity(id = 3, name = "Report", deadlineAt = deadline, isCompleted = true), listOf(5), listOf(60), zone))
    }

    @Test
    fun shoppingDeadlinesUseTheirOwnReminders() {
        val deadline = day.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val item = TaskEntity(id = 3, name = "Shoes", categoryType = TaskCategoryType.SHOPPING, deadlineAt = deadline)
        val e = CalendarEvents.forTask(item, listOf(5), listOf(60, 15, 5), zone)!!
        assertEquals(deadline, e.start)
        assertEquals(listOf(60, 15, 5), e.reminders)
        assertNull(CalendarEvents.forTask(item.copy(isCompleted = true), listOf(5), listOf(60), zone))
        assertNull(CalendarEvents.forTask(item.copy(deadlineAt = null), listOf(5), listOf(60), zone))
    }

    @Test
    fun habitsWithATimeRepeatOnTheirDays() {
        val habit = HabitEntity(
            id = 4, name = "Read", iconEmoji = "📖", frequency = HabitFrequency.SPECIFIC_DAYS, frequencyDays = "SAT,SUN",
            reminderTimeMillis = TaskTiming.millisOf(day.atTime(21, 0))
        )
        val e = CalendarEvents.forHabit(habit, listOf(5), zone)!!
        assertEquals("📖 Read", e.title)
        assertEquals("FREQ=WEEKLY;BYDAY=SA,SU", e.rrule)
        assertNull(CalendarEvents.forHabit(habit.copy(reminderTimeMillis = null), listOf(5), zone))
        assertNull(CalendarEvents.forHabit(habit.copy(isActive = false), listOf(5), zone))
    }

    @Test
    fun onlyHabitGameEventsHaveAKey() {
        assertNull(CalendarEvents.keyIn("Lunch with Sam"))
        assertNull(CalendarEvents.keyIn(null))
        assertEquals("habit 12", CalendarEvents.keyIn("Notes\n\nAdded by HabitGame. [HabitGame habit 12]"))
    }

    @Test
    fun sharedNoteTitles() {
        assertEquals("Shopping list", SharedNotes.titleFromBody("\n  Shopping list\nmilk\neggs"))
        assertEquals("milk\neggs", SharedNotes.withoutRepeatedTitle("Shopping list\nmilk\neggs", "Shopping list"))
        assertEquals("Other\nmilk", SharedNotes.withoutRepeatedTitle("Other\nmilk", "Shopping list"))
    }
}
