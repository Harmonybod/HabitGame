package com.habitgame.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.appicon.LauncherIcon

/** The app icon choices side by side, drawn the way a launcher shows them. */
@Composable
internal fun AppIconPicker(current: LauncherIcon, onPick: (LauncherIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "The icon on your home screen and in the app drawer. Your launcher can take a few seconds to show the new one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LauncherIcon.entries.forEach { icon ->
                val on = icon == current
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Use the ${icon.title} icon") { if (!on) onPick(icon) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val shape = RoundedCornerShape(24.dp)
                    Box(
                        Modifier
                            .size(88.dp)
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape).padding(5.dp) else Modifier.padding(5.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                    ) {
                        // The art fills a 108dp canvas of which launchers show the middle 72dp
                        Image(
                            painterResource(icon.art), contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        icon.title, fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (on) "In use" else if (icon == LauncherIcon.WORDMARK) "Default" else "Tap to use",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
