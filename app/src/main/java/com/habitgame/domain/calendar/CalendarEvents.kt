package com.habitgame.domain.calendar

import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.domain.tracker.RepeatRule
import com.habitgame.domain.tracker.TaskTiming
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One HabitGame item as a calendar event. [key] (e.g. "task 12") is written into the event's
 * description, so HabitGame finds its own events again on every sync and on every phone.
 */
data class EventSpec(
    val key: String,
    val title: String,
    val description: String,
    val start: Long,                 // UTC millis; for all-day events, midnight UTC of the day
    val durationMinutes: Int,
    val allDay: Boolean = false,
    val rrule: String? = null,       // e.g. "FREQ=WEEKLY;BYDAY=MO,WE" for repeating items
    val reminders: List<Int> = emptyList()  // minutes before the start
) {
    /** Changes whenever anything that ends up in the calendar changes, so unchanged events aren't rewritten. */
    val fingerprint: String get() = listOf(title, description, start, durationMinutes, allDay, rrule, reminders.sorted()).joinToString("|")
}

/** Builds calendar events from tasks and habits. No Android here, so it's unit-tested. */
object CalendarEvents {
    const val MARKER_PREFIX = "[HabitGame "
    private const val DEFAULT_MINUTES = 30
    private val untilFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun marker(key: String) = "$MARKER_PREFIX$key]"

    /** "task 12" from a description written by [describe], or null for events that aren't HabitGame's. */
    fun keyIn(description: String?): String? =
        description?.substringAfterLast(MARKER_PREFIX, "")?.substringBefore("]", "")?.takeIf { it.isNotEmpty() }

    private fun describe(text: String, key: String) =
        (if (text.isBlank()) "" else text.trim() + "\n\n") + "Added by HabitGame. " + marker(key)

    /**
     * The event for [task], or null when it has nothing to show in a calendar (no day or time).
     * Finished one-time tasks get a ✅ and no reminders; bought shopping items are left out.
     */
    fun forTask(task: TaskEntity, reminders: List<Int>, deadlineReminders: List<Int>, zone: ZoneId = ZoneId.systemDefault()): EventSpec? {
        val key = "task ${task.id}"
        if (task.isShopping) {
            val deadline = task.deadlineAt ?: return null
            if (task.isCompleted) return null
            return EventSpec(key, "🛒 Deadline: buy ${task.name}", describe("Not bought by then? It's removed from your HabitGame list.", key), deadline, 15, reminders = deadlineReminders)
        }
        val time = TaskTiming.timeOfDay(task)
        val minutes = if (task.hasFocusTimer) task.focusDurationMinutes else DEFAULT_MINUTES
        if (task.isRecurring) {
            val rule = TaskTiming.repeatRule(task)
            val base = task.scheduledTimeMillis?.let { TaskTiming.localDateOf(it) } ?: LocalDate.now()
            val first = firstDueOnOrAfter(base, rule) ?: return null
            return if (time != null) {
                EventSpec(key, task.name, describe(task.description, key), first.atTime(time).atZone(zone).toInstant().toEpochMilli(), minutes, rrule = rrule(rule, zone, time), reminders = reminders)
            } else {
                EventSpec(key, task.name, describe(task.description, key), first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), 24 * 60, allDay = true, rrule = rrule(rule, zone, null))
            }
        }
        val date = TaskTiming.assignedDate(task) ?: run {
            // No day of its own: its deadline, if it has one
            val deadline = task.deadlineAt ?: return null
            if (task.isCompleted) return null
            return EventSpec(key, "⏳ Deadline: ${task.name}", describe("Not done by then? It's removed from your HabitGame tasks.", key), deadline, 15, reminders = deadlineReminders)
        }
        val done = task.isCompleted
        val title = (if (done) "✅ " else "") + task.name
        return if (time != null) {
            EventSpec(key, title, describe(task.description, key), date.atTime(time).atZone(zone).toInstant().toEpochMilli(), minutes, reminders = if (done) emptyList() else reminders)
        } else {
            EventSpec(key, title, describe(task.description, key), date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), 24 * 60, allDay = true)
        }
    }

    /** The event for a habit with a reminder time, repeating on its days. */
    fun forHabit(habit: HabitEntity, reminders: List<Int>, zone: ZoneId = ZoneId.systemDefault()): EventSpec? {
        if (!habit.isActive) return null
        val time = TaskTiming.timeOfDay(habit) ?: return null
        val rule = TaskTiming.repeatRule(habit)
        val base = habit.reminderTimeMillis?.let { TaskTiming.localDateOf(it) } ?: LocalDate.now()
        val first = firstDueOnOrAfter(base, rule) ?: return null
        val key = "habit ${habit.id}"
        return EventSpec(
            key, "${habit.iconEmoji} ${habit.name}", describe(habit.description, key),
            first.atTime(time).atZone(zone).toInstant().toEpochMilli(), DEFAULT_MINUTES,
            rrule = rrule(rule, zone, time), reminders = reminders
        )
    }

    /** "FREQ=DAILY" or "FREQ=WEEKLY;BYDAY=MO,WE", plus ";UNTIL=…" when it has an end date. */
    fun rrule(rule: RepeatRule, zone: ZoneId, time: LocalTime?): String {
        val days = TaskTiming.WEEK.filter { it in rule.days }
        val base = if (days.isEmpty() || days.size == 7) "FREQ=DAILY" else "FREQ=WEEKLY;BYDAY=" + days.joinToString(",") { it.take(2) }
        val until = rule.endDate?.let { end ->
            // The last day's event included: the end of that day, in UTC
            LocalDateTime.of(end, time ?: LocalTime.of(23, 59, 59)).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(untilFormat)
        }
        return if (until != null) "$base;UNTIL=$until" else base
    }

    /** The first day on or after [from] the rule repeats on (a week's search is enough), or null if it has ended. */
    fun firstDueOnOrAfter(from: LocalDate, rule: RepeatRule): LocalDate? =
        (0..6L).map { from.plusDays(it) }.firstOrNull { rule.isDueOn(it) }

    /** "PT30M", or "P1D" for all-day events: how repeating events give their length. */
    fun duration(spec: EventSpec): String = if (spec.allDay) "P1D" else "PT${spec.durationMinutes}M"
}
