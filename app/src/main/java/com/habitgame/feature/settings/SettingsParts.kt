package com.habitgame.feature.settings

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.theme.DangerRed
import com.habitgame.ui.theme.readable

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

/** The four home-screen widgets, each with a button that places it on the home screen. */
@Composable
internal fun WidgetsSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<String?>(null) }
    val widgets = listOf(
        Triple("✅ Today's checklist", "Tick off today's tasks and habits from your home screen", com.habitgame.widget.ChecklistWidgetReceiver::class.java),
        Triple("🛡️ Character card", "Your rank, level, XP, coins and streak", com.habitgame.widget.CharacterWidgetReceiver::class.java),
        Triple("🟡 100-day board", "Your 100 circles, filling up day by day", com.habitgame.widget.BoardWidgetReceiver::class.java),
        Triple("💰 Savings jar", "Money saved toward your goal, with a quick Add button", com.habitgame.widget.SavingsWidgetReceiver::class.java)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Pick the ones you want on your home screen. They use your theme and update by themselves.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        widgets.forEach { (name, description, receiver) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val asked = runCatching {
                            androidx.glance.appwidget.GlanceAppWidgetManager(context).requestPinGlanceAppWidget(receiver)
                        }.getOrDefault(false)
                        hint = if (asked) null
                        else "Your launcher can't add widgets from here. Long-press your home screen → Widgets → HabitGame."
                    }
                }) { Text("Add", fontSize = 13.sp) }
            }
        }
        hint?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Or long-press your home screen → Widgets → HabitGame.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Moves progress between phones by hand: make a code here and paste it on the other phone.
 * Loading a code replaces that phone's progress, after showing what the code holds.
 */
@Composable
internal fun TransferSection(viewModel: SettingsViewModel) {
    val state by viewModel.transfer.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var pasted by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    state.pending?.let { payload ->
        val made = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(payload.createdAt))
        val saved = payload.savings.sumOf { it.amount }
        AlertDialog(
            onDismissRequest = viewModel::cancelTransfer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Replace this phone's progress?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Code made $made", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text("👤 ${payload.user.displayName} · Level ${payload.user.level} (${payload.user.title})", color = MaterialTheme.colorScheme.onSurface)
                    Text("🪙 ${payload.user.totalCoins} coins · 🔥 ${payload.user.currentStreak}-day streak", color = MaterialTheme.colorScheme.onSurface)
                    Text("📋 ${payload.tasks.size} tasks · 🔄 ${payload.habits.size} habits", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "💰 ${payload.challenge?.currency.orEmpty()}${com.habitgame.domain.notification.TaskReminderWorker.formatAmount(saved)} saved · " +
                            "🎨 ${payload.ownedCosmetics.size} themes/badges/packs",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Everything on this phone is replaced with this. Your photos, app locks and any running App Unlock stay as they are.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmTransfer(); pasted = "" }) { Text("Replace", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelTransfer) { Text("Cancel") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Move your progress to another phone: level and XP, coins, streak, the 100-day board, tasks, habits, savings, journal, and the themes, badges and icon packs you own.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        // 1. On the phone with your progress
        Text("On this phone", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Button(
            onClick = { copied = false; viewModel.createTransferCode() },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.code == null) "Create transfer code" else "Create a new code") }
        state.code?.let { code ->
            Text(
                code,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp)
            )
            Text(
                "${code.length} characters. Send all of it, e.g. to yourself on Telegram or by email. Anyone with this code can see your progress, so keep it private.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)); copied = true },
                    modifier = Modifier.weight(1f)
                ) { Text(if (copied) "✅ Copied" else "📋 Copy code", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, code)
                        context.startActivity(android.content.Intent.createChooser(send, "Send transfer code"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📤 Share", fontSize = 13.sp) }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // 2. On the phone that should receive it
        Text("Got a code from another phone?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Paste the code here") },
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            trailingIcon = {
                TextButton(onClick = { clipboard.getText()?.text?.let { pasted = it } }) { Text("Paste") }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.previewTransferCode(pasted) },
            enabled = pasted.isNotBlank() && !state.working,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Load progress") }

        state.error?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
        state.done?.let { Text(it, color = com.habitgame.ui.theme.SuccessGreen.readable(), style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * Glass on/off (on top of the chosen theme) and the photo behind it: picked from the
 * gallery or taken with the camera, then blurred.
 */
@Composable
internal fun GlassSettings(
    owned: Boolean,
    cost: Long,
    enabled: Boolean,
    photoPath: String,
    onToggle: (Boolean) -> Unit,
    onPhotoPicked: (android.net.Uri) -> Unit,
    onPhotoTaken: (android.graphics.Bitmap) -> Unit,
    onRemovePhoto: () -> Unit
) {
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPhotoPicked) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let(onPhotoTaken) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Glass look",
                    color = MaterialTheme.colorScheme.onSurface.copy(if (owned) 1f else 0.4f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (owned) "Frosted glass on top of your theme. Turn it off to see the theme on its own."
                    else "🪙 $cost · buy it in the Shop to use it",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (owned) {
                Switch(checked = enabled, onCheckedChange = onToggle)
            } else {
                AppIcon("Lock", contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(18.dp))
            }
        }

        if (owned) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Background photo", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                if (enabled) "Blurred behind the whole app; the glass picks up its colours."
                else "Shows while Glass is on.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            val preview = com.habitgame.ui.theme.rememberBackgroundPhoto(photoPath)
            preview?.let { (image, _) ->
                androidx.compose.foundation.Image(
                    image,
                    contentDescription = "Current background photo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        gallery.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("🖼️ Choose photo", fontSize = 13.sp) }
                OutlinedButton(onClick = { camera.launch(null) }, modifier = Modifier.weight(1f)) {
                    Text("📷 Take photo", fontSize = 13.sp)
                }
            }
            if (photoPath.isNotBlank()) {
                TextButton(onClick = onRemovePhoto) { Text("Remove photo", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A theme, icon pack or badge from the Shop: selectable when bought, otherwise locked with its price. */
@Composable
internal fun ShopItemRow(item: com.habitgame.core.database.entity.RewardItemEntity, selected: Boolean, onSelect: () -> Unit) {
    val description = item.description.replace(" — unlock in Settings after purchase", "")
    ThemeRow(
        label = item.iconEmoji + " " + item.name,
        description = if (item.isOwned) description else "🪙 ${item.cost} · buy it in the Shop to use it",
        selected = selected && item.isOwned,
        locked = !item.isOwned,
        onClick = onSelect
    )
}

@Composable
internal fun ThemeRow(label: String, description: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(0.1f) else Color.Transparent)
            .then(if (!locked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (locked) MaterialTheme.colorScheme.onSurface.copy(0.4f) else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (locked) 0.4f else 1f), style = MaterialTheme.typography.bodySmall)
        }
        if (locked) {
            AppIcon("Lock", contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(18.dp))
        } else if (selected) {
            AppIcon("CheckCircle", contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

