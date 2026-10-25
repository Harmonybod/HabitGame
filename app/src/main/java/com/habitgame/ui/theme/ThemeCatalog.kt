package com.habitgame.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 11 shades of one colour, from 50 to 950, as in the Realtime Colors CSS (design/realtime_colors_20_themes.css). */
class ShadeScale(private val colors: List<Color>) {
    init { require(colors.size == STEPS.size) { "A shade scale needs ${STEPS.size} colours" } }

    /** e.g. `primary[500]` */
    operator fun get(shade: Int): Color = colors[STEPS.indexOf(shade).also { require(it >= 0) { "No shade $shade" } }]

    val all: List<Color> get() = colors

    companion object {
        val STEPS = listOf(50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950)
    }
}

internal fun shades(vararg argb: Long) = ShadeScale(argb.map { Color(it) })

/**
 * A Realtime Colors theme: five palettes of 11 shades. Light themes run light (50) to dark (950);
 * dark themes run the other way, so shade 50 is always the page colour and 900 the text colour.
 */
class ThemeShades(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val dark: Boolean,
    val text: ShadeScale,
    val background: ShadeScale,
    val primary: ShadeScale,
    val secondary: ShadeScale,
    val accent: ShadeScale
)

/** WCAG contrast ratio between two colours (1 to 21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** The first shade from [start] towards the far end that reads 4.5:1 on [on]. */
private fun ShadeScale.readableFrom(start: Int, on: Color): Color =
    ShadeScale.STEPS.dropWhile { it < start }.map { this[it] }.firstOrNull { contrastRatio(it, on) >= 4.5f } ?: this[950]

/** Turns the 55 shades into the colours the app's screens use. */
internal fun ThemeShades.toPalette(): Palette {
    val page = background[50]
    // Cards: near-white on light themes, one step up from the page on dark ones
    val surface = if (dark) background[100] else lerp(Color.White, page, 0.3f)
    val surfaceHigh = if (dark) lerp(background[100], background[200], 0.5f) else background[100]
    val primaryColor = primary.readableFrom(if (dark) 500 else 600, page)
    val secondaryColor = secondary.readableFrom(if (dark) 500 else 600, page)
    return Palette(
        dark = dark,
        primary = primaryColor, onPrimary = contentColorFor(primaryColor),
        secondary = secondaryColor, onSecondary = contentColorFor(secondaryColor),
        tertiary = accent.readableFrom(if (dark) 500 else 600, page),
        background = page,
        surface = surface,
        surfaceHigh = surfaceHigh,
        onSurface = text[900],
        onSurfaceVariant = text.readableFrom(600, surfaceHigh),
        // Light themes need a darker edge so switches, fields and card borders stay visible
        outline = if (dark) background[200] else background[300],
        highlight = accent[500]
    )
}

/** One theme the user can pick: its name, look, and whether it's free or sold in the Shop. */
class GameTheme internal constructor(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val free: Boolean,
    internal val palette: Palette,
    /** All 11 shades of each palette; null for the app's original hand-made themes. */
    val shades: ThemeShades? = null
) {
    val dark: Boolean get() = palette.dark

    /** Page, card, main, second and highlight colours, for theme previews. */
    val previewColors: List<Color>
        get() = listOf(palette.background, palette.surface, palette.primary, palette.secondary, palette.highlight)
    val previewText: Color get() = palette.onSurface
    val previewTextDim: Color get() = palette.onSurfaceVariant
    val previewOutline: Color get() = palette.outline
}

/**
 * Every theme in the app. The ids are stored in the database, so they never change.
 * "default" (dark) and "minimal" (light) are the free originals; the rest are bought in the Shop.
 * A theme added to RealtimeThemes appears here, in Settings and in the Shop by itself.
 */
object ThemeCatalog {
    const val PRICE = 200L

    val originals: List<GameTheme> = listOf(
        GameTheme("default", "Default", "🌙", "The original dark theme", free = true, palette = DefaultPalette),
        GameTheme("minimal", "Minimal", "⬜", "Clean light theme", free = true, palette = MinimalPalette),
        GameTheme("neon", "Neon", "🌈", "Vibrant neon colours", free = false, palette = NeonPalette),
        GameTheme("midnight", "Midnight", "🌙", "Deep dark variant", free = false, palette = MidnightPalette),
        GameTheme("amoled", "AMOLED Dark", "🖤", "True black everywhere", free = false, palette = AmoledPalette)
    )

    /** The 20 Realtime Colors themes, 200 coins each in the Shop. */
    val realtime: List<GameTheme> = RealtimeThemes.map {
        GameTheme(it.id, it.name, it.emoji, it.description, free = false, palette = it.toPalette(), shades = it)
    }

    val all: List<GameTheme> = originals + realtime

    private val byId = all.associateBy { it.id }

    fun byId(id: String): GameTheme? = byId[id]
}

/** The current theme's 11-shade palettes, for screens that want a specific shade (null on the original themes). */
val LocalThemeShades = staticCompositionLocalOf<ThemeShades?> { null }
