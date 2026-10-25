package com.habitgame.domain.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitgame.domain.tracker.TrackerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var trackerManager: TrackerManager
    @Inject lateinit var appRestrictionManager: com.habitgame.domain.restriction.AppRestrictionManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // WorkManager keeps queued work across reboots, but recompute reminders in case the clock or day changed
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                trackerManager.runDailyMaintenance()
                // Turn the app lock back on after a restart
                appRestrictionManager.startMonitorIfNeeded()
            } finally {
                pending.finish()
            }
        }
    }
}
