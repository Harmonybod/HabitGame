package com.habitgame.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicLinksTest {

    @Test
    fun findsTheLinkInSharedText() {
        assertEquals(
            "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=abc",
            MusicLinks.findLink("Blinding Lights - The Weeknd https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=abc")
        )
        assertNull(MusicLinks.findLink("no link here"))
    }

    @Test
    fun recognisesPlatformsAndSongOrAlbum() {
        fun check(url: String, platform: MusicPlatform, kind: MusicKind?) {
            assertEquals(url, platform, MusicLinks.platformOf(url))
            assertEquals(url, kind, MusicLinks.kindOf(url))
        }
        check("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=1", MusicPlatform.SPOTIFY, MusicKind.SONG)
        check("https://open.spotify.com/intl-de/album/4yP0hdKOZPNshxUOjY0cZj", MusicPlatform.SPOTIFY, MusicKind.ALBUM)
        check("https://open.spotify.com/artist/1Xyo4u8uXC1ZmMpatF05PJ", MusicPlatform.SPOTIFY, null)
        check("https://music.youtube.com/watch?v=fHI8X4OXluQ", MusicPlatform.YOUTUBE_MUSIC, MusicKind.SONG)
        check("https://music.youtube.com/playlist?list=OLAK5uy_abc", MusicPlatform.YOUTUBE_MUSIC, MusicKind.ALBUM)
        check("https://youtu.be/4NRXx6U8ABQ", MusicPlatform.YOUTUBE, MusicKind.SONG)
        check("https://music.apple.com/us/album/after-hours/1499378108?i=1499378615", MusicPlatform.APPLE_MUSIC, MusicKind.SONG)
        check("https://music.apple.com/us/album/after-hours/1499378108", MusicPlatform.APPLE_MUSIC, MusicKind.ALBUM)
        check("https://www.deezer.com/en/track/908604612", MusicPlatform.DEEZER, MusicKind.SONG)
        check("https://soundcloud.com/forss/sets/soulhack", MusicPlatform.SOUNDCLOUD, MusicKind.ALBUM)
        check("https://soundcloud.com/forss/flickermood", MusicPlatform.SOUNDCLOUD, MusicKind.SONG)
        check("https://tidal.com/browse/track/12345", MusicPlatform.TIDAL, MusicKind.SONG)
        check("https://music.amazon.com/albums/B084?trackAsin=B085", MusicPlatform.AMAZON_MUSIC, MusicKind.SONG)
    }

    @Test
    fun readsOpenGraphTagsInAnyAttributeOrder() {
        val html = """<meta property="og:title" content="Blinding Lights"/><meta content="The Weeknd &amp; Friends · After Hours · Song · 2020" property="og:description">"""
        val tags = MusicLinks.ogTags(html)
        assertEquals("Blinding Lights", tags["og:title"])
        assertEquals("The Weeknd & Friends · After Hours · Song · 2020", tags["og:description"])
    }

    @Test
    fun spotifySongAndAlbum() {
        val song = MusicLinks.fromSpotifyPage(mapOf("og:title" to "Blinding Lights", "og:description" to "The Weeknd · After Hours · Song · 2020", "og:image" to "img"))
        assertEquals(Triple("Blinding Lights", "The Weeknd", "img"), song)
        val album = MusicLinks.fromSpotifyPage(mapOf("og:title" to "Listen to After Hours - Album by The Weeknd | Spotify", "og:description" to "The Weeknd · album · 2020 · 14 songs"))
        assertEquals("After Hours", album.first)
        assertEquals("The Weeknd", album.second)
    }

    @Test
    fun appleMusicTitleAndKind() {
        val (title, artist, kind) = MusicLinks.fromApplePage(mapOf("og:title" to "After Hours by The Weeknd on Apple Music", "og:description" to "Album · 2020 · 14 Songs"))
        assertEquals("After Hours", title)
        assertEquals("The Weeknd", artist)
        assertEquals(MusicKind.ALBUM, kind)
        assertEquals(MusicKind.SONG, MusicLinks.fromApplePage(mapOf("og:description" to "Song · 2020 · Duration 6:01")).third)
    }

    @Test
    fun youtubeTitlesAreTidied() {
        assertEquals("Blinding Lights" to "The Weeknd", MusicLinks.fromYouTube("The Weeknd - Blinding Lights (Official Video)", "TheWeekndVEVO"))
        assertEquals("Childhood" to "Some Artist", MusicLinks.fromYouTube("Childhood", "Some Artist - Topic"))
    }

    @Test
    fun soundCloudTitle() {
        assertEquals("Flickermood" to "Forss", MusicLinks.fromSoundCloud("Flickermood by Forss", "Forss"))
    }
}
