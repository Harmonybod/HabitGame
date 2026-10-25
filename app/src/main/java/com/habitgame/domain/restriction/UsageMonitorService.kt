package com.habitgame.domain.restriction

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import com.habitgame.core.database.repository.AppLockRepository
import com.habitgame.domain.engine.BoosterManager
import com.habitgame.domain.notification.SessionEndWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Watches which app is on screen and covers locked apps with the block screen.
 * App Unlock / Entertainment time from the Shop is spent only while a locked app is
 * actually open (screen on), and a "No Restriction Day" turns all locks off for the day.
 */
@AndroidEntryPoint
class UsageMonitorService : Service() {

    @Inject lateinit var appLockRepo: AppLockRepository
    @Inject lateinit var appRestrictionManager: AppRestrictionManager
    @Inject lateinit var boosterManager: BoosterManager
    @Inject lateinit var userRepo: com.habitgame.core.database.repository.UserRepository

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private val bubble by lazy { CountdownBubble(applicationContext) }

    // The app most recently brought to the front, read from the system's usage events
    private var foregroundPackage: String? = null
    private var lastEventTime = System.currentTimeMillis() - 60_000L
    private var lastTick = System.currentTimeMillis()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        if (monitorJob?.isActive != true) startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob = scope.launch {
            while (isActive) {
                val apps = appLockRepo.getAll()
                if (apps.isEmpty()) {
                    // Nothing to lock any more
                    bubble.hide()
                    stopSelf()
                    return@launch
                }

                // Shop timers that just ran out: lock apps again / end boosters, and say so
                SessionEndWorker.notifyEnded(this@UsageMonitorService, boosterManager.endFinishedSessions())

                // Re-lock apps whose older per-app unlock time has run out
                apps.filter { it.isUnlocked && appRestrictionManager.isUnlockExpired(it.unlockedAt, it.unlockDurationMinutes) }
                    .forEach { appLockRepo.lockApp(it.packageName) }

                if (!appRestrictionManager.hasUsageStatsPermission()) {
                    bubble.hide()
                    delay(5_000)
                    continue
                }
                val foreground = currentForegroundApp()
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceIn(0L, 5_000L)  // capped so a stalled loop can't eat minutes
                lastTick = now

                // Is the person using a locked app right now?
                val inLockedApp = foreground != null &&
                    apps.any { it.packageName == foreground } &&
                    !com.habitgame.HabitGameApp.isAppVisible &&
                    getSystemService(PowerManager::class.java).isInteractive

                val unlocks = boosterManager.activeSessions().filter { it.unlocksApps }
                if (unlocks.isNotEmpty()) {
                    // Apps stay open. A No Restriction Day runs on the clock; bought unlock time
                    // only goes down while a locked app is open. The bubble shows while it's being spent.
                    val dayPass = unlocks.firstOrNull { !it.countsUsage }
                    val timed = unlocks.filter { it.countsUsage }
                    if (dayPass == null && inLockedApp) boosterManager.spendUnlockTime(elapsed)
                    if (inLockedApp) {
                        // The ring matches the accent colour picked in Settings
                        val ring = userRepo.getUser().let { com.habitgame.ui.theme.ringColorArgb(it?.selectedTheme ?: "default", it?.accentColorHex.orEmpty()) }
                        if (dayPass != null) {
                            bubble.show(dayPass.fractionLeft(now), minutesLabel(dayPass.remainingMillis(now)), ring)
                        } else {
                            val left = (timed.sumOf { it.remainingMillis() } - elapsed).coerceAtLeast(0L)
                            bubble.show(left.toFloat() / timed.sumOf { it.totalMillis }.coerceAtLeast(1L), minutesLabel(left), ring)
                        }
                    } else {
                        bubble.hide()
                    }
                    delay(1_000)
                    continue
                }
                bubble.hide()

                val blocked = apps
                    .filter { !it.isUnlocked || appRestrictionManager.isUnlockExpired(it.unlockedAt, it.unlockDurationMinutes) }
                    .map { it.packageName }
                    .toSet()

                if (blocked.isEmpty()) {
                    delay(5_000)
                    continue
                }

                if (foreground != null && foreground in blocked) {
                    startActivity(
                        Intent(this@UsageMonitorService, BlockOverlayActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("BLOCKED_PACKAGE", foreground)
                        }
                    )
                    // The block screen is now in front; wait for the next time the app is opened
                    foregroundPackage = packageName
                }

                delay(1_000)
            }
        }
    }

    /** "1h 5m" or "12m" — rounded up, so it never says 0m while time is left. */
    private fun minutesLabel(millis: Long): String {
        val minutes = ((millis + 59_999) / 60_000).toInt()
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    /** The app currently on screen, based on the latest "moved to foreground" event. */
    private fun currentForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventTime, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundPackage = event.packageName
            }
            if (event.timeStamp > lastEventTime) lastEventTime = event.timeStamp
        }
        return foregroundPackage
    }

    private fun buildForegroundNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App Lock", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("HabitGame app lock is on")
            .setContentText("Your distracting apps are locked 🎯")
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubble.hide()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "usage_monitor"
    }
}
