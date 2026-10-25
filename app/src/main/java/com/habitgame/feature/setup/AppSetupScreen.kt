package com.habitgame.feature.setup

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habitgame.domain.restriction.LockableApp
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.theme.SuccessGreen
import com.habitgame.ui.theme.readable

@Composable
fun AppSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: AppSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Re-check permissions every time we come back from the system settings screens
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state.page) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            SetupPage.WELCOME -> WelcomePage { viewModel.goTo(if (state.hasAllPermissions) SetupPage.APPS else SetupPage.PERMISSIONS) }
            SetupPage.PERMISSIONS -> PermissionsPage(
                hasUsageStats = state.hasUsageStats,
                hasOverlay = state.hasOverlay,
                onRequestUsageStats = viewModel::requestUsageStats,
                onRequestOverlay = viewModel::requestOverlay,
                onContinue = { viewModel.goTo(SetupPage.APPS) }
            )
            else -> SelectAppsPage(
                state = state,
                iconFor = viewModel::iconFor,
                onQueryChange = viewModel::setQuery,
                onToggleApp = viewModel::toggleApp,
                onFixPermissions = { viewModel.goTo(SetupPage.PERMISSIONS) },
                onDone = { viewModel.saveLockedApps(onSetupComplete) }
            )
        }

        // Page indicator (only during first-time setup)
        val page = state.page
        if (page != null && !state.alreadyConfigured && page != SetupPage.APPS) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 20.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎮", fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text("Lock distracting apps", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Pick the apps that pull you away. They stay locked until you spend coins in the Shop to open them for a while.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PermissionsPage(
    hasUsageStats: Boolean,
    hasOverlay: Boolean,
    onRequestUsageStats: () -> Unit,
    onRequestOverlay: () -> Unit,
    onContinue: () -> Unit
) {
    val allGranted = hasUsageStats && hasOverlay
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🔐", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Two permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap Allow, find HabitGame in the list, switch it on, then come back here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        PermissionRow(
            icon = "📊", title = "Usage access",
            description = "Lets HabitGame see which app is open, so it knows when to lock it",
            granted = hasUsageStats, onGrant = onRequestUsageStats
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            icon = "🖥️", title = "Display over other apps",
            description = "Lets HabitGame show the lock screen on top of a locked app",
            granted = hasOverlay, onGrant = onRequestOverlay
        )

        if (allGranted) {
            Spacer(Modifier.height(16.dp))
            Text("✅ All set! Now choose which apps to lock.", color = SuccessGreen.readable(), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (allGranted) "Continue →" else "Choose apps anyway →", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermissionRow(icon: String, title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (granted) SuccessGreen.copy(0.6f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 28.sp)
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon("CheckCircle", contentDescription = null, tint = SuccessGreen.readable())
                Text("Allowed", fontSize = 11.sp, color = SuccessGreen.readable(), fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(onClick = onGrant, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Allow") }
        }
    }
}

@Composable
private fun SelectAppsPage(
    state: AppSetupState,
    iconFor: suspend (String) -> ImageBitmap?,
    onQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onFixPermissions: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("Apps to lock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Tick the apps that distract you. Spend coins in the Shop to open them for a while.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

            if (!state.hasAllPermissions) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onFixPermissions)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚠️ Locking won't work until both permissions are allowed.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Fix", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.loadingApps) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Locked apps first so they're easy to find and untick
                val sorted = state.visibleApps.sortedByDescending { it.packageName in state.selectedPackages }
                items(sorted, key = { it.packageName }) { app ->
                    AppRow(app, app.packageName in state.selectedPackages, iconFor) { onToggleApp(app.packageName) }
                }
                if (state.visibleApps.isEmpty()) {
                    item { Text("No apps match “${state.query}”.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (state.selectedPackages.isEmpty()) "Save (no apps locked)" else "Lock ${state.selectedPackages.size} app${if (state.selectedPackages.size == 1) "" else "s"}",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppRow(app: LockableApp, selected: Boolean, iconFor: suspend (String) -> ImageBitmap?, onToggle: () -> Unit) {
    val icon by produceState<ImageBitmap?>(null, app.packageName) { value = iconFor(app.packageName) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(0.5f) else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) } ?: Text("📱", fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(app.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Checkbox(
            checked = selected, onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
    }
}
