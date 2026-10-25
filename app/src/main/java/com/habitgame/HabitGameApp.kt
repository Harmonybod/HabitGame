package com.habitgame

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.habitgame.domain.notification.DailyCheckInWorker
import com.habitgame.domain.notification.DailyResetWorker
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.notification.HabitReminderWorker
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HabitGameApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var trackerManager: TrackerManager
    @Inject lateinit var database: com.habitgame.core.database.AppDatabase
    @Inject lateinit var cloudSync: com.habitgame.domain.sync.CloudSync
    @Inject lateinit var calendarSync: com.habitgame.domain.calendar.CalendarSync

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Lets WorkManager build workers that need injected repositories
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        trackVisibleScreens()
        createNotificationChannels()
        com.habitgame.domain.focus.FocusTimerService.ensureChannels(this)
        val workManager = WorkManager.getInstance(this)
        DailyResetWorker.schedule(workManager)
        DailyCheckInWorker.schedule(workManager)
        appScope.launch { trackerManager.runDailyMaintenance() }
        watchForWidgetChanges()
        cloudSync.start(appScope)
        calendarSync.start(appScope)
    }

    /** Home-screen widgets redraw whenever the data they show changes, wherever it changed. */
    private fun watchForWidgetChanges() {
        com.habitgame.widget.WidgetUpdater.start(this, appScope)
        database.invalidationTracker.addObserver(object : androidx.room.InvalidationTracker.Observer(
            arrayOf("user", "tasks", "habits", "habit_completions", "savings_entries", "day_progress", "challenge")
        ) {
            override fun onInvalidated(tables: Set<String>) = com.habitgame.widget.WidgetUpdater.requestUpdate()
        })
    }

    /** Counts HabitGame screens on display, so the countdown bubble can hide while the app is open. */
    private fun trackVisibleScreens() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { visibleScreens++ }
            override fun onActivityStopped(activity: android.app.Activity) { visibleScreens = (visibleScreens - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(TaskReminderWorker.CHANNEL_TASKS, "Task Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reminders for your scheduled tasks" }
        )
        nm.createNotificationChannel(
            NotificationChannel(HabitReminderWorker.CHANNEL_HABITS, "Habit Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reminders for your daily habits" }
        )
        nm.createNotificationChannel(
            NotificationChannel(DailyCheckInWorker.CHANNEL_CHECK_IN, "Evening Check-in", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Your 100-day progress and journal reminder" }
        )
        nm.createNotificationChannel(
            NotificationChannel(com.habitgame.domain.notification.SessionEndWorker.CHANNEL_SESSIONS, "Shop Timers", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "When an app unlock, entertainment time or booster ends" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FOCUS, "Focus Sessions", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Focus session alerts and completions" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DAILY_RESET, "Daily Summary", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Daily reset and penalty summary" }
        )
    }

    companion object {
        @Volatile private var visibleScreens = 0

        /** True while any HabitGame screen is on display. */
        val isAppVisible: Boolean get() = visibleScreens > 0

        const val CHANNEL_FOCUS = "habitgame_focus"
        const val CHANNEL_DAILY_RESET = "habitgame_daily_reset"
    }
}
