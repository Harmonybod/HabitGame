package com.habitgame.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.update.AppUpdater
import com.habitgame.domain.update.UpdateState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun appUpdater(): AppUpdater
}

@Composable
fun rememberAppUpdater(): AppUpdater {
    val context = LocalContext.current
    return remember { EntryPointAccessors.fromApplication(context.applicationContext, UpdateEntryPoint::class.java).appUpdater() }
}

/** On Home when a newer HabitGame is out: what's new, and one tap to download and install it. */
@Composable
fun UpdateBanner() {
    val updater = rememberAppUpdater()
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release.takeIf { !updater.isSnoozed(it) }
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Failed -> s.release
        UpdateState.None -> null
    } ?: return

    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⬆️", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HabitGame ${release.version} is out", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(
                    when (val s = state) {
                        is UpdateState.Downloading -> "Downloading… ${(s.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Downloaded. Tap Install, then Update."
                        is UpdateState.Failed -> s.message
                        else -> release.notes.lines().firstOrNull { it.isNotBlank() }?.trimStart('-', '*', ' ') ?: "A new version is ready."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (state is UpdateState.Downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { (state as UpdateState.Downloading).progress }, modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state is UpdateState.Available) {
                    OutlinedButton(onClick = { updater.snooze(release) }, modifier = Modifier.weight(1f)) { Text("Later") }
                }
                Button(
                    onClick = { if (state is UpdateState.Ready) updater.install() else scope.launch { updater.downloadAndInstall(release) } },
                    modifier = Modifier.weight(1f)
                ) { Text(if (state is UpdateState.Ready) "Install" else if (state is UpdateState.Failed) "Try again" else "Update", fontWeight = FontWeight.Bold) }
            }
            Text(
                "Your progress stays: the update installs over this version.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Settings → General: the installed version and a button to look for a new one. */
@Composable
fun AppVersionSection() {
    val updater = rememberAppUpdater()
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var upToDate by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Version ${updater.installedVersion}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        checking -> "Checking…"
                        state is UpdateState.Available || state is UpdateState.Ready -> "A new version is available: see Home."
                        state is UpdateState.Failed -> (state as UpdateState.Failed).message
                        upToDate -> "You have the latest version."
                        else -> "Updates come from github.com/${AppUpdater.REPO}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = {
                    checking = true
                    scope.launch {
                        val found = updater.check(force = true)
                        if (found != null) updater.unsnooze()
                        upToDate = found == null && updater.state.value !is UpdateState.Failed
                        checking = false
                    }
                },
                enabled = !checking
            ) { Text("Check") }
        }
    }
}
