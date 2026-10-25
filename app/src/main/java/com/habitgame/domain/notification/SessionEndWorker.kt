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
        val ended = boosterManager.endFinishedSessions()
        notifyEnded(applicationContext, ended)
        // WorkManager can run a little early; re-queue anything still counting down
        boosterManager.activeSessions().forEach { schedule(applicationContext, it) }
        return Result.success()
    }

    companion object {
        const val CHANNEL_SESSIONS = "habitgame_sessions"

        /** Clock-based sessions only; App Unlock time is spent by the lock service while apps are open. */
        fun schedule(context: Context, session: ActiveSession) {
            if (session.countsUsage) return
            val delay = session.remainingMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<SessionEndWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("session_end_${session.item.id}", ExistingWorkPolicy.REPLACE, request)
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
