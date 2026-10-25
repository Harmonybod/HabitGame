package com.habitgame.domain.notification

import com.habitgame.core.database.entity.UserEntity

/**
 * How many minutes before something to send reminders, stored on the user as text like "10,5,1".
 * Tasks and habits use [UserEntity.reminderLeadTimes]; shopping deadlines use
 * [UserEntity.deadlineReminderTimes].
 */
object ReminderTimes {
    /** Most reminders one task can have; each one gets its own scheduled job. */
    const val MAX = 6
    private const val LONGEST = 7 * 24 * 60

    /** Quick picks in Settings (any other number of minutes can be added too). */
    val TASK_CHOICES = listOf(0, 1, 5, 10, 15, 30, 60)
    val DEADLINE_CHOICES = listOf(5, 15, 30, 60, 120, 24 * 60)

    /** "10, 5,x,5" → [10, 5]: valid, no repeats, earliest reminder first, at most [MAX]. */
    fun parse(csv: String): List<Int> =
        csv.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..LONGEST }
            .distinct()
            .sortedDescending()
            .take(MAX)

    fun format(minutes: Collection<Int>): String = parse(minutes.joinToString(",")).joinToString(",")

    /** Adds or removes one time. Adding past [MAX] is ignored. */
    fun toggle(csv: String, minutes: Int): String {
        val current = parse(csv)
        return when {
            minutes in current -> format(current - minutes)
            current.size >= MAX -> format(current)
            else -> format(current + minutes)
        }
    }

    /** 0 → "At the time", 5 → "5 min", 90 → "1 h 30 min", 1440 → "1 day". */
    fun label(minutes: Int): String = when {
        minutes == 0 -> "At the time"
        minutes % (24 * 60) == 0 -> (minutes / (24 * 60)).let { if (it == 1) "1 day" else "$it days" }
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} h"
        minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }

    /** e.g. "1 h, 15 min and 5 min before" */
    fun describe(minutes: List<Int>): String {
        if (minutes.isEmpty()) return "No reminders"
        val parts = minutes.map { if (it == 0) "at the time" else label(it) + " before" }
        return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

/**
 * The reminder times HabitGame itself uses: none when the calendar reminds instead
 * (Settings → Calendar). Returns task/habit times and shopping deadline times.
 */
fun UserEntity.appReminderLeads(context: android.content.Context): Pair<List<Int>, List<Int>> =
    if (com.habitgame.domain.calendar.CalendarSync.appRemindersOn(context)) taskReminderLeads to deadlineReminderLeads
    else emptyList<Int>() to emptyList()

val UserEntity.taskReminderLeads: List<Int> get() = ReminderTimes.parse(reminderLeadTimes)
val UserEntity.deadlineReminderLeads: List<Int> get() = ReminderTimes.parse(deadlineReminderTimes)
