package com.habitgame.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colours beyond Material's scheme: the highlight that fills the 100-day board
 * (the accent colour when one is picked), whether the theme is dark, and whether it's
 * the see-through Glass theme. [solidBackground] is an opaque page colour for full-screen
 * pop-ups (the Glass theme's normal background is see-through).
 */
data class GameColors(
    val highlight: Color,
    val onHighlight: Color,
    val isDark: Boolean,
    val isGlass: Boolean = false,
    val solidBackground: Color = Color(0xFF0E0E14)
)

val LocalGameColors = staticCompositionLocalOf { GameColors(Color(0xFFF5C02E), Color(0xFF2A2000), true) }

/** A theme described by a handful of colours; [toScheme] fills in every Material role from them. */
internal data class Palette(
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val highlight: Color,
    val glass: Boolean = false,
    val solidSurface: Color = surfaceHigh,       // dialogs and sheets: always opaque
    val solidBackground: Color = background      // full-screen pop-ups: always opaque
)

internal fun Palette.toScheme(): ColorScheme {
    val primaryContainer = primary.copy(alpha = 0.22f).compositeOver(surface)
    val secondaryContainer = secondary.copy(alpha = 0.22f).compositeOver(surface)
    val tertiaryContainer = tertiary.copy(alpha = 0.22f).compositeOver(surface)
    val highest = lerp(surfaceHigh, onSurface, 0.06f)
    // Start from Material's baseline, then set every role so nothing falls back to its default purple
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onSurface,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSurface,
        tertiary = tertiary,
        onTertiary = onSecondary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        error = DangerRed,
        onError = Color.White,
        errorContainer = DangerRed.copy(alpha = 0.2f).compositeOver(surface),
        onErrorContainer = onSurface,
        outline = outline,
        outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = surfaceHigh,
        // Dialogs, sheets and menus use these; on Glass they stay solid so text over them is readable
        surfaceContainerHighest = if (glass) lerp(solidSurface, onSurface, 0.06f) else highest,
        surfaceContainerHigh = if (glass) solidSurface else surfaceHigh,
        surfaceContainer = if (glass) solidSurface else surface,
        surfaceContainerLow = if (glass) solidSurface else surface,
        surfaceContainerLowest = if (glass) solidBackground else background,
        surfaceDim = if (glass) solidBackground else background
    )
}

internal val DefaultPalette = Palette(
    dark = true,
    primary = IndigoPrimary, onPrimary = Color.White,
    secondary = CyanXp, onSecondary = BgDark, tertiary = GoldCoin,
    background = BgDark, surface = SurfaceDark, surfaceHigh = SurfaceElevated,
    onSurface = OnSurface, onSurfaceVariant = OnSurfaceDim, outline = CardBorder,
    highlight = Color(0xFFF5C02E)
)

internal val NeonPalette = Palette(
    dark = true,
    primary = NeonPrimary, onPrimary = NeonBgDark,
    secondary = NeonSecondary, onSecondary = NeonBgDark, tertiary = NeonAccent,
    background = NeonBgDark, surface = NeonSurface, surfaceHigh = Color(0xFF1E1E40),
    onSurface = Color(0xFFF0F0FF), onSurfaceVariant = Color(0xFFA6A6D8), outline = Color(0xFF2C2C5A),
    highlight = NeonPrimary
)

internal val MinimalPalette = Palette(
    dark = false,
    primary = MinimalPrimary, onPrimary = Color.White,
    secondary = Color(0xFF0E7490), onSecondary = Color.White, tertiary = Color(0xFFB45309),
    background = MinimalBg, surface = MinimalSurface, surfaceHigh = Color(0xFFEDEFF3),
    onSurface = MinimalText, onSurfaceVariant = MinimalDimText, outline = Color(0xFFE2E5EA),
    highlight = Color(0xFFF2B705)
)

internal val MidnightPalette = Palette(
    dark = true,
    primary = MidnightPrimary, onPrimary = Color.White,
    secondary = Color(0xFF58A6FF), onSecondary = MidnightBg, tertiary = Color(0xFFF2CC60),
    background = MidnightBg, surface = MidnightSurface, surfaceHigh = Color(0xFF21262D),
    onSurface = MidnightText, onSurfaceVariant = Color(0xFF8B949E), outline = MidnightBorder,
    highlight = Color(0xFFF2CC60)
)

/** True black for AMOLED screens: black pixels are switched off, which saves battery. */
internal val AmoledPalette = Palette(
    dark = true,
    primary = Color(0xFF8B7CFF), onPrimary = Color.White,
    secondary = Color(0xFF22D3EE), onSecondary = Color.Black, tertiary = Color(0xFFFFC94D),
    background = Color.Black, surface = Color(0xFF0A0A0A), surfaceHigh = Color(0xFF151515),
    onSurface = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF9A9A9A), outline = Color(0xFF1F1F1F),
    highlight = Color(0xFF8B7CFF)
)

/** Unknown names (e.g. a theme from a newer version) fall back to the default theme. */
private fun paletteFor(themeName: String): Palette = ThemeCatalog.byId(themeName)?.palette ?: DefaultPalette

/**
 * Glass on top of any theme: the page becomes see-through so the glowing background (or
 * the user's blurred photo) shows, and cards and bars turn into frosted glass. [tint] is the
 * photo's main colour, which the glass picks up. Dialogs and sheets stay solid so they're readable.
 */
