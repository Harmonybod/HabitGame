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
import com.habitgame.core.database.entity.ActivityCategory
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.domain.tracker.TrackerPlan
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Fires a task reminder. Wave 0 goes out before the task's time, once for each reminder time
 * picked in Settings (e.g. 10 and 5 minutes before); wave 1 is a nudge 30 minutes after the
 * task's time if it still isn't done. Repeating tasks reschedule themselves for their next day
 * after the nudge. Shopping items have deadlines instead (see [ShoppingDeadlineWorker]).
 */
@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepo: TaskRepository,
    private val userRepo: UserRepository,
    private val trackerRepo: TrackerRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val wave = inputData.getInt(KEY_WAVE, 0)
        val task = taskRepo.getTaskById(taskId) ?: return Result.success()

        val doneToday = TaskTiming.completedOn(task, LocalDate.now()) || (!task.isRecurring && task.isCompleted)
        if (task.notifyEnabled && !doneToday) {
            notify(applicationContext, task, wave, describe(task))
        }

        if (wave == WAVE_NUDGE && task.isRecurring) {
            val (leads, deadlineLeads) = (userRepo.getUser() ?: com.habitgame.core.database.entity.UserEntity()).appReminderLeads(applicationContext)
            schedule(applicationContext, task, leads, deadlineLeads)
        }
        return Result.success()
    }

    private fun describe(task: TaskEntity): String {
        if (task.activityCategory == ActivityCategory.SAVINGS && task.savingAmount > 0) {
            return "Put aside ${formatAmount(task.savingAmount)} today."
        }
        return task.description.ifBlank { "Tap Done when it's finished to earn ${task.xpReward} XP." }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_WAVE = "wave"
        const val CHANNEL_TASKS = "habitgame_tasks"
        private const val WAVE_REMINDER = 0
        private const val WAVE_NUDGE = 1
        private const val NUDGE_AFTER_MINUTES = 30L

        fun notificationId(taskId: Long, wave: Int): Int = 100_000 + (taskId * 2 + wave).toInt()

        /**
         * Schedule (or replace) the next reminders and nudge for a task: one reminder for each of
         * [leads] (minutes before its time). Cancels them if it has no upcoming time. Shopping items
         * get their deadline reminders instead, [deadlineLeads] minutes before the deadline.
         */
        fun schedule(context: Context, task: TaskEntity, leads: List<Int>, deadlineLeads: List<Int>) {
            if (task.isShopping) {
                cancelTimeReminders(context, task.id)
                ShoppingDeadlineWorker.schedule(context, task, deadlineLeads)
                return
            }
            val wm = WorkManager.getInstance(context)
            val now = LocalDateTime.now()
            val next = if (task.notifyEnabled) TaskTiming.nextOccurrence(task, now) else null
            if (next == null) {
                cancelReminder(context, task.id)
                return
            }
            // Reminders whose time has already passed are skipped; if all have, one goes out now
            val upcoming = leads.map { next.minusMinutes(it.toLong()) }.filter { it.isAfter(now) }
            val remindAts = upcoming.ifEmpty { if (leads.isEmpty()) emptyList() else listOf(now) }
            fun enqueue(name: String, wave: Int, at: LocalDateTime) {
                val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                    .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_TASK_ID to task.id, KEY_WAVE to wave))
                    .addTag("task_reminder_${task.id}")
                    .build()
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            }
            for (i in 0 until ReminderTimes.MAX) {
                val at = remindAts.getOrNull(i)
                if (at != null) enqueue(reminderName(task.id, i), WAVE_REMINDER, at)
                else wm.cancelUniqueWork(reminderName(task.id, i))
            }
            wm.cancelUniqueWork(uniqueName(task.id, WAVE_REMINDER))  // the single reminder older versions scheduled
            enqueue(uniqueName(task.id, WAVE_NUDGE), WAVE_NUDGE, next.plusMinutes(NUDGE_AFTER_MINUTES))
        }

        /** Cancels everything scheduled for a task: reminders, the nudge and any shopping deadline. */
        fun cancelReminder(context: Context, taskId: Long) {
            cancelTimeReminders(context, taskId)
            ShoppingDeadlineWorker.cancel(context, taskId)
        }

        private fun cancelTimeReminders(context: Context, taskId: Long) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(uniqueName(taskId, WAVE_REMINDER))
            wm.cancelUniqueWork(uniqueName(taskId, WAVE_NUDGE))
            for (i in 0 until ReminderTimes.MAX) wm.cancelUniqueWork(reminderName(taskId, i))
        }

        private fun reminderName(taskId: Long, index: Int) = "reminder_${taskId}_$index"

        fun dismissNotifications(context: Context, taskId: Long) {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(notificationId(taskId, WAVE_REMINDER))
            nm.cancel(notificationId(taskId, WAVE_NUDGE))
            nm.cancel(ShoppingDeadlineWorker.notificationId(taskId, expired = false))
        }

        private fun uniqueName(taskId: Long, wave: Int) =
            if (wave == WAVE_REMINDER) "reminder_$taskId" else "nudge_$taskId"

        private fun notify(context: Context, task: TaskEntity, wave: Int, text: String) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val timeLabel = TaskTiming.timeOfDay(task)?.let { TaskTiming.formatTime(it) }
            val title = if (wave == WAVE_REMINDER) {
                "⏰ ${task.name}" + (timeLabel?.let { " · $it" } ?: "")
            } else {
                "🔔 Still to do: ${task.name}"
            }

            val openApp = PendingIntent.getActivity(
                context, task.id.toInt(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val markDone = PendingIntent.getBroadcast(
                context, task.id.toInt(),
                Intent(context, TaskActionReceiver::class.java)
                    .setAction(TaskActionReceiver.ACTION_COMPLETE)
                    .putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
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
            NotificationManagerCompat.from(context).notify(notificationId(task.id, wave), notification)
        }

        fun formatAmount(amount: Double): String =
            if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format("%.2f", amount)
    }
}
