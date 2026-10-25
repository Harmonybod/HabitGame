package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.HabitFrequency
import com.habitgame.core.database.entity.TaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How something repeats: on which weekdays (empty = every day) and until when (null = no end). */
data class RepeatRule(val days: Set<String> = emptySet(), val endDate: LocalDate? = null) {
    fun isDueOn(date: LocalDate): Boolean =
        (endDate == null || !date.isAfter(endDate)) &&
            (days.isEmpty() || TaskTiming.dayAbbrev(date.dayOfWeek) in days)

    fun hasEnded(today: LocalDate = LocalDate.now()): Boolean = endDate != null && today.isAfter(endDate)

    /** e.g. "Every day · until Nov 30" */
    fun describe(): String {
        val dayText = when {
            days.isEmpty() -> "Every day"
            days.size == 7 -> "Every day"
            days == setOf("MON", "TUE", "WED", "THU", "FRI") -> "Weekdays"
            days == setOf("SAT", "SUN") -> "Weekends"
            else -> TaskTiming.WEEK.filter { it in days }.joinToString(" ") { it.take(1) + it.drop(1).lowercase() }
        }
        val endText = endDate?.let { " · until " + it.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) } ?: ""
        return dayText + endText
    }

    companion object {
        fun parseDays(csv: String): Set<String> =
            csv.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

        fun parseEnd(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
}

/** Date/time rules for tasks and habits: when they're due and when to remind about them. */
object TaskTiming {
    val WEEK = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun dayAbbrev(day: DayOfWeek): String = day.name.take(3)

    fun localDateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun millisOf(dateTime: LocalDateTime): Long = dateTime.atZone(zone).toInstant().toEpochMilli()

    fun localTimeOf(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /** Stores a time of day the way tasks and habits keep it: as a timestamp today at that time. */
    fun millisForTime(time: LocalTime): Long = millisOf(LocalDate.now().atTime(time))

    fun formatTime(time: LocalTime): String = time.format(timeFormat)

    // ── Tasks ──────────────────────────────────────────────────────────────

    /** Time of day the task is planned for, or null for all-day tasks. */
    fun timeOfDay(task: TaskEntity): LocalTime? {
        if (task.isAllDay) return null
        return task.scheduledTimeMillis?.let { localTimeOf(it) }
    }

    fun repeatRule(task: TaskEntity): RepeatRule =
        RepeatRule(RepeatRule.parseDays(task.recurrenceDays), RepeatRule.parseEnd(task.repeatEndDate))

    /** Date picked for a one-time task. The date picker stores UTC midnight. */
    fun scheduledDate(task: TaskEntity): LocalDate? =
        task.scheduledDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    /**
     * The day a one-time task is planned for: its date, or the day of its time. Null for tasks
     * that can be done any day, like shopping items (a deadline isn't a planned day).
     */
    fun assignedDate(task: TaskEntity): LocalDate? {
        if (task.isRecurring || task.isShopping) return null
        return scheduledDate(task) ?: if (!task.isAllDay) task.scheduledTimeMillis?.let { localDateOf(it) } else null
    }

    fun isDueOn(task: TaskEntity, date: LocalDate): Boolean =
        if (task.isRecurring) repeatRule(task).isDueOn(date) else assignedDate(task) == date

    /** Can be done any day: a one-time task with no day of its own (e.g. a shopping item). */
    fun isAnytime(task: TaskEntity): Boolean = !task.isRecurring && assignedDate(task) == null

    fun completedOn(task: TaskEntity, date: LocalDate): Boolean =
        task.isCompleted && task.completedAt?.let { localDateOf(it) == date } == true

    /**
     * Tasks that count toward a day's circle on the 100-day board: only the ones given that day.
     * "Any time" tasks never do, so an open shopping item can't leave a day half done.
     */
    fun countsToward(task: TaskEntity, date: LocalDate): Boolean =
        !isAnytime(task) && (isDueOn(task, date) || completedOn(task, date))

    /** The next date/time this task should happen, or null if it has no time or is finished. */
    fun nextOccurrence(task: TaskEntity, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val time = timeOfDay(task) ?: return null
        if (task.isRecurring) {
            return nextRepeat(time, repeatRule(task), completedOn(task, now.toLocalDate()), now)
        }
        if (task.isCompleted) return null
        val date = scheduledDate(task) ?: task.scheduledTimeMillis?.let { localDateOf(it) } ?: return null
        val at = date.atTime(time)
        return if (at.isAfter(now)) at else null
    }

    // ── Habits ─────────────────────────────────────────────────────────────

    fun timeOfDay(habit: HabitEntity): LocalTime? = habit.reminderTimeMillis?.let { localTimeOf(it) }

    fun repeatRule(habit: HabitEntity): RepeatRule = RepeatRule(
        if (habit.frequency == HabitFrequency.SPECIFIC_DAYS) RepeatRule.parseDays(habit.frequencyDays) else emptySet(),
        RepeatRule.parseEnd(habit.repeatEndDate)
    )

    fun isDueOn(habit: HabitEntity, date: LocalDate): Boolean = habit.isActive && repeatRule(habit).isDueOn(date)

    fun nextOccurrence(habit: HabitEntity, completedToday: Boolean, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!habit.isActive) return null
        val time = timeOfDay(habit) ?: return null
        return nextRepeat(time, repeatRule(habit), completedToday, now)
    }

    // ── Shared ─────────────────────────────────────────────────────────────

    private fun nextRepeat(time: LocalTime, rule: RepeatRule, doneToday: Boolean, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        for (offset in 0..7L) {
            val date = today.plusDays(offset)
            if (!rule.isDueOn(date)) continue
            if (offset == 0L && doneToday) continue
            val at = date.atTime(time)
            if (at.isAfter(now)) return at
        }
        return null
    }
}
