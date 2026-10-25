package com.habitgame.domain.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A newer HabitGame published on GitHub. */
data class AppRelease(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease) : UpdateState
    data class Failed(val release: AppRelease?, val message: String) : UpdateState
}

/**
 * Keeps HabitGame up to date from its GitHub Releases: checks for a newer version, downloads the
 * APK and opens Android's install screen (one tap; Android always asks for apps not from the Play
 * Store). Updates install over the app, so nothing is lost: they're signed with the same key.
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    val installedVersion: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Looks for a newer release, at most every few hours unless [force]. Returns it, or null if up to date. */
    suspend fun check(force: Boolean = false): AppRelease? {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_CHECKED, 0) < CHECK_EVERY) {
            // Checked recently: show what was found then (kept across app restarts)
            val known = savedRelease()?.takeIf { isNewer(it.version, installedVersion) }
            if (known != null && _state.value == UpdateState.None) _state.value = UpdateState.Available(known)
            return known
        }
        val result = withContext(Dispatchers.IO) { runCatching { latestRelease() } }
        if (result.isFailure) {
            if (force) _state.value = UpdateState.Failed(null, "Couldn't reach GitHub. Check your internet and try again.")
            return null
        }
        prefs.edit().putLong(KEY_CHECKED, now).apply()
        val release = result.getOrNull()   // null: nothing published yet
        return if (release != null && isNewer(release.version, installedVersion)) {
            prefs.edit().putString(KEY_RELEASE, gson.toJson(release)).apply()
            if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Available(release)
            release
        } else {
            prefs.edit().remove(KEY_RELEASE).apply()
            _state.value = UpdateState.None
            null
        }
    }

    private val gson = com.google.gson.Gson()

    private fun savedRelease(): AppRelease? =
        runCatching { gson.fromJson(prefs.getString(KEY_RELEASE, null), AppRelease::class.java) }.getOrNull()

    /** "Later": hides this version's banner until the next one comes out. */
    fun snooze(release: AppRelease) {
        prefs.edit().putString(KEY_SNOOZED, release.version).apply()
        _state.value = UpdateState.None
    }

    fun isSnoozed(release: AppRelease) = prefs.getString(KEY_SNOOZED, null) == release.version

    fun unsnooze() = prefs.edit().remove(KEY_SNOOZED).apply()

    /** Downloads the release's APK, then opens the install screen. */
    suspend fun downloadAndInstall(release: AppRelease) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, "HabitGame-${release.version}.apk")
        dir.listFiles { f -> f.name.endsWith(".apk") && f != file }?.forEach { it.delete() }   // older downloads
        if (!file.exists() || file.length() == 0L) {
            _state.value = UpdateState.Downloading(release, 0f)
            val ok = download(release, file)
            if (!ok) {
                file.delete()
                _state.value = UpdateState.Failed(release, "The download didn't finish. Try again.")
                return
            }
        }
        _state.value = UpdateState.Ready(release)
        install(file)
    }

    private suspend fun download(release: AppRelease, file: File): Boolean {
        val dm = context.getSystemService(DownloadManager::class.java) ?: return false
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle("HabitGame ${release.version}")
                .setDescription("Downloading the update")
                .setMimeType(APK_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file))
        )
        while (true) {
            delay(500)
            val (status, done, total) = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (!c.moveToFirst()) return false
                Triple(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            } ?: return false
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> return true
                DownloadManager.STATUS_FAILED -> return false
                else -> if (total > 0) _state.value = UpdateState.Downloading(release, done.toFloat() / total)
            }
        }
    }

    /** Android's install screen, or first its "allow installs from HabitGame" setting. */
    fun install(file: File? = null) {
        val apk = file ?: (_state.value as? UpdateState.Ready)?.let { r ->
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "HabitGame-${r.release.version}.apk")
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The newest published release with an APK attached. */
    private fun latestRelease(): AppRelease? {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            when (connection.responseCode) {
                200 -> Unit
                404 -> return null   // no releases published yet: up to date
                else -> throw java.io.IOException("GitHub answered ${connection.responseCode}")
            }
            val json = JsonParser.parseString(connection.inputStream.bufferedReader().readText()).asJsonObject
            val apk = json.getAsJsonArray("assets")
                ?.map { it.asJsonObject }
                ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk") == true }
                ?.get("browser_download_url")?.asString ?: return null
            return AppRelease(
                version = json.get("tag_name")?.asString.orEmpty().removePrefix("v"),
                notes = json.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim(),
                apkUrl = apk,
                pageUrl = json.get("html_url")?.asString.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val REPO = "Harmonybod/HabitGame"
        private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        private const val KEY_CHECKED = "last_checked"
        private const val KEY_SNOOZED = "snoozed_version"
        private const val KEY_RELEASE = "found_release"
        private const val CHECK_EVERY = 3 * 60 * 60_000L

        /** "2.10" is newer than "2.9"; "2.4" isn't newer than "2.4". Non-numbers count as 0. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = installed.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
