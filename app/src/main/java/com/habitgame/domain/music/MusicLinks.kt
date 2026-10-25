package com.habitgame.domain.music

import java.net.URI

/** Where a song or album link is from. [color] is the service's brand colour, shown as a dot. */
enum class MusicPlatform(val displayName: String, val color: Long) {
    SPOTIFY("Spotify", 0xFF1DB954),
    YOUTUBE_MUSIC("YouTube Music", 0xFFFF0033),
    YOUTUBE("YouTube", 0xFFFF0033),
    APPLE_MUSIC("Apple Music", 0xFFFA243C),
    DEEZER("Deezer", 0xFFA238FF),
    SOUNDCLOUD("SoundCloud", 0xFFFF5500),
    TIDAL("Tidal", 0xFF33FFEE),
    AMAZON_MUSIC("Amazon Music", 0xFF25D1DA),
    OTHER("Music", 0xFF9E9E9E)
}

enum class MusicKind(val label: String) { SONG("Song"), ALBUM("Album") }

/** The song or album saved on a journal day, if any. */
val com.habitgame.core.database.entity.JournalEntryEntity.music: MusicInfo?
    get() = musicUrl?.let { url ->
        MusicInfo(
            url = url,
            platform = runCatching { MusicPlatform.valueOf(musicPlatform.orEmpty()) }.getOrDefault(MusicLinks.platformOf(url)),
            kind = runCatching { MusicKind.valueOf(musicKind.orEmpty()) }.getOrDefault(MusicKind.SONG),
            title = musicTitle,
            artist = musicArtist,
            coverUrl = musicCoverUrl
        )
    }

/** A journal day's song or album. [title] is null until its details have been looked up. */
data class MusicInfo(
    val url: String,
    val platform: MusicPlatform,
    val kind: MusicKind,
    val title: String? = null,
    val artist: String? = null,
    val coverUrl: String? = null
)

/**
 * Recognises song and album links from music services and reads their details out of
 * the pages and look-ups those services publish. Pure text handling, no network.
 */
object MusicLinks {

    private val urlPattern = Regex("""https?://[^\s<>"']+""")

    /** The first link in shared text, e.g. "Check this out https://open.spotify.com/track/…". */
    fun findLink(text: String): String? = urlPattern.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

    fun platformOf(url: String): MusicPlatform {
        val host = host(url)
        return when {
            host.endsWith("spotify.com") || host.endsWith("spotify.link") || host == "spotify.app.link" -> MusicPlatform.SPOTIFY
            host == "music.youtube.com" -> MusicPlatform.YOUTUBE_MUSIC
            host.endsWith("youtube.com") || host == "youtu.be" -> MusicPlatform.YOUTUBE
            host == "music.apple.com" || host == "itunes.apple.com" -> MusicPlatform.APPLE_MUSIC
            host.endsWith("deezer.com") || host == "deezer.page.link" -> MusicPlatform.DEEZER
            host.endsWith("soundcloud.com") || host == "snd.sc" -> MusicPlatform.SOUNDCLOUD
            host.endsWith("tidal.com") -> MusicPlatform.TIDAL
            host.startsWith("music.amazon.") || host.endsWith("amazon.com") && "/music" in url -> MusicPlatform.AMAZON_MUSIC
            else -> MusicPlatform.OTHER
        }
    }

    /** Song or album, from the link's shape. Null for things that aren't either (artists, playlists, profiles). */
    fun kindOf(url: String): MusicKind? {
        val path = path(url)
        val query = query(url)
        return when (platformOf(url)) {
            MusicPlatform.SPOTIFY -> when {
                "/track/" in path -> MusicKind.SONG
                "/album/" in path -> MusicKind.ALBUM
                host(url).endsWith("spotify.link") || host(url) == "spotify.app.link" -> MusicKind.SONG  // short link: settled after following it
                else -> null
            }
            MusicPlatform.YOUTUBE_MUSIC, MusicPlatform.YOUTUBE -> when {
                "list=OLAK5uy_" in query || path.startsWith("/browse/MPREb") -> MusicKind.ALBUM
                path.startsWith("/watch") || path.startsWith("/shorts/") || host(url) == "youtu.be" -> MusicKind.SONG
                else -> null
            }
            MusicPlatform.APPLE_MUSIC -> when {
                "/song/" in path || ("/album/" in path && Regex("""(^|&)i=\d+""").containsMatchIn(query)) -> MusicKind.SONG
                "/album/" in path -> MusicKind.ALBUM
                else -> null
            }
            MusicPlatform.DEEZER -> when {
                "/track/" in path -> MusicKind.SONG
                "/album/" in path -> MusicKind.ALBUM
                host(url) == "deezer.page.link" || host(url) == "link.deezer.com" -> MusicKind.SONG
                else -> null
            }
            MusicPlatform.SOUNDCLOUD -> {
                val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
                when {
                    parts.size >= 3 && parts[1] == "sets" -> MusicKind.ALBUM
                    parts.size >= 2 || host(url) == "on.soundcloud.com" -> MusicKind.SONG
                    else -> null
                }
            }
            MusicPlatform.TIDAL -> when {
                "/track/" in path -> MusicKind.SONG
                "/album/" in path -> MusicKind.ALBUM
                else -> null
            }
            MusicPlatform.AMAZON_MUSIC -> when {
                "trackAsin=" in query || "/tracks/" in path -> MusicKind.SONG
                "/albums/" in path -> MusicKind.ALBUM
                else -> null
            }
            MusicPlatform.OTHER -> if ("album" in path.lowercase()) MusicKind.ALBUM else MusicKind.SONG
        }
    }

