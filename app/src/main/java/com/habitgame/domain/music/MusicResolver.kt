package com.habitgame.domain.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up a song or album link's title, artist and cover using what each service
 * publishes for link previews; no accounts or keys needed. Covers are kept on the
 * phone so they show offline.
 */
@Singleton
class MusicResolver @Inject constructor(@ApplicationContext private val context: Context) {

    class NotMusicException : Exception("That link isn't a song or an album. Share a song or album instead.")

    /** Follows short links, works out song/album, then fills in the details. */
    suspend fun resolve(link: String): MusicInfo = withContext(Dispatchers.IO) {
        val url = if (MusicLinks.isShortLink(link)) finalUrl(link) else link
        val platform = MusicLinks.platformOf(url)
        val kind = MusicLinks.kindOf(url) ?: throw NotMusicException()
        val base = MusicInfo(url = url, platform = platform, kind = kind)
        when (platform) {
            MusicPlatform.SPOTIFY -> {
                // Spotify sends phone browsers to its app, so ask the way link previews do straight away
                val (title, artist, cover) = MusicLinks.fromSpotifyPage(MusicLinks.ogTags(get(url, PREVIEW_AGENT)))
                base.copy(title = title, artist = artist, coverUrl = cover)
            }
            MusicPlatform.APPLE_MUSIC -> {
                val tags = MusicLinks.ogTags(get(url))
                val (title, artist, pageKind) = MusicLinks.fromApplePage(tags)
                base.copy(kind = pageKind ?: kind, title = title, artist = artist, coverUrl = tags["og:image"]?.let(::squareAppleCover))
            }
            MusicPlatform.YOUTUBE, MusicPlatform.YOUTUBE_MUSIC -> {
                val json = runCatching { json(get(MusicLinks.youtubeOEmbedUrl(url))) }.getOrNull()
                if (json != null) {
                    val (title, artist) = MusicLinks.fromYouTube(json.str("title").orEmpty(), json.str("author_name"))
                    base.copy(title = title, artist = artist, coverUrl = json.str("thumbnail_url"))
                } else {
                    fromPage(base)  // e.g. album pages the look-up doesn't cover
                }
            }
            MusicPlatform.DEEZER -> {
                val (type, id) = MusicLinks.deezerId(url) ?: throw NotMusicException()
                val json = json(get("https://api.deezer.com/$type/$id"))
                val cover = if (type == "album") json.str("cover_xl") else json.obj("album")?.str("cover_xl")
                base.copy(title = json.str("title"), artist = json.obj("artist")?.str("name"), coverUrl = cover)
            }
            MusicPlatform.SOUNDCLOUD -> {
                val json = json(get("https://soundcloud.com/oembed?format=json&url=" + java.net.URLEncoder.encode(url, "UTF-8")))
                val (title, artist) = MusicLinks.fromSoundCloud(json.str("title").orEmpty(), json.str("author_name"))
                base.copy(title = title, artist = artist, coverUrl = json.str("thumbnail_url"))
            }
            else -> fromPage(base)
        }.also { info ->
            // Save the cover now, so it's there offline
            info.coverUrl?.let { runCatching { coverFile(it) } }
        }
    }

    private fun fromPage(base: MusicInfo): MusicInfo {
        val tags = MusicLinks.ogTags(get(base.url))
        val (title, artist) = MusicLinks.fromGenericPage(tags)
        return base.copy(title = title, artist = artist, coverUrl = tags["og:image"])
    }

    /** The cover as a picture, from the phone if already saved, otherwise downloaded and saved. */
    suspend fun cover(coverUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { coverFile(coverUrl)?.let { BitmapFactory.decodeFile(it.absolutePath) } }.getOrNull()
    }

    private fun coverFile(coverUrl: String): File? {
        val dir = File(context.filesDir, "music_covers").apply { mkdirs() }
        val file = File(dir, sha1(coverUrl) + ".jpg")
        if (file.exists()) return file
        val bytes = download(coverUrl) ?: return null
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = 320f / maxOf(original.width, original.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        FileOutputStream(file).use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    /** Apple's preview image is a wide banner; ask for the square album art instead. */
    private fun squareAppleCover(url: String) = url.replace(Regex("""/\d+x\d+[a-z]*(-\d+)?\.(jpg|png|webp)$"""), "/600x600bb.jpg")

    // ─── HTTP ───────────────────────────────────────────────────────────────────

    private fun open(url: String, userAgent: String = USER_AGENT): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept-Language", "en")
        }

    /** Fetches a page (first 400 KB is plenty for the details at the top). */
    private fun get(url: String, userAgent: String = USER_AGENT): String {
        var current = url
        repeat(6) {
            val conn = open(current, userAgent)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: error("Redirect without a target")
                conn.disconnect()
                // Some sites send phones to their app ("intent://…") instead of the page;
                // ask again the way chat apps fetch link previews, which gets the page
                if (!location.startsWith("http") && !location.startsWith("/")) {
                    if (userAgent != PREVIEW_AGENT) return get(url, PREVIEW_AGENT)
                    error("Redirected to an app link")
                }
                // HttpURLConnection won't follow http↔https hops by itself
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) error("HTTP $code")
            return conn.inputStream.use { stream ->
                val buffer = ByteArray(400 * 1024)
                var read = 0
                while (read < buffer.size) {
                    val n = stream.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                String(buffer, 0, read, Charsets.UTF_8)
            }
        }
        error("Too many redirects")
    }

    /** Where a short share link ends up. */
    private fun finalUrl(url: String): String {
        val spotifyPage = Regex("""https://open\.spotify\.com/(?:intl-[a-z]+/)?(?:track|album)/[A-Za-z0-9]+""")
        var current = url
        repeat(6) {
            // The real address is often tucked inside the redirect (e.g. spotify.app.link/?$full_url=…)
            spotifyPage.find(java.net.URLDecoder.decode(current, "UTF-8"))?.let { if (current != url || "open.spotify.com" in url) return it.value }
            val conn = open(current).apply { instanceFollowRedirects = false }
            val location = conn.getHeaderField("Location")
            if (conn.responseCode in 300..399 && location != null) {
                conn.disconnect()
                // A hop to an app ("intent://…"): take the web address out of it if there is one
                if (!location.startsWith("http") && !location.startsWith("/")) {
                    return spotifyPage.find(java.net.URLDecoder.decode(location, "UTF-8"))?.value ?: current
                }
                current = URL(URL(current), location).toString()
            } else {
                // Some short links land on a page that points onward instead of redirecting
                val body = runCatching { conn.inputStream.use { it.readBytes().decodeToString() } }.getOrDefault("")
                return spotifyPage.find(body)?.value ?: current
            }
        }
        return current
    }

    private fun download(url: String): ByteArray? = runCatching {
        val conn = open(url)
        if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
    }.getOrNull()

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun sha1(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        // A phone browser: the music sites send their full link-preview details to it
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        // How link-preview fetchers identify themselves; sites answer with the page's preview details
        private const val PREVIEW_AGENT = "facebookexternalhit/1.1"
    }
}

/** A song or album shared into HabitGame from another app, waiting for the Journal to pick it up. */
object SharedMusic {
    val pending = MutableStateFlow<String?>(null)
}
