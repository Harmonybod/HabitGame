package com.habitgame.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Holds the whole app. With Glass on, it paints what shows through the glass: the user's
 * blurred photo (dimmed so text stays readable), or else the theme's background with soft
 * glowing spots in the theme's colours. The layout is the same either way, so switching
 * themes keeps you on the screen you're on.
 */
@Composable
fun ThemeBackdrop(
    glass: Boolean,
    dark: Boolean,
    base: Color,
    glows: List<Color>,
    photo: ImageBitmap?,
    photoTint: Color?,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        if (glass) {
            if (photo != null) {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                // Dim (or lighten) the photo so text stays readable: brighter photos get more
                val brightness = photoTint?.luminance() ?: 0.5f
                val scrim = if (dark) Color.Black.copy(alpha = (0.40f + 0.70f * brightness).coerceAtMost(0.82f))
                else Color.White.copy(alpha = (0.40f + 0.70f * (1f - brightness)).coerceAtMost(0.82f))
                Box(Modifier.matchParentSize().background(scrim))
            } else {
                Box(Modifier.matchParentSize().glowBackdrop(dark, base, glows))
            }
        }
        content()
    }
}

private fun Modifier.glowBackdrop(dark: Boolean, base: Color, glows: List<Color>): Modifier {
    val strength = if (dark) 0.50f else 0.38f
    return this
        .background(Brush.linearGradient(listOf(base, lerp(base, glows[0], if (dark) 0.22f else 0.12f), lerp(base, glows[1], if (dark) 0.18f else 0.10f))))
        .drawBehind {
            fun glow(color: Color, x: Float, y: Float, radius: Float) = drawCircle(
                Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = radius),
                radius = radius, center = Offset(x, y)
            )
            val w = size.width
            val h = size.height
            glow(glows[1].copy(alpha = strength), w * 0.05f, h * 0.10f, w * 0.75f)
            glow(glows[0].copy(alpha = strength), w * 1.00f, h * 0.38f, w * 0.80f)
            glow(glows[2].copy(alpha = strength * 0.6f), w * 0.10f, h * 0.70f, w * 0.70f)
            glow(glows[1].copy(alpha = strength * 0.6f), w * 0.85f, h * 0.95f, w * 0.75f)
        }
}

/**
 * A card's fill and edge. On the Glass theme: frosted, see-through white that's brighter
 * at the top, with a light rim. On other themes: the normal solid surface and outline.
 */
fun Modifier.cardSurface(shape: Shape, borderWidth: Dp = 1.dp): Modifier = composed {
    val game = LocalGameColors.current
    val scheme = MaterialTheme.colorScheme
    if (game.isGlass) {
        // The fill starts from the theme's glass colour, so a photo's tint carries into the cards
        val pane = scheme.surface
        val (top, bottom) = if (game.isDark) 1.6f to 0.6f else 1.2f to 0.7f
        this
            .background(Brush.verticalGradient(listOf(pane.copy(alpha = (pane.alpha * top).coerceAtMost(1f)), pane.copy(alpha = pane.alpha * bottom))), shape)
            .border(borderWidth, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (game.isDark) 0.45f else 0.9f), Color.White.copy(alpha = if (game.isDark) 0.10f else 0.35f))), shape)
    } else {
        this.background(scheme.surface, shape).border(borderWidth, scheme.outline, shape)
    }
}
