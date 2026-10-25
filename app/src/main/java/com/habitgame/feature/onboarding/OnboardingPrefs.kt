package com.habitgame.feature.onboarding

import android.content.Context

/** Remembers whether the first-launch guide has been shown. */
object OnboardingPrefs {
    private const val FILE = "habitgame_prefs"
    private const val KEY_DONE = "onboarding_done"

    fun isDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DONE)) return prefs.getBoolean(KEY_DONE, false)
        // People updating from an older version already know the app; only fresh installs see the guide
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val isFreshInstall = info.firstInstallTime == info.lastUpdateTime
        if (!isFreshInstall) setDone(context)
        return !isFreshInstall
    }

    fun setDone(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}
