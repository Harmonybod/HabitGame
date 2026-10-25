package com.habitgame.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {

    @Test
    fun hasTheOriginalsAndTwentyNewThemesWithStableIds() {
        assertEquals(20, ThemeCatalog.realtime.size)
        assertEquals(listOf("default", "minimal", "neon", "midnight", "amoled"), ThemeCatalog.originals.map { it.id })
        assertEquals(ThemeCatalog.all.size, ThemeCatalog.all.map { it.id }.toSet().size)
        assertEquals("lavender-dream", ThemeCatalog.realtime.first().id)
        assertEquals("amoled-sky", ThemeCatalog.realtime.last().id)
    }

    @Test
    fun everyRealtimeThemeKeepsAll55Shades() {
        ThemeCatalog.realtime.forEach { theme ->
            val s = theme.shades!!
            listOf(s.text, s.background, s.primary, s.secondary, s.accent).forEach { assertEquals(11, it.all.size) }
        }
        // Spot check against the CSS: lavender-dream --primary-500: #8539D0
        assertEquals(Color(0xFF8539D0), ThemeCatalog.byId("lavender-dream")!!.shades!!.primary[500])
    }

    @Test
    fun darkAndLightFollowTheCss() {
        val dark = setOf("midnight-blue", "neon-forest", "cosmic-purple", "deep-ocean", "crimson-night", "solar-flare", "graphite", "electric-orchid", "dark-mocha", "amoled-sky")
        ThemeCatalog.realtime.forEach { assertEquals(it.id, it.id in dark, it.dark) }
    }

    @Test
    fun textAndMainColoursAreReadable() {
        ThemeCatalog.all.forEach { theme ->
            val p = theme.palette
            assertTrue("${theme.id} text", contrastRatio(p.onSurface, p.background) >= 7f)
            assertTrue("${theme.id} text on cards", contrastRatio(p.onSurface, p.surface) >= 7f)
            assertTrue("${theme.id} buttons", contrastRatio(p.onPrimary, p.primary) >= 3f)
            if (theme.shades != null) {
                assertTrue("${theme.id} dim text", contrastRatio(p.onSurfaceVariant, p.surfaceHigh) >= 4.5f)
                assertTrue("${theme.id} primary", contrastRatio(p.primary, p.background) >= 4.5f)
            }
        }
    }

    @Test
    fun unknownThemesFallBack() {
        assertEquals(null, ThemeCatalog.byId("not-a-theme"))
    }
}
