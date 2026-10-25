package com.habitgame.domain.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** A note shared into HabitGame from another app, waiting for the Notes tab to save it. */
data class IncomingNote(val title: String, val body: String, val source: String)

/**
 * Notes shared in with "Save to notes" from Samsung Notes (Share → Text file, or as text)
 * or any other app. Samsung Notes has no way for other apps to read its notes directly,
 * so sharing is how they come across.
 */
object SharedNotes {
    val pending = MutableStateFlow<IncomingNote?>(null)

    private const val MAX_CHARS = 200_000

    /** Reads the shared text and any shared text files. Null when there's nothing to save. */
    fun read(context: Context, intent: Intent, fromPackage: String?): IncomingNote? {
        val parts = mutableListOf<String>()
        var fileName: String? = null
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(parts::add)
        streams(intent).forEach { uri ->
            if (fileName == null) fileName = displayName(context, uri)
            readText(context, uri)?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        val body = parts.joinToString("\n\n").trim().take(MAX_CHARS)
        if (body.isEmpty()) return null

        val title = (intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE) ?: fileName?.substringBeforeLast('.'))
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: titleFromBody(body)
        return IncomingNote(title.take(120), withoutRepeatedTitle(body, title), sourceName(context, fromPackage))
    }

    /** The first line, shortened, when the share didn't name the note. */
    fun titleFromBody(body: String): String = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(60)

    /** Text files often start with the note's title again; drop that line so it isn't shown twice. */
    fun withoutRepeatedTitle(body: String, title: String): String {
        val lines = body.lines()
        val first = lines.indexOfFirst { it.isNotBlank() }
        return if (first >= 0 && lines[first].trim() == title.trim() && lines.size > first + 1) lines.drop(first + 1).joinToString("\n").trim() else body
    }

    private fun streams(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    private fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read)
            }
        }
    }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** "Samsung Notes", or the sharing app's name. */
    private fun sourceName(context: Context, pkg: String?): String {
        if (pkg.isNullOrBlank() || pkg == context.packageName) return "Shared"
        if (pkg == SAMSUNG_NOTES) return "Samsung Notes"
        return runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("Shared")
    }

    const val SAMSUNG_NOTES = "com.samsung.android.app.notes"
}
