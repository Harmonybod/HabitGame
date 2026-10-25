package com.habitgame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.ui.AppIcon
import com.habitgame.ui.theme.GameTheme
import com.habitgame.ui.theme.GoldCoin
import com.habitgame.ui.theme.readable

/** A small square in a theme's colours: its page, a card, and its main, second and highlight colours. */
@Composable
fun ThemeSwatch(theme: GameTheme, size: Dp, modifier: Modifier = Modifier) {
    val (page, card, primary, secondary, highlight) = theme.previewColors
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(page)
            .border(1.dp, theme.previewOutline, shape)
            .padding(size * 0.14f)
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(size * 0.12f)).background(card).padding(size * 0.1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.fillMaxWidth(0.8f).height(size * 0.08f).clip(CircleShape).background(theme.previewText))
            Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                listOf(primary, secondary, highlight).forEach { Box(Modifier.size(size * 0.14f).clip(CircleShape).background(it)) }
            }
        }
    }
}

/**
 * A preview of the app in [theme] (a page with a card, a progress bar and buttons), with its name and
 * whether it's in use, free, owned or still for sale. Locked themes can't be picked.
 */
@Composable
fun ThemePreviewCard(
    theme: GameTheme,
    selected: Boolean,
    locked: Boolean,
    price: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (page, card, primary, secondary, highlight) = theme.previewColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = !locked, role = Role.RadioButton, onClickLabel = "Use ${theme.name}", onClick = onClick)
            .padding(6.dp)
    ) {
        // The mock-up, in the theme's own colours
        Box(
            Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(page)
                .alpha(if (locked) 0.55f else 1f)
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(primary))
                    Box(Modifier.width(42.dp).height(6.dp).clip(CircleShape).background(theme.previewText))
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(card)
                        .border(1.dp, theme.previewOutline, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.width(54.dp).height(5.dp).clip(CircleShape).background(theme.previewTextDim))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(theme.previewOutline)) {
                        Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().clip(CircleShape).background(primary))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(highlight, highlight, secondary).forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(it)) }
                    }
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(primary),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = com.habitgame.ui.theme.contentColorFor(primary),fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            if (locked) {
                AppIcon("Lock", contentDescription = "Locked", tint = theme.previewText, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${theme.emoji} ${theme.name}",
            color = MaterialTheme.colorScheme.onSurface.copy(if (locked) 0.6f else 1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            when {
                selected -> "In use"
                locked -> "🪙 $price · in the Shop"
                theme.free -> "Free · ${if (theme.dark) "dark" else "light"}"
                else -> "Owned · ${if (theme.dark) "dark" else "light"}"
            },
            color = if (locked) GoldCoin.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
        )
    }
}
