package com.habitgame

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.habitgame.feature.settings.SettingsViewModel
import com.habitgame.domain.location.LocationTrackingService
import android.content.Intent
import com.habitgame.ui.navigation.HabitGameBottomBar
import com.habitgame.ui.navigation.HabitGameNavGraph
import com.habitgame.ui.theme.*
import com.habitgame.ui.LocalIconPack
import com.habitgame.feature.onboarding.OnboardingPrefs
import com.habitgame.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var appRestrictionManager: com.habitgame.domain.restriction.AppRestrictionManager
    @javax.inject.Inject lateinit var cloudSync: com.habitgame.domain.sync.CloudSync
    @javax.inject.Inject lateinit var focusTimer: com.habitgame.domain.focus.FocusTimer

    override fun onResume() {
        super.onResume()
        // A focus session left running (e.g. the phone restarted): its timer service picks it up again
        if (focusTimer.state.value.run != null) com.habitgame.domain.focus.FocusTimerService.start(this)
        // Settings → Devices shows when the app was last open on each phone
        cloudSync.touchDevice()
        // Keep the app lock running whenever there are locked apps (Android may have stopped it)
        lifecycleScope.launch { appRestrictionManager.startMonitorIfNeeded() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        rememberWidgetTarget(intent)
    }

    /** A widget button (e.g. Savings "+ Add") asked to open a particular screen. */
    private fun rememberWidgetTarget(intent: Intent?) {
        // A song or album shared from Spotify, YouTube Music, Apple Music…
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { com.habitgame.domain.music.SharedMusic.pending.value = it }
        }
        intent?.getStringExtra(com.habitgame.widget.WidgetLaunch.EXTRA)?.let { com.habitgame.widget.WidgetLaunch.pending.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rememberWidgetTarget(intent)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.state.collectAsState()
            val accentColor = remember(settingsState.user.accentColorHex) {
                val hex = settingsState.user.accentColorHex
                if (hex.isNotBlank()) {
                    try {
                        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
                    } catch (_: Exception) { null }
                } else null
            }
            // The blurred photo behind the app while Glass is on
            val backgroundPhoto = com.habitgame.ui.theme.rememberBackgroundPhoto(settingsState.user.backgroundImagePath)
            HabitGameTheme(
                themeName = settingsState.user.selectedTheme,
                accentColor = accentColor,
                glass = settingsState.user.glassEnabled,
                backgroundImage = backgroundPhoto?.first,
                backgroundTint = backgroundPhoto?.second
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                var onboardingDone by remember { mutableStateOf(OnboardingPrefs.isDone(context)) }
                var openLockSetup by remember { mutableStateOf(false) }
                // After the intro: the "Sign in with Google / Skip" step (only when sync is set up)
                var askSignIn by remember { mutableStateOf(false) }
                val sync = com.habitgame.ui.components.rememberCloudSync()
                val syncState by sync.state.collectAsState()

                if (askSignIn) {
                    com.habitgame.ui.components.SignInStep(onDone = { askSignIn = false })
                } else if (!onboardingDone) {
                    // First launch: the step-by-step guide comes before anything else
                    OnboardingScreen { lockApps ->
                        OnboardingPrefs.setDone(context)
                        openLockSetup = lockApps
                        onboardingDone = true
                        askSignIn = syncState.configured && !syncState.signedIn
                    }
                } else {
                    PermissionGate {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalIconPack provides settingsState.user.selectedIconPack
                        ) {
                            HabitGameContent(
                                iconPack = settingsState.user.selectedIconPack,
                                openLockSetup = openLockSetup,
                                onLockSetupOpened = { openLockSetup = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    if (permissionState.allPermissionsGranted) {
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        content()
    } else {
        LaunchedEffect(Unit) {
            permissionState.launchMultiplePermissionRequest()
        }

        // Show a nice UI while waiting for permissions
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Text("🎮", fontSize = 64.sp)
                Text(
                    "HabitGame",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "We need a few permissions to unlock all features:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text("📍 Location — for location-based tasks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("🔔 Notifications — for habit reminders", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { permissionState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) {
                    Text("Grant Permissions", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { /* Allow skip — will show content anyway on next recomposition */ }) {
                    Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // After a brief delay, show content anyway (some permissions might be optional)
        var showContent by remember { mutableStateOf(false) }
        LaunchedEffect(permissionState.shouldShowRationale, permissionState.allPermissionsGranted) {
            if (!permissionState.allPermissionsGranted) {
                kotlinx.coroutines.delay(3000)
                showContent = true
            }
        }
        if (showContent) {
            content()
        }
    }
}

@Composable
fun HabitGameContent(
    iconPack: String = "default",
    openLockSetup: Boolean = false,
    onLockSetupOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Coming from the guide's "Choose apps to lock"
    LaunchedEffect(openLockSetup) {
        if (openLockSetup) {
            navController.navigate("app_setup?start=guide")
            onLockSetupOpened()
        }
    }
    // Savings widget "+ Add": go to Savings, where the Add money box opens
    val widgetTarget by com.habitgame.widget.WidgetLaunch.pending.collectAsState()
    val sharedMusic by com.habitgame.domain.music.SharedMusic.pending.collectAsState()
    LaunchedEffect(sharedMusic) {
        if (sharedMusic != null) navController.navigate("journal") { launchSingleTop = true }
    }
    LaunchedEffect(widgetTarget) {
        when (widgetTarget) {
            com.habitgame.widget.WidgetLaunch.SAVINGS_ADD -> navController.navigate("savings") { launchSingleTop = true }
            // The focus timer's notification or bubble was tapped
            com.habitgame.widget.WidgetLaunch.FOCUS -> {
                com.habitgame.widget.WidgetLaunch.consume(com.habitgame.widget.WidgetLaunch.FOCUS)
                navController.navigate("focus") { launchSingleTop = true }
            }
        }
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { HabitGameBottomBar(navController, iconPack = iconPack) }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            HabitGameNavGraph(navController)
        }
    }
}
