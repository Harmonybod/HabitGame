package com.habitgame.domain.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.habitgame.MainActivity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.tracker.TaskTiming
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Habit reminders work like task reminders: wave 0 before the habit's time, once for each
 * reminder time picked in Settings, wave 1 a nudge 30 minutes later if it still isn't done.
 * After the nudge the habit reschedules itself for its next day.
 */
@HiltWorker
class HabitReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val habitRepo: HabitRepository,
    private val userRepo: UserRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong(KEY_HABIT_ID, -1L)
        val wave = inputData.getInt(KEY_REMINDER_WAVE, 0)
        val habit = habitRepo.getHabitById(habitId) ?: return Result.success()
        val doneToday = habitRepo.isHabitCompletedToday(habit.id)

        if (habit.isActive && !doneToday && TaskTiming.isDueOn(habit, java.time.LocalDate.now())) {
            notify(applicationContext, habit, wave, habitRepo.getHabitStreak(habit.id))
        }
        if (wave == WAVE_NUDGE) {
            schedule(applicationContext, habit, doneToday, (userRepo.getUser() ?: com.habitgame.core.database.entity.UserEntity()).appReminderLeads(applicationContext).first)
        }
        return Result.success()
    }

    companion object {
        const val KEY_HABIT_ID = "habit_id"
        const val KEY_REMINDER_WAVE = "reminder_wave"
        const val CHANNEL_HABITS = "habitgame_habits"
        private const val WAVE_REMINDER = 0
        private const val WAVE_NUDGE = 1
        private const val NUDGE_AFTER_MINUTES = 30L

        fun notificationId(habitId: Long, wave: Int): Int = 500_000 + (habitId * 2 + wave).toInt()

        /** Schedule (or replace) the next reminder and nudge. Cancels them if the habit has no upcoming time. */
        fun schedule(context: Context, habit: HabitEntity, completedToday: Boolean, leads: List<Int>) {
            val wm = WorkManager.getInstance(context)
            val now = LocalDateTime.now()
            val next = TaskTiming.nextOccurrence(habit, completedToday, now)
            if (next == null) {
                cancelReminders(context, habit.id)
                return
            }
            // Reminders whose time has already passed are skipped; if all have, one goes out now
            val upcoming = leads.map { next.minusMinutes(it.toLong()) }.filter { it.isAfter(now) }
            val remindAts = upcoming.ifEmpty { if (leads.isEmpty()) emptyList() else listOf(now) }
            fun enqueue(name: String, wave: Int, at: LocalDateTime) {
                val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
                    .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_HABIT_ID to habit.id, KEY_REMINDER_WAVE to wave))
                    .addTag("habit_reminder_${habit.id}")
                    .build()
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            }
            for (i in 0 until ReminderTimes.MAX) {
                val at = remindAts.getOrNull(i)
                if (at != null) enqueue(leadName(habit.id, i), WAVE_REMINDER, at)
                else wm.cancelUniqueWork(leadName(habit.id, i))
            }
            wm.cancelUniqueWork(uniqueName(habit.id, WAVE_REMINDER))  // the single reminder older versions scheduled
            enqueue(uniqueName(habit.id, WAVE_NUDGE), WAVE_NUDGE, next.plusMinutes(NUDGE_AFTER_MINUTES))
        }

        fun cancelReminders(context: Context, habitId: Long) {
            val wm = WorkManager.getInstance(context)
            (0..2).forEach { wave -> wm.cancelUniqueWork(uniqueName(habitId, wave)) }
            for (i in 0 until ReminderTimes.MAX) wm.cancelUniqueWork(leadName(habitId, i))
        }

        private fun leadName(habitId: Long, index: Int) = "habit_${habitId}_lead$index"

        fun dismissNotifications(context: Context, habitId: Long) {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(notificationId(habitId, WAVE_REMINDER))
            nm.cancel(notificationId(habitId, WAVE_NUDGE))
        }

        private fun uniqueName(habitId: Long, wave: Int) = "habit_${habitId}_wave$wave"

        private fun notify(context: Context, habit: HabitEntity, wave: Int, streak: Int) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val timeLabel = TaskTiming.timeOfDay(habit)?.let { TaskTiming.formatTime(it) }
            val title = if (wave == WAVE_REMINDER) {
                "${habit.iconEmoji} ${habit.name}" + (timeLabel?.let { " · $it" } ?: "")
            } else {
                "🔥 Still to do: ${habit.name}"
            }
            val text = when {
                wave == WAVE_NUDGE && streak > 0 -> "Keep your $streak-day streak going. Tap Done when it's finished."
                habit.description.isNotBlank() -> habit.description
                else -> "Tap Done when it's finished to earn ${habit.xpReward} XP."
            }

            val openApp = PendingIntent.getActivity(
                context, notificationId(habit.id, wave),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val markDone = PendingIntent.getBroadcast(
                context, notificationId(habit.id, wave),
                Intent(context, TaskActionReceiver::class.java)
                    .setAction(TaskActionReceiver.ACTION_COMPLETE_HABIT)
                    .putExtra(TaskActionReceiver.EXTRA_HABIT_ID, habit.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_HABITS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openApp)
                .addAction(android.R.drawable.checkbox_on_background, "Done ✓", markDone)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId(habit.id, wave), notification)
        }
    }
}
