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
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.domain.tracker.TaskTiming
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Shopping items and one-time tasks with a deadline: reminders before it (1 h, 15 min and 5 min by
 * default, set in Settings → Notifications), and at the deadline it's removed if it still isn't done.
 * (Named for shopping, where deadlines started; the name stays so already-scheduled work still runs.)
 */
@HiltWorker
class ShoppingDeadlineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepo: TaskRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val task = taskRepo.getTaskById(inputData.getLong(KEY_TASK_ID, -1L)) ?: return Result.success()
        val deadline = task.deadlineAt ?: return Result.success()
        if (task.isCompleted || task.isRecurring) return Result.success()
        val now = System.currentTimeMillis()

        if (inputData.getBoolean(KEY_EXPIRE, false)) {
            // WorkManager can run a little early; only remove once the deadline has really passed
            if (deadline - now > EARLY_TOLERANCE_MS) {
                enqueueExpiry(applicationContext, task.id, deadline)
                return Result.success()
            }
            taskRepo.deleteTask(task)
            TaskReminderWorker.dismissNotifications(applicationContext, task.id)
            notifyRemoved(applicationContext, task)
        } else if (deadline > now) {
            notifyDue(applicationContext, task, deadline - now)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_EXPIRE = "expire"
        private const val EARLY_TOLERANCE_MS = 30_000L

        fun notificationId(taskId: Long, expired: Boolean): Int = 300_000 + (taskId * 2 + if (expired) 1 else 0).toInt()

        /** Schedules (or replaces) the reminders and removal for [task]; cancels them once it's bought or has no deadline. */
        fun schedule(context: Context, task: TaskEntity, leads: List<Int>) {
            val deadline = task.deadlineAt
            if (task.isCompleted || task.isRecurring || deadline == null) {
                cancel(context, task.id)
                return
            }
            val wm = WorkManager.getInstance(context)
            val now = System.currentTimeMillis()
            val upcoming = leads.map { deadline - it * 60_000L }.filter { it > now }
            for (i in 0 until ReminderTimes.MAX) {
                val at = upcoming.getOrNull(i)
                if (at == null) {
                    wm.cancelUniqueWork(reminderName(task.id, i))
                    continue
                }
                val request = OneTimeWorkRequestBuilder<ShoppingDeadlineWorker>()
                    .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_TASK_ID to task.id))
                    .build()
                wm.enqueueUniqueWork(reminderName(task.id, i), ExistingWorkPolicy.REPLACE, request)
            }
            enqueueExpiry(context, task.id, deadline)
        }

        private fun enqueueExpiry(context: Context, taskId: Long, deadline: Long) {
            val request = OneTimeWorkRequestBuilder<ShoppingDeadlineWorker>()
                .setInitialDelay((deadline - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TASK_ID to taskId, KEY_EXPIRE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(expiryName(taskId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, taskId: Long) {
            val wm = WorkManager.getInstance(context)
            for (i in 0 until ReminderTimes.MAX) wm.cancelUniqueWork(reminderName(taskId, i))
            wm.cancelUniqueWork(expiryName(taskId))
        }

        private fun reminderName(taskId: Long, index: Int) = "shop_remind_${taskId}_$index"
        private fun expiryName(taskId: Long) = "shop_expire_$taskId"

        /** "1 h", "15 min", "1 h 5 min": rounded to the nearest minute. */
        fun timeLeft(millis: Long): String = ReminderTimes.label(((millis + 30_000) / 60_000).toInt().coerceAtLeast(1))

        private fun canNotify(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun openApp(context: Context, requestCode: Int) = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun notifyDue(context: Context, task: TaskEntity, left: Long) {
            if (!canNotify(context)) return
            val deadline = TaskTiming.formatTime(TaskTiming.localTimeOf(task.deadlineAt!!))
            val text = if (task.isShopping) {
                "Buy it by $deadline, or it's removed from your list. Tap Bought ✓ when you have it to earn ${task.xpReward} XP."
            } else {
                "Finish it by $deadline, or it's removed from your tasks. Tap Done ✓ when it's finished to earn ${task.xpReward} XP."
            }
            val bought = PendingIntent.getBroadcast(
                context, notificationId(task.id, false),
                Intent(context, TaskActionReceiver::class.java)
                    .setAction(TaskActionReceiver.ACTION_COMPLETE)
                    .putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_TASKS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("${if (task.isShopping) "🛒" else "⏳"} ${task.name} · ${timeLeft(left)} left")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openApp(context, notificationId(task.id, false)))
                .addAction(android.R.drawable.checkbox_on_background, if (task.isShopping) "Bought ✓" else "Done ✓", bought)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId(task.id, false), notification)
        }

        private fun notifyRemoved(context: Context, task: TaskEntity) {
            if (!canNotify(context)) return
            val notification = NotificationCompat.Builder(context, TaskReminderWorker.CHANNEL_TASKS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("⌛ ${task.name} was removed")
                .setContentText(if (task.isShopping) "Its deadline passed before it was bought." else "Its deadline passed before it was done.")
                .setContentIntent(openApp(context, notificationId(task.id, true)))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId(task.id, true), notification)
        }
    }
}
