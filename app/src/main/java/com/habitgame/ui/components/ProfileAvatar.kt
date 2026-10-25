package com.habitgame.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The user's round profile picture, or their first initial when no photo is set.
 * [imagePath] is a file inside the app's private storage (see ProfileViewModel).
 */
@Composable
fun ProfileAvatar(
    imagePath: String,
    displayName: String,
    size: Dp,
    initialSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val photo by produceState<ImageBitmap?>(null, imagePath) {
        value = if (imagePath.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val file = File(imagePath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(0.8f), MaterialTheme.colorScheme.primary))),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = photo
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Profile picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(displayName.take(1).uppercase(), fontSize = initialSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
