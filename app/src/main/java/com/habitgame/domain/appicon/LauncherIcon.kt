package com.habitgame.domain.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.habitgame.R

/**
 * The app icons to choose from. Each is an activity-alias in the manifest with its own icon, and the
 * chosen one is the only one enabled, so the app drawer and home screen show that icon.
 */
enum class LauncherIcon(private val alias: String, val title: String, @DrawableRes val art: Int) {
    WORDMARK(".LauncherWordmark", "Full wordmark", R.drawable.ic_launcher_wordmark_fg),
    MARK(".LauncherMark", "Mark", R.drawable.ic_launcher_mark_fg);

    private fun component(context: Context) = ComponentName(context.packageName, "com.habitgame$alias")

    companion object {
        /** The icon in use: one turned on in Settings, otherwise the default wordmark. */
        fun current(context: Context): LauncherIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: WORDMARK
        }

        fun use(context: Context, icon: LauncherIcon) {
            val pm = context.packageManager
            // The new one goes on before the old one goes off, so the app never drops out of the drawer
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
