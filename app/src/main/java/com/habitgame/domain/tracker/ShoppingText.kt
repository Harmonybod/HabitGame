package com.habitgame.domain.tracker

import com.habitgame.domain.notification.TaskReminderWorker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How shopping list items describe their price range and deadline. */
object ShoppingText {

    /** "$20–40", "$25", "up to $40", "from $20", or null when no price was given. */
    fun priceRange(min: Double, max: Double, currency: String): String? {
        fun money(v: Double) = currency + TaskReminderWorker.formatAmount(v)
        return when {
            min <= 0 && max <= 0 -> null
            max <= 0 || min == max -> money(if (min > 0) min else max)
            min <= 0 -> "up to " + money(max)
            min > max -> money(max) + "–" + TaskReminderWorker.formatAmount(min)
            else -> money(min) + "–" + TaskReminderWorker.formatAmount(max)
        }
    }

    /** "by 6:00 PM today", "by 9:30 AM tomorrow", "by Fri, Oct 31 · 6:00 PM". */
    fun deadline(at: LocalDateTime, today: LocalDate = LocalDate.now()): String {
        val time = TaskTiming.formatTime(at.toLocalTime())
        return when (at.toLocalDate()) {
            today -> "by $time today"
            today.plusDays(1) -> "by $time tomorrow"
            else -> "by " + at.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) + " · " + time
        }
    }

    fun deadline(millis: Long): String = deadline(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())

    /** Text field input → amount; blank or invalid is 0 (not given). */
    fun parseAmount(text: String): Double = text.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
}
