package com.habitgame.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.music.MusicInfo
import com.habitgame.domain.music.MusicResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicEntryPoint {
    fun musicResolver(): MusicResolver
}

/**
 * A song or album: cover, title, artist and where it's from. Tapping it opens it in
 * that service's app (or the browser if the app isn't installed).
 */
@Composable
fun MusicCard(music: MusicInfo, modifier: Modifier = Modifier, coverSize: Dp = 60.dp) {
    val context = LocalContext.current
    val cover = rememberCover(music.coverUrl)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = "Open in ${music.platform.displayName}") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(music.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(coverSize).clip(RoundedCornerShape(10.dp)).background(Color(music.platform.color).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(cover, contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (music.kind.name == "ALBUM") "💿" else "🎵", fontSize = (coverSize.value * 0.4f).sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                music.title ?: "Loading details…",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            music.artist?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(music.platform.color)))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${music.platform.displayName} · ${music.kind.label}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Text("▶", color = Color(music.platform.color), fontSize = 20.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/** The cover from the phone, downloading it the first time (e.g. after a transfer code). */
@Composable
private fun rememberCover(coverUrl: String?): ImageBitmap? {
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(null, coverUrl) {
        value = coverUrl?.let {
            EntryPointAccessors.fromApplication(context.applicationContext, MusicEntryPoint::class.java)
                .musicResolver().cover(it)?.asImageBitmap()
        }
    }
    return cover
}
