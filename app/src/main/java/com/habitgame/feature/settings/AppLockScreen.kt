package com.habitgame.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habitgame.core.database.entity.AppLockEntity
import com.habitgame.domain.restriction.LockableApp
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.GameCard

/** Settings → App lock: only the locked apps, each can be unlocked, and + adds more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(onBack: () -> Unit, viewModel: AppLockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf<AppLockEntity?>(null) }

    // Permissions may have been allowed in the phone's settings meanwhile
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    unlocking?.let { app ->
        AlertDialog(
            onDismissRequest = { unlocking = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Unlock ${app.appName}?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "It won't be locked any more, so you can open it without spending coins. You can lock it again with +.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.unlock(app); unlocking = null }) { Text("Unlock", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { unlocking = null }) { Text("Cancel") } }
        )
    }

    if (adding) {
        LaunchedEffect(Unit) { viewModel.loadInstalledApps() }
        AddLockedAppsSheet(
            apps = state.installed,
            locked = state.locked.map { it.packageName }.toSet(),
            iconFor = viewModel::iconFor,
            onLock = viewModel::lock,
            onDismiss = { adding = false }
        )
    }

    SettingsPage(
        "App lock", onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }, containerColor = MaterialTheme.colorScheme.primary) {
                AppIcon("Add", contentDescription = "Lock another app", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) {
        if (!state.hasAllPermissions) item {
            GameCard {
                Text("⚠️ Locking needs two permissions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    "Find HabitGame in the list, switch it on, then come back.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.hasUsageStats) OutlinedButton(onClick = viewModel::requestUsageStats, modifier = Modifier.weight(1f)) { Text("Usage access", fontSize = 13.sp) }
                    if (!state.hasOverlay) OutlinedButton(onClick = viewModel::requestOverlay, modifier = Modifier.weight(1f)) { Text("Show over apps", fontSize = 13.sp) }
                }
            }
        }
        item {
            Text(
                if (state.locked.isEmpty()) "No apps are locked. Tap + to pick the ones that distract you."
                else "These stay locked until you spend coins in the Shop to open them for a while.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }
        items(state.locked, key = { it.packageName }) { app ->
            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(app.packageName, viewModel::iconFor)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text("🔒 Locked", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { unlocking = app },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text("Unlock", fontSize = 13.sp) }
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }  // room for the + button
    }
}

/** Every app on the phone that isn't locked yet, with a search box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLockedAppsSheet(
    apps: List<LockableApp>?,
    locked: Set<String>,
    iconFor: suspend (String) -> ImageBitmap?,
    onLock: (LockableApp) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Lock an app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search apps") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            if (apps == null) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val visible = apps.filter { it.packageName !in locked && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Lock ${app.name}") { onLock(app) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(app.packageName, iconFor)
                            Spacer(Modifier.width(12.dp))
                            Text(app.name, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            AppIcon("Lock", contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (visible.isEmpty()) item {
                        Text(
                            if (query.isBlank()) "Every app is already locked." else "No apps match “$query”.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(packageName: String, iconFor: suspend (String) -> ImageBitmap?) {
    val icon by produceState<ImageBitmap?>(null, packageName) { value = iconFor(packageName) }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) } ?: Text("📱", fontSize = 24.sp)
    }
}
