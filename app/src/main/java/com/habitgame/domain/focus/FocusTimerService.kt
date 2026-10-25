package com.habitgame.domain.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.habitgame.HabitGameApp
import com.habitgame.MainActivity
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.restriction.CountdownBubble
import com.habitgame.ui.theme.ringColorArgb
import com.habitgame.widget.WidgetLaunch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a focus session going while HabitGame is closed: moves the timer on every second,
 * shows the time left in a notification (with Pause and Stop), shows the ring bubble over other
 * apps, and plays a sound when a focus block or a break ends. Stops itself when the session ends.
 */
@AndroidEntryPoint
class FocusTimerService : Service() {

    @Inject lateinit var timer: FocusTimer
    @Inject lateinit var userRepo: UserRepository

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loop: Job? = null
    private val bubble by lazy { CountdownBubble(applicationContext, topDp = 260f, openTarget = WidgetLaunch.FOCUS) }
    private var ringColor = android.graphics.Color.parseColor("#F5C02E")
    private var shownKey = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> timer.pause()
            ACTION_RESUME -> timer.resume()
            ACTION_STOP -> timer.stop()
        }
        // Android requires startForeground() after every start, even when the session has just ended
        val run = timer.state.value.run
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(run ?: FocusRun(FocusPlan())),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (run == null) {
            finish()
            return START_NOT_STICKY
        }
        shownKey = keyOf(run)
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        userRepo.getUser()?.let { ringColor = ringColorArgb(it.selectedTheme, it.accentColorHex) }
        while (scope.isActive) {
            val events = timer.tick()
            events.forEach(::announce)
            val run = timer.state.value.run
            if (run == null) {
                finish()
                return
            }
            // The notification only changes when the phase or pause state does (the countdown runs by itself)
            val key = keyOf(run)
            if (key != shownKey) {
                shownKey = key
                NotificationManagerCompat.from(this).runCatching { notify(NOTIFICATION_ID, buildNotification(run)) }
            }
            updateBubble(run)
            delay(1_000)
        }
    }

    /** The ring bubble, only while another app is on screen. */
    private fun updateBubble(run: FocusRun) {
        if (HabitGameApp.isAppVisible) {
            bubble.hide()
            return
        }
        val now = System.currentTimeMillis()
        val label = when {
            run.paused -> "⏸ " + FocusRun.shortLeft(run.left(now))
            run.phase == FocusPhase.BREAK -> "☕ " + FocusRun.shortLeft(run.left(now))
            else -> FocusRun.shortLeft(run.left(now))
        }
        bubble.show(run.fractionLeft(now), label, ringColor)
    }

    private fun finish() {
        bubble.hide()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        bubble.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun keyOf(run: FocusRun) = "${run.round}-${run.phase}-${run.paused}"

    // ─── Notifications ──────────────────────────────────────────────────────────

    private fun buildNotification(run: FocusRun): Notification {
        ensureChannels(this)
        val now = System.currentTimeMillis()
        val title = when (run.phase) {
            FocusPhase.FOCUS -> "🎯 Focus" + roundText(run)
            FocusPhase.BREAK -> "☕ Break" + roundText(run)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openFocus(this))
        if (run.paused) {
            builder.setContentText("Paused · ${FocusRun.clock(run.left(now))} left")
                .addAction(0, "Resume", serviceAction(ACTION_RESUME))
        } else {
            // The system counts down by itself, so the notification doesn't need updating every second
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(run.endsAt ?: now)
                .setShowWhen(true)
                .setContentText(if (run.phase == FocusPhase.FOCUS) "Stay with it. Rewards come when the time is up." else "Rest. The next focus block starts by itself.")
                .addAction(0, "Pause", serviceAction(ACTION_PAUSE))
        }
        builder.addAction(0, "Stop", serviceAction(ACTION_STOP))
        return builder.build()
    }

    private fun roundText(run: FocusRun) = if (run.plan.rounds > 1) " · round ${run.round} of ${run.plan.rounds}" else ""

    /** Sound and a heads-up note when a focus block or a break ends. */
    private fun announce(event: FocusEvent) {
        val (title, text) = when (event) {
            is FocusEvent.FocusDone ->
                if (event.lastRound) "🎉 Focus session complete!" to "${event.minutes} min done. Your XP and coins are added."
                else "☕ Time for a break" to "Round ${event.round} done (${event.minutes} min), and rewarded. Your break has started."
            is FocusEvent.BreakDone -> "🎯 Break's over" to "Round ${event.round + 1} has started. Back to it!"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(openFocus(this))
            .build()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(DONE_NOTIFICATION_ID, notification)
        }
    }

    private fun serviceAction(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, FocusTimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val NOTIFICATION_ID = 7_001
        private const val DONE_NOTIFICATION_ID = 7_002
        private const val ACTION_PAUSE = "com.habitgame.focus.PAUSE"
        private const val ACTION_RESUME = "com.habitgame.focus.RESUME"
        private const val ACTION_STOP = "com.habitgame.focus.STOP"
        const val CHANNEL_TIMER = "habitgame_focus_timer"
        const val CHANNEL_DONE = "habitgame_focus_done"

        /** Starts the service (or tells it something changed). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FocusTimerService::class.java)) }
        }

        private fun openFocus(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 7_000,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(WidgetLaunch.EXTRA, WidgetLaunch.FOCUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_TIMER) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_TIMER, "Focus timer", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "The time left in a running focus session"; setShowBadge(false) }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_DONE, "Focus finished", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "A sound when a focus block or a break ends"
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
                        )
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 300, 200, 300)
                    }
                )
            }
        }
    }
}
