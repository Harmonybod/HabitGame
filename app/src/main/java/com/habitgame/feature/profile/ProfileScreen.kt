package com.habitgame.feature.profile

import com.habitgame.ui.theme.cardSurface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.engine.UserProgressEngine
import androidx.activity.result.PickVisualMediaRequest
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.ProfileAvatar
import com.habitgame.ui.components.XpProgressBar
import com.habitgame.ui.theme.*

@Composable
fun ProfileScreen(
    onNavigateSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Android's photo picker: no storage permission needed
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.setProfileImage(it) }
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val hasPhoto = state.user.profileImageUri.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card with profile image
        item {
            GameCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        ProfileAvatar(
                            imagePath = state.user.profileImageUri,
                            displayName = state.user.displayName,
                            size = 96.dp,
                            initialSize = 36.sp,
                            modifier = Modifier.clickable(onClickLabel = "Change profile picture", onClick = pickPhoto)
                        )
                        // Badge overlay
                        if (state.user.selectedBadge.isNotEmpty()) {
                            Text(state.user.selectedBadge, fontSize = 20.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = pickPhoto) { Text(if (hasPhoto) "Change photo" else "Add photo") }
                        if (hasPhoto) {
                            TextButton(onClick = viewModel::removeProfileImage) {
                                Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(state.user.displayName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(state.user.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    XpProgressBar(
                        level = state.user.level, title = state.user.title,
                        progress = state.xpProgress, currentXp = state.user.currentXp, xpRequired = state.xpRequired
                    )
                }
            }
        }

        // Stats Grid
        item { Text("Statistics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("🔥", "${state.user.currentStreak}", "Day Streak", Modifier.weight(1f))
                StatCard("🏆", "${state.user.longestStreak}", "Best Streak", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("✅", "${state.totalTasksDone}", "Tasks Done", Modifier.weight(1f))
                StatCard("⏱", "${state.totalFocusSessions}", "Focus Sessions", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("⭐", "${state.user.totalCoins}", "Total Coins", Modifier.weight(1f))
                StatCard("🕐", "${"%.1f".format(state.totalFocusHours)}h", "Focus Time", Modifier.weight(1f))
            }
        }

        // Badges
        item {
            Spacer(Modifier.height(4.dp))
            Text("Badges", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.badges.forEach { badge -> BadgeCard(badge) }
            }
        }
    }
}

@Composable
fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .cardSurface(RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun BadgeCard(badge: Badge) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (badge.unlocked) MaterialTheme.colorScheme.primary.copy(0.4f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .alpha(if (badge.unlocked) 1f else 0.4f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(badge.emoji, fontSize = 22.sp)
        Column {
            Text(badge.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(badge.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

