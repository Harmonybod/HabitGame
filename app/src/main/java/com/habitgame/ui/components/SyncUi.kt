package com.habitgame.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.sync.CloudSync
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun cloudSync(): CloudSync
}

@Composable
fun rememberCloudSync(): CloudSync {
    val context = LocalContext.current
    return remember { EntryPointAccessors.fromApplication(context.applicationContext, SyncEntryPoint::class.java).cloudSync() }
}

/** Google's sign-in button: white, with the four-colour "G". */
@Composable
fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
        border = BorderStroke(1.dp, Color(0xFFDADCE0))
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("G") }
            },
            fontWeight = FontWeight.Black,
            fontSize = 20.sp
        )
        Spacer(Modifier.width(12.dp))
        Text("Sign in with Google", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** The Google account card: sign in, or who's signed in and whether progress is saved. */
@Composable
fun SyncSection(showSignOut: Boolean = true) {
    val sync = rememberCloudSync()
    val state by sync.state.collectAsState()
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !state.configured -> {
                Text("Cloud sync isn't switched on in this version yet.", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(
                    "Once it's set up, you can sign in with Google to back up your progress and use it on all your phones. Until then, use Transfer progress below.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            !state.signedIn -> {
                Text(
                    "Sign in with Google to back up your progress and keep it the same on all your phones.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                GoogleSignInButton(
                    enabled = !busy && activity != null,
                    onClick = {
                        busy = true; message = null
                        scope.launch { message = sync.signIn(activity!!); busy = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853)))),
                        contentAlignment = Alignment.Center
                    ) { Text((state.name ?: state.email ?: "?").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.name ?: "Signed in", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        state.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Text(
                    when {
                        state.syncing -> "☁️ Syncing…"
                        state.lastSynced != null -> "☁️ In sync · last saved ${relativeTime(state.lastSynced!!)}"
                        else -> "☁️ Connecting…"
                    },
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = sync::syncNow, enabled = !state.syncing, modifier = Modifier.weight(1f)) { Text("Sync now") }
                    if (showSignOut) OutlinedButton(
                        onClick = { scope.launch { busy = true; sync.signOut(); busy = false } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sign out") }
                }
                if (showSignOut) Text(
                    "Signing out keeps everything on this phone; your cloud copy stays in your Google account.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        (message ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
}

/** The last step of the first-launch guide: sign in, or skip. */
@Composable
fun SignInStep(onDone: () -> Unit) {
    val sync = rememberCloudSync()
    val state by sync.state.collectAsState()
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Signed in: carry on into the app (any cloud progress loads by itself)
    LaunchedEffect(state.signedIn) { if (state.signedIn) onDone() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("☁️", fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text("Keep your progress safe", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Sign in with Google to back up your levels, coins, streak, journal and savings, and keep them the same on every phone you use. Already used HabitGame on another phone? Sign in and that progress comes to this phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        GoogleSignInButton(
            enabled = !busy && activity != null,
            onClick = {
                busy = true; message = null
                scope.launch { message = sync.signIn(activity!!); busy = false }
            },
            modifier = Modifier.fillMaxWidth()
        )
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
        Text("You can sign in any time in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

private fun relativeTime(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(time))
    }
}
