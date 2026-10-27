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
import com.habitgame.domain.engine.ActiveSession
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.domain.engine.EconomyManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Ends a timed Shop purchase when its time is up, even if the app is closed. */
@HiltWorker
class SessionEndWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val boosterManager: BoosterManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val warnId = inputData.getLong(KEY_WARN_ITEM, -1L)
        if (warnId >= 0) {
            boosterManager.activeSessions().firstOrNull { it.item.id == warnId }
                ?.takeIf { BoosterManager.canExtend(it) && it.remainingMillis() <= WARN_BEFORE_MILLIS + 60_000L }
                ?.let { notifyEnding(applicationContext, it) }
            return Result.success()
        }
        val ended = boosterManager.endFinishedSessions()
        notifyEnded(applicationContext, ended)
        // WorkManager can run a little early; re-queue anything still counting down
        boosterManager.activeSessions().forEach { schedule(applicationContext, it) }
        return Result.success()
    }

    companion object {
        const val CHANNEL_SESSIONS = "habitgame_sessions"
        private const val KEY_WARN_ITEM = "warn_item"
        private const val WARN_BEFORE_MILLIS = 30 * 60_000L

        /** Clock-based sessions only; App Unlock time is spent by the lock service while apps are open. */
        fun schedule(context: Context, session: ActiveSession) {
            if (session.countsUsage) return
            val delay = session.remainingMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<SessionEndWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniqueWork("session_end_${session.item.id}", ExistingWorkPolicy.REPLACE, request)
            // No Restriction Day: offer 5 more hours half an hour before it ends
            if (session.item.boosterEffect == BoosterManager.NO_RESTRICTION && delay > WARN_BEFORE_MILLIS) {
                val warn = OneTimeWorkRequestBuilder<SessionEndWorker>()
                    .setInitialDelay(delay - WARN_BEFORE_MILLIS, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_WARN_ITEM to session.item.id))
                    .build()
                wm.enqueueUniqueWork("session_warn_${session.item.id}", ExistingWorkPolicy.REPLACE, warn)
            }
        }

        /**
         * Buys 5 more hours of a running No Restriction Day (50 coins) and moves its end.
         * Returns what to tell the user.
         */
        suspend fun extend(context: Context, economyManager: EconomyManager, boosterManager: BoosterManager, session: ActiveSession): String {
            if (!economyManager.extendSession(session)) {
                return if (session.isFinished()) "${session.item.name} has already ended."
                else "You need ${BoosterManager.EXTEND_COST} coins to add 5 hours."
            }
            boosterManager.activeSessions().filter { it.item.id == session.item.id }.forEach { schedule(context, it) }
            NotificationManagerCompat.from(context).cancel(warnNotificationId(session.item.id))
            return "Added 5 hours to ${session.item.name} 🔓"
        }

        private fun warnNotificationId(itemId: Long) = 710_000 + itemId.toInt()

        private fun notifyEnding(context: Context, session: ActiveSession) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val openApp = PendingIntent.getActivity(
                context, 78,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val extend = PendingIntent.getBroadcast(
                context, warnNotificationId(session.item.id),
                Intent(context, TaskActionReceiver::class.java)
                    .setAction(TaskActionReceiver.ACTION_EXTEND_SESSION)
                    .putExtra(TaskActionReceiver.EXTRA_ITEM_ID, session.item.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val minutes = (session.remainingMillis() / 60_000L).coerceAtLeast(1)
            val text = "Your locked apps lock again in $minutes min. Keep them open 5 more hours for ${BoosterManager.EXTEND_COST} coins?"
            val notification = NotificationCompat.Builder(context, CHANNEL_SESSIONS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("${session.item.iconEmoji} ${session.item.name} ends soon")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .addAction(android.R.drawable.ic_input_add, "+5 h · ${BoosterManager.EXTEND_COST} coins", extend)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(warnNotificationId(session.item.id), notification)
        }

        /** "Your app unlock has ended" style notifications for sessions that just finished. */
        fun notifyEnded(context: Context, ended: List<ActiveSession>) {
            if (ended.isEmpty()) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            val openApp = PendingIntent.getActivity(
                context, 77,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            ended.forEach { session ->
                val text = if (session.unlocksApps) "Time's up. Your locked apps are locked again."
                else "${session.item.name} has ended."
                val notification = NotificationCompat.Builder(context, CHANNEL_SESSIONS)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("${session.item.iconEmoji} ${session.item.name} finished")
                    .setContentText(text)
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(700_000 + session.item.id.toInt(), notification)
            }
        }
    }
}
