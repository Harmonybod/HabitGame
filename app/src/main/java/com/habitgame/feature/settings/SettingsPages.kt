package com.habitgame.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habitgame.domain.notification.ReminderTimes
import com.habitgame.domain.notification.deadlineReminderLeads
import com.habitgame.domain.notification.taskReminderLeads
import com.habitgame.feature.journal.fieldColors
import com.habitgame.feature.profile.BadgeCard
import com.habitgame.feature.profile.ProfileViewModel
import com.habitgame.feature.profile.StatCard
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.ThemeCatalog
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable

/** Runs [onResume] every time the screen comes back, e.g. from the phone's own settings. */
@Composable
private fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// ─── Profile ───────────────────────────────────────────────────────────────────

/** Change the profile picture (gallery or camera) and the name. */
@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val user = state.user
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setProfileImage) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> bitmap?.let(viewModel::setProfileImage) }
    var name by remember(user.displayName) { mutableStateOf(user.displayName) }
    var saved by remember { mutableStateOf(false) }
    val changed = name.trim() != user.displayName && name.isNotBlank()

    SettingsPage("Edit profile", onBack) {
        item {
            GameCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        ProfileAvatar(
                            user.profileImageUri, user.displayName, size = 120.dp, initialSize = 46.sp,
                            modifier = Modifier.clickable(onClickLabel = "Choose a photo") {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { AppIcon("Edit", contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                            Text("🖼️ Choose photo", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { camera.launch(null) }, modifier = Modifier.weight(1f)) { Text("📷 Take photo", fontSize = 13.sp) }
                    }
                    if (user.profileImageUri.isNotBlank()) {
                        TextButton(onClick = viewModel::removeProfileImage) { Text("Remove photo", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            GameCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Name", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= ProfileViewModel.MAX_NAME) { name = it; saved = false } },
                        singleLine = true,
                        supportingText = { Text("${name.length}/${ProfileViewModel.MAX_NAME}") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Button(
                        onClick = { saved = viewModel.setDisplayName(name) },
                        enabled = changed,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (saved && !changed) "✅ Saved" else "Save name", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─── General ───────────────────────────────────────────────────────────────────

/** Level, streak, the Google account, your badge, achievements, and a few extras. */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    onShowIntro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    profile: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val progress by profile.state.collectAsState()
    val user = state.user

    SettingsPage("General", onBack) {
        item {
            SettingsSectionHeader("🛡️ Your progress")
            GameCard {
                XpProgressBar(level = user.level, title = user.title, progress = progress.xpProgress, currentXp = user.currentXp, xpRequired = progress.xpRequired)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("🔥", "${user.currentStreak}", "Day streak", Modifier.weight(1f))
                StatCard("🏆", "${user.longestStreak}", "Best streak", Modifier.weight(1f))
                StatCard("🪙", "${user.totalCoins}", "Coins", Modifier.weight(1f))
            }
        }
        item {
            SettingsSectionHeader("☁️ Google account")
            GameCard { SyncSection(showSignOut = false) }
        }
        item {
            SettingsSectionHeader("🏅 Badge next to your name")
            GameCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeRow("➖ No badge", "Nothing next to your name", user.selectedBadge.isEmpty(), false) { viewModel.setBadge("") }
                    state.badges.forEach { badge ->
                        ShopItemRow(badge, selected = user.selectedBadge == badge.badgeKey) { viewModel.setBadge(badge.badgeKey) }
                    }
                }
            }
        }
        item {
            val unlocked = progress.badges.count { it.unlocked }
            SettingsSectionHeader("🎖️ Achievements · $unlocked of ${progress.badges.size}")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Unlocked first
                progress.badges.sortedByDescending { it.unlocked }.forEach { BadgeCard(it) }
            }
        }
        item {
            SettingsSectionHeader("⬆️ App version")
            GameCard { AppVersionSection() }
        }
        item {
            SettingsSectionHeader("👋 Getting started")
            GameCard { SettingsRow("📖", "Show the intro again", "The step-by-step guide to how HabitGame works", onClick = onShowIntro) }
        }
    }
}

// ─── Devices ───────────────────────────────────────────────────────────────────

/** The phones signed in to this Google account, and when the app was last open on each. */
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val sync = rememberCloudSync()
    val state by sync.state.collectAsState()
    var removing by remember { mutableStateOf<com.habitgame.domain.sync.SyncDevice?>(null) }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Remove ${device.name}?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "It's taken off this list. It isn't signed out: if HabitGame is opened on it while it's still signed in, it shows up again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { sync.removeDevice(device.id); removing = null }) { Text("Remove", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }

    SettingsPage("Devices", onBack) {
        when {
            !state.signedIn -> item {
                GameCard {
                    Text(
                        "Sign in with Google to keep your progress in step across phones. Every phone signed in to your account is listed here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    SyncSection()
                }
            }
            state.devices.isEmpty() -> item {
                GameCard { Text("Looking for your phones…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> {
                item {
                    Text(
                        "Phones signed in to ${state.email ?: "your account"}. Progress syncs between them by itself.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
                items(state.devices, key = { it.id }) { device ->
                    GameCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (device.isThisPhone) "📱" else "📲", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.name + if (device.isThisPhone) " · this phone" else "",
                                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    (if (device.isThisPhone) "Active now" else "Last opened " + lastSeen(device.lastSeen)) +
                                        (if (device.appVersion.isNotBlank()) " · version ${device.appVersion}" else ""),
                                    color = if (device.isThisPhone) SuccessGreen.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!device.isThisPhone) {
                                IconButton(onClick = { removing = device }) {
                                    AppIcon("Delete", contentDescription = "Remove ${device.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lastSeen(time: Long): String {
    if (time <= 0) return "a while ago"
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 2 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        else -> java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(time))
    }
}

// ─── Notifications ─────────────────────────────────────────────────────────────

/** When reminders come: any number of times before tasks and habits, and before shopping deadlines. */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    OnResume { allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }

    SettingsPage("Notifications", onBack) {
        if (!allowed) item {
            GameCard {
                Text("🔕 Notifications are off for HabitGame", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Reminders can't come until they're allowed.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Allow notifications") }
            }
        }
        item {
            SettingsSectionHeader("⏰ Tasks and habits")
            GameCard {
                ReminderPicker(
                    intro = "Remind me before each task or habit with a time. Pick as many as you like.",
                    selected = state.user.taskReminderLeads,
                    choices = ReminderTimes.TASK_CHOICES,
                    footer = "If it still isn't done, a nudge comes 30 min after its time.",
                    onToggle = viewModel::toggleTaskReminder
                )
            }
        }
        item {
            SettingsSectionHeader("🛒 Shopping deadlines")
            GameCard {
                ReminderPicker(
                    intro = "Remind me before a shopping item's deadline. Items not bought by then are removed.",
                    selected = state.user.deadlineReminderLeads,
                    choices = ReminderTimes.DEADLINE_CHOICES,
                    footer = "Only for items you've given a deadline.",
                    onToggle = viewModel::toggleDeadlineReminder
                )
            }
        }
    }
}

/** Chips for the usual times plus any custom ones, and a "Custom…" chip to add another. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPicker(intro: String, selected: List<Int>, choices: List<Int>, footer: String, onToggle: (Int) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    if (adding) CustomReminderDialog(onDismiss = { adding = false }, onAdd = { if (it !in selected) onToggle(it); adding = false })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(intro, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (choices + selected).distinct().sortedDescending().forEach { minutes ->
                val on = minutes in selected
                FilterChip(
                    selected = on,
                    onClick = { onToggle(minutes) },
                    enabled = on || selected.size < ReminderTimes.MAX,
                    label = { Text(ReminderTimes.label(minutes)) },
                    leadingIcon = if (on) {
                        { Text("✓", fontWeight = FontWeight.Bold) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            AssistChip(onClick = { adding = true }, enabled = selected.size < ReminderTimes.MAX, label = { Text("＋ Custom…") })
        }
        Text(
            "🔔 " + ReminderTimes.describe(selected).replaceFirstChar { it.uppercase() } + ".",
            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium
        )
        Text(footer, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (selected.size >= ReminderTimes.MAX) {
            Text("That's the most (${ReminderTimes.MAX}). Turn one off to add another.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomReminderDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(false) }
    val minutes = amount.toIntOrNull()?.let { if (hours) it * 60 else it }
    val valid = minutes != null && minutes in 1..(7 * 24 * 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Remind me before", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(4) },
                    label = { Text(if (hours) "Hours" else "Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Minutes", !hours) { hours = false }
                    ChoiceChip("Hours", hours) { hours = true }
                }
                if (amount.isNotEmpty() && !valid) {
                    Text("Pick something between 1 minute and 7 days.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { minutes?.let(onAdd) }, enabled = valid) { Text("Add", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Customization ─────────────────────────────────────────────────────────────

private val ACCENTS = listOf(
    "" to "Theme",
    "#6C63FF" to "Indigo",
    "#00BCD4" to "Cyan",
    "#E91E63" to "Pink",
    "#4CAF50" to "Green",
    "#FF9800" to "Amber",
    "#9C27B0" to "Purple",
    "#F44336" to "Red"
)

/** Themes (with previews), Glass, icon packs, accent colour and home-screen widgets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val user = state.user
    val shopThemes = state.themes.associateBy { it.themeKey }

    SettingsPage("Customization", onBack) {
        item {
            val owned = ThemeCatalog.all.count { it.free || shopThemes[it.id]?.isOwned == true }
            SettingsSectionHeader("🎨 Themes · $owned of ${ThemeCatalog.all.size} unlocked")
            Text(
                "Tap a theme to use it. Locked ones are in the Shop → Cosmetic → Themes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }
        // Two previews per row: free ones first, then owned, then the rest
        val themes = ThemeCatalog.all.sortedBy { theme ->
            when {
                theme.free -> 0
                shopThemes[theme.id]?.isOwned == true -> 1
                else -> 2
            }
        }
        items(themes.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme ->
                    val item = shopThemes[theme.id]
                    ThemePreviewCard(
                        theme = theme,
                        selected = user.selectedTheme == theme.id,
                        locked = !theme.free && item?.isOwned != true,
                        price = item?.cost ?: ThemeCatalog.PRICE,
                        onClick = { viewModel.setTheme(theme.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Glass, on top of any theme
        shopThemes["glass"]?.let { glass ->
            item {
                SettingsSectionHeader("🔮 Glass")
                GameCard {
                    GlassSettings(
                        owned = glass.isOwned,
                        cost = glass.cost,
                        enabled = user.glassEnabled,
                        photoPath = user.backgroundImagePath,
                        onToggle = viewModel::setGlass,
                        onPhotoPicked = { viewModel.setBackgroundPhoto(it) },
                        onPhotoTaken = { viewModel.setBackgroundPhoto(it) },
                        onRemovePhoto = viewModel::removeBackgroundPhoto
                    )
                }
            }
        }

        item {
            SettingsSectionHeader("🖼️ Icon pack")
            GameCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeRow("📦 Default", "Standard icons", user.selectedIconPack == "default", false) { viewModel.setIconPack("default") }
                    state.iconPacks.forEach { pack ->
                        ShopItemRow(pack, selected = user.selectedIconPack == pack.iconPackKey) { viewModel.setIconPack(pack.iconPackKey) }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader("🌈 Accent colour")
            GameCard {
                Text(
                    "Replaces the theme's main colour on buttons, progress bars, the 100-day board and the timer ring.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { (hex, label) ->
                        val selected = user.accentColorHex == hex
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (hex.isEmpty()) MaterialTheme.colorScheme.primary else Color(android.graphics.Color.parseColor(hex)))
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable(onClickLabel = "Use $label") { viewModel.setAccentColor(hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hex.isEmpty()) Text("A", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        item {
            SettingsSectionHeader("🧩 Widgets")
            GameCard { WidgetsSection() }
        }
    }
}

// ─── Manual sync ───────────────────────────────────────────────────────────────

/**
 * Moving progress with a transfer code, for phones that don't use the same Google account, and
 * the backups made before a sync replaced this phone's progress.
 */
@Composable
fun ManualSyncScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val backups by viewModel.backups.collectAsState()
    OnResume { viewModel.refreshBackups() }
    SettingsPage("Manual sync", onBack) {
        item { GameCard { TransferSection(viewModel) } }
        item {
            SettingsSectionHeader("🛟 Backups from syncing")
            GameCard {
                Text(
                    "When you sign in, this phone takes your Google account's progress. What it had before is saved here first, so nothing is lost. " +
                        "Restoring one makes it this phone's progress and, while you're signed in, your account's too.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                if (backups.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("No backups yet.", color = MaterialTheme.colorScheme.onSurface)
                }
                backups.forEach { backup ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "💾 " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(backup.savedAt)),
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.previewBackup(backup) }) { Text("Restore", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