internal fun Palette.toGlass(tint: Color?): Palette {
    val pane = if (tint != null) lerp(Color.White, tint, 0.30f) else Color.White
    // Secondary text moves closer to the main text colour, so it reads over a busy background
    val clearerText = lerp(onSurfaceVariant, onSurface, 0.45f)
    return if (dark) copy(
        onSurfaceVariant = clearerText,
        background = Color.Transparent,
        surface = pane.copy(alpha = 0.10f),
        surfaceHigh = pane.copy(alpha = 0.16f),
        outline = Color.White.copy(alpha = 0.22f),
        glass = true,
        solidSurface = tint?.let { lerp(it, Color.Black, 0.62f) } ?: lerp(surfaceHigh, primary, 0.12f),
        solidBackground = tint?.let { lerp(it, Color.Black, 0.78f) } ?: background
    ) else copy(
        onSurfaceVariant = clearerText,
        background = Color.Transparent,
        surface = pane.copy(alpha = 0.50f),
        surfaceHigh = pane.copy(alpha = 0.66f),
        outline = Color.White.copy(alpha = 0.80f),
        glass = true,
        solidSurface = tint?.let { lerp(it, Color.White, 0.86f) } ?: surface,
        solidBackground = tint?.let { lerp(it, Color.White, 0.92f) } ?: background
    )
}

/**
 * The colour of the countdown ring on the floating bubble: the picked accent colour,
 * or the theme's own highlight when none is picked. Used outside Compose.
 */
fun ringColorArgb(themeName: String, accentHex: String): Int {
    if (accentHex.isNotBlank()) runCatching { return android.graphics.Color.parseColor(accentHex) }
    return paletteFor(themeName).highlight.toArgb()
}

/** Colours for the home-screen widgets, which can't use the Compose theme directly. */
data class WidgetPalette(
    val background: Color,
    val card: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val highlight: Color,
    val empty: Color,
    val dark: Boolean,
    val glass: Boolean
)

/** The user's theme, accent colour and Glass setting, turned into widget colours. */
fun widgetPalette(themeName: String, accentHex: String, glass: Boolean): WidgetPalette {
    val base = paletteFor(themeName)
    val accent = accentHex.takeIf { it.isNotBlank() }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    val primary = accent ?: base.primary
    val highlight = accent ?: base.highlight
    val useGlass = glass || themeName == "glass"
    return WidgetPalette(
        // Glass widgets are see-through so the wallpaper shows, like frosted glass
        background = if (useGlass) (if (base.dark) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.45f)) else base.background,
        card = if (useGlass) Color.White.copy(alpha = if (base.dark) 0.12f else 0.55f) else base.surfaceHigh,
        onSurface = base.onSurface,
        onSurfaceVariant = if (useGlass) lerp(base.onSurfaceVariant, base.onSurface, 0.45f) else base.onSurfaceVariant,
        primary = primary,
        onPrimary = contentColorFor(primary),
        highlight = highlight,
        empty = base.onSurface.copy(alpha = if (base.dark) 0.14f else 0.12f),
        dark = base.dark,
        glass = useGlass
    )
}

/** Near-black or white, whichever has more contrast on [background]. */
fun contentColorFor(background: Color): Color {
    val dark = Color(0xFF111111)
    return if (contrastRatio(dark, background) > contrastRatio(Color.White, background)) dark else Color.White
}

/**
 * @param glass the Glass add-on, on top of [themeName]
 * @param backgroundImage the user's blurred photo, shown behind everything while Glass is on
 * @param backgroundTint that photo's main colour
 */
@Composable
fun HabitGameTheme(
    themeName: String = "default",
    accentColor: Color? = null,
    glass: Boolean = false,
    backgroundImage: ImageBitmap? = null,
    backgroundTint: Color? = null,
    content: @Composable () -> Unit
) {
    // "glass" was a theme of its own before it became an add-on
    val useGlass = glass || themeName == "glass"
    val photo = if (useGlass) backgroundImage else null
    val base = paletteFor(themeName)
    val palette = base.let { if (useGlass) it.toGlass(if (photo != null) backgroundTint else null) else it }.let { base ->
        // A picked accent colour replaces the theme's main colours everywhere: the 100-day board, the level bar, the combo
        if (accentColor == null) base
        else base.copy(
            primary = accentColor, onPrimary = contentColorFor(accentColor),
            secondary = accentColor, onSecondary = contentColorFor(accentColor),
            highlight = accentColor
        )
    }
    val gameColors = GameColors(
        highlight = palette.highlight,
        onHighlight = contentColorFor(palette.highlight),
        isDark = palette.dark,
        isGlass = palette.glass,
        solidBackground = palette.solidBackground
    )

    // Status and navigation bar icons: dark icons on light themes, light icons on dark ones
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.dark
                isAppearanceLightNavigationBars = !palette.dark
            }
        }
    }

    CompositionLocalProvider(
        LocalGameColors provides gameColors,
        LocalThemeShades provides ThemeCatalog.byId(themeName)?.shades
    ) {
        MaterialTheme(
            colorScheme = palette.toScheme(),
            typography  = HabitGameTypography
        ) {
            ThemeBackdrop(
                glass = palette.glass,
                dark = palette.dark,
                base = base.background,
                glows = listOf(palette.primary, palette.secondary, palette.tertiary),
                photo = photo,
                photoTint = backgroundTint,
                content = content
            )
        }
    }
}

/** Status colours (gold, cyan, amber…) are tuned for dark themes; darken them on light themes so text stays readable. */
@Composable
fun Color.readable(): Color = if (LocalGameColors.current.isDark) this else lerp(this, Color.Black, 0.38f)
