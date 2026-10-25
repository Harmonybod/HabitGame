package com.habitgame.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.ProfileAvatar
import com.habitgame.ui.components.rememberCloudSync
import com.habitgame.ui.theme.DangerRed
import com.habitgame.ui.theme.readable
import kotlinx.coroutines.launch

/** The pages inside Settings, as navigation routes. */
object SettingsRoutes {
    const val PROFILE = "settings/profile"
    const val GENERAL = "settings/general"
    const val DEVICES = "settings/devices"
    const val NOTIFICATIONS = "settings/notifications"
    const val CUSTOMIZATION = "settings/customization"
    const val APP_LOCK = "settings/app_lock"
    const val MANUAL_SYNC = "settings/manual_sync"
}

/**
 * Settings: your profile at the top, then one row per section, each opening its own page,
 * then Log out and Reset all data.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val user = state.user
    val sync = rememberCloudSync()
    val syncState by sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLogOut by remember { mutableStateOf(false) }

    if (confirmReset) ResetDialog(signedIn = syncState.signedIn, onConfirm = { viewModel.resetAllData(); confirmReset = false }, onCancel = { confirmReset = false })
    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Log out of Google?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Everything stays on this phone, and your saved copy stays in your Google account. This phone stops syncing until you sign in again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { sync.signOut() }; confirmLogOut = false }) { Text("Log out", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmLogOut = false }) { Text("Cancel") } }
        )
    }

    SettingsPage("Settings", onBack) {
        // ─── Profile ──────────────────────────────────────────────────────
        item {
            GameCard(Modifier.clickable(onClickLabel = "Edit profile") { onOpen(SettingsRoutes.PROFILE) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(user.profileImageUri, user.displayName, size = 60.dp, initialSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.displayName + if (user.selectedBadge.isNotEmpty()) " ${user.selectedBadge}" else "",
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            syncState.email ?: "Level ${user.level} · ${user.title}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onOpen(SettingsRoutes.PROFILE) }) {
                        AppIcon("Edit", contentDescription = "Edit profile", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ─── Sections ─────────────────────────────────────────────────────
        item {
            GameCard {
                Column {
                    val rows = listOf(
                        Triple("⚙️", "General", "Level ${user.level} · 🔥 ${user.currentStreak}-day streak · " + if (syncState.signedIn) "synced" else "not signed in") to SettingsRoutes.GENERAL,
                        Triple("📲", "Devices", if (syncState.signedIn) "Phones using your account" else "Sign in to see your phones") to SettingsRoutes.DEVICES,
                        Triple("🔔", "Notifications", "When reminders come") to SettingsRoutes.NOTIFICATIONS,
                        Triple("🎨", "Customization", "Themes, icon packs, glass, colours, widgets") to SettingsRoutes.CUSTOMIZATION,
                        Triple("🔒", "App lock", if (state.lockedApps == 0) "No apps locked" else "${state.lockedApps} app${if (state.lockedApps == 1) "" else "s"} locked") to SettingsRoutes.APP_LOCK,
                        Triple("🔑", "Manual sync", "Move progress with a transfer code") to SettingsRoutes.MANUAL_SYNC
                    )
                    rows.forEachIndexed { i, (row, route) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.6f))
                        SettingsRow(row.first, row.second, row.third) { onOpen(route) }
                    }
                }
            }
        }

        // ─── Account ──────────────────────────────────────────────────────
        if (syncState.signedIn) item {
            GameCard {
                SettingsRow("🚪", "Log out", "Signed in as ${syncState.email ?: syncState.name}", showArrow = false) { confirmLogOut = true }
            }
        }

        // ─── Reset ────────────────────────────────────────────────────────
        item {
            Button(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = androidx.compose.ui.graphics.Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                AppIcon("DeleteForever", contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset all data", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResetDialog(signedIn: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon("Warning", contentDescription = null, tint = DangerRed)
                Text("Reset all data?", color = DangerRed.readable(), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("This permanently deletes everything on this phone:", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                listOf("Tasks, habits and the shopping list", "XP, coins, level and streaks", "Journal, savings and the 100-day board", "Everything bought in the Shop", "App lock choices")
                    .forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                if (signedIn) {
                    Spacer(Modifier.height(8.dp))
                    Text("You're signed in, so the copy in your Google account is reset too.", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("⚠️ This can't be undone.", color = DangerRed.readable(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = androidx.compose.ui.graphics.Color.White)) {
                Text("Continue")
            }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** One line in a list of settings: emoji, title, short description, and an arrow when it opens a page. */
@Composable
internal fun SettingsRow(emoji: String, title: String, subtitle: String?, showArrow: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(0.14f)),
            contentAlignment = Alignment.Center
        ) { com.habitgame.ui.PackEmoji(emoji, 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showArrow) AppIcon("ArrowForward", contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** A Settings page: a top bar with a back arrow, and a scrolling column of cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon("ArrowBack", "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            content = content
        )
    }
}
