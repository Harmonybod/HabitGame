package com.habitgame.domain.restriction

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.habitgame.core.database.repository.AppLockRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsageInfo(val packageName: String, val usedMinutes: Long)

/** An app that can be locked, as shown in the app picker. */
data class LockableApp(val packageName: String, val name: String)

@Singleton
class AppRestrictionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLockRepo: AppLockRepository
) {
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasAllPermissions(): Boolean = hasUsageStatsPermission() && hasOverlayPermission()

    fun openUsageStatsSettings() {
        // Some phones support jumping straight to this app's entry; fall back to the list
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(direct) } catch (_: Exception) { context.startActivity(list) }
    }

    fun openOverlaySettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun getTodayUsage(targetPackage: String): Long {
        if (!hasUsageStatsPermission()) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L
        val stats = usm.queryAndAggregateUsageStats(start, now)
        return (stats[targetPackage]?.totalTimeInForeground ?: 0L) / 60_000L // minutes
    }

    fun startMonitorService() {
        try {
            context.startForegroundService(Intent(context, UsageMonitorService::class.java))
        } catch (_: Exception) {
            // Android can refuse to start it from the background; it starts next time the app opens
        }
    }

    fun stopMonitorService() {
        context.stopService(Intent(context, UsageMonitorService::class.java))
    }

    /** Starts the lock service when there are apps to lock and the permissions it needs are granted. */
    suspend fun startMonitorIfNeeded() {
        if (hasAllPermissions() && appLockRepo.getAll().isNotEmpty()) startMonitorService()
    }

    /**
     * Every app that appears in the phone's app drawer, including preinstalled ones like
     * YouTube or Chrome, except HabitGame itself.
     */
    fun getInstalledApps(): List<LockableApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { LockableApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Checks if any timed unlocks have expired and should be re-locked.
     */
    fun isUnlockExpired(unlockedAt: Long?, durationMinutes: Int): Boolean {
        if (unlockedAt == null || durationMinutes <= 0) return false
        val expiresAt = unlockedAt + durationMinutes * 60 * 1000L
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * Returns remaining unlock minutes, or 0 if expired.
     */
    fun remainingUnlockMinutes(unlockedAt: Long?, durationMinutes: Int): Int {
        if (unlockedAt == null || durationMinutes <= 0) return 0
        val expiresAt = unlockedAt + durationMinutes * 60 * 1000L
        val remaining = (expiresAt - System.currentTimeMillis()) / 60_000L
        return remaining.coerceAtLeast(0).toInt()
    }
}