    /** Short share links (spotify.link, deezer.page.link, on.soundcloud.com…) need following first. */
    fun isShortLink(url: String): Boolean = host(url) in setOf(
        "spotify.link", "spotify.app.link", "deezer.page.link", "link.deezer.com", "on.soundcloud.com", "snd.sc"
    )

    // ─── Reading page details ───────────────────────────────────────────────────

    /** The page's Open Graph tags (og:title, og:image…), which most music sites fill in for link previews. */
    fun ogTags(html: String): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val tag = match.value
            val key = Regex("""(?:property|name)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
            val content = Regex("""content\s*=\s*"([^"]*)"|content\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE).find(tag)
                ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            if (key != null && content != null && key.lowercase() !in tags) tags[key.lowercase()] = unescape(content).trim()
        }
        return tags
    }

    /**
     * Spotify: og:title is the song ("Blinding Lights") or "Listen to After Hours - Album by The Weeknd | Spotify";
     * og:description starts with the artist ("The Weeknd · After Hours · Song · 2020").
     */
    fun fromSpotifyPage(tags: Map<String, String>): Triple<String?, String?, String?> {
        var title = tags["og:title"]?.removePrefix("Listen to ")?.substringBefore(" | Spotify")
        var artist = tags["og:description"]?.substringBefore(" · ")?.takeIf { "·" in tags["og:description"].orEmpty() }
        title?.let { t ->
            Regex("""^(.*) - (?:Album|Single|EP|Song) by (.*)$""").find(t)?.let {
                title = it.groupValues[1]
                if (artist.isNullOrBlank()) artist = it.groupValues[2]
            }
        }
        return Triple(title?.trim(), artist?.trim(), tags["og:image"])
    }

    /** Apple Music: og:title is "After Hours by The Weeknd on Apple Music"; og:description starts "Song ·" or "Album ·". */
    fun fromApplePage(tags: Map<String, String>): Triple<String?, String?, MusicKind?> {
        val raw = tags["og:title"]?.replace("‎", "")?.removeSuffix(" on Apple Music")?.trim()
        var title = raw
        var artist: String? = null
        raw?.let { t ->
            val dashed = Regex("""^(.*) - (?:Song|Album|Single|EP) by (.*?)(?: - Apple Music)?$""").find(t)
            if (dashed != null) {
                title = dashed.groupValues[1]; artist = dashed.groupValues[2]
            } else if (" by " in t) {
                title = t.substringBeforeLast(" by "); artist = t.substringAfterLast(" by ")
            }
        }
        val kind = when (tags["og:description"]?.substringBefore(" ·")?.trim()?.lowercase()) {
            "song" -> MusicKind.SONG
            "album", "single", "ep" -> MusicKind.ALBUM
            else -> null
        }
        return Triple(title?.trim(), artist?.trim(), kind)
    }

    /**
     * YouTube: "The Weeknd - Blinding Lights (Official Video)" by channel "TheWeekndVEVO".
     * Splits artist and title, and drops "(Official Video)"-style extras.
     */
    fun fromYouTube(title: String, channel: String?): Pair<String, String?> {
        val cleanTitle = title
            .replace(Regex("""\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visualizer|hd|4k)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .removePrefix("Album - ")
            .trim()
        val dash = cleanTitle.indexOf(" - ")
        return if (dash > 0) {
            cleanTitle.substring(dash + 3).trim() to cleanTitle.substring(0, dash).trim()
        } else {
            cleanTitle to channel?.removeSuffix(" - Topic")?.removeSuffix("VEVO")?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /** SoundCloud: "Flickermood by Forss". */
    fun fromSoundCloud(title: String, author: String?): Pair<String, String?> =
        if (author != null && title.endsWith(" by $author")) title.removeSuffix(" by $author") to author
        else title to author

    /** Anything else: og:title, trying "Title - Song by Artist" and "Title by Artist" shapes. */
    fun fromGenericPage(tags: Map<String, String>): Pair<String?, String?> {
        val raw = (tags["og:title"] ?: tags["twitter:title"])?.trim() ?: return null to null
        Regex("""^(.*) - (?:Song|Album|Single|EP|Track) by (.*?)(?: [-|] .*)?$""", RegexOption.IGNORE_CASE).find(raw)?.let {
            return it.groupValues[1].trim() to it.groupValues[2].trim()
        }
        if (" by " in raw) return raw.substringBeforeLast(" by ").trim() to raw.substringAfterLast(" by ").substringBefore(" on ").trim()
        return raw to tags["music:musician_description"]
    }

    /** Deezer ids from www.deezer.com/en/track/908604612 */
    fun deezerId(url: String): Pair<String, String>? =
        Regex("""/(track|album)/(\d+)""").find(path(url))?.let { it.groupValues[1] to it.groupValues[2] }

    /** music.youtube.com links work with YouTube's own look-up once the host is swapped. */
    fun youtubeOEmbedUrl(url: String): String =
        "https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode(url.replace("music.youtube.com", "www.youtube.com"), "UTF-8")

    fun unescape(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: it.value }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toIntOrNull(16)?.let { c -> String(Character.toChars(c)) } ?: it.value }

    private fun host(url: String) = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull().orEmpty()
    private fun path(url: String) = runCatching { URI(url).path }.getOrNull().orEmpty()
    private fun query(url: String) = runCatching { URI(url).rawQuery }.getOrNull().orEmpty()
}
