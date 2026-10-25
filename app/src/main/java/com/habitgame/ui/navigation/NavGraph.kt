package com.habitgame.ui.navigation

import com.habitgame.ui.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.habitgame.feature.onboarding.OnboardingScreen
import com.habitgame.feature.calendar.CalendarScreen
import com.habitgame.feature.dashboard.DashboardScreen
import com.habitgame.feature.focus.FocusScreen
import com.habitgame.feature.habits.HabitsScreen
import com.habitgame.feature.journal.JournalScreen
import com.habitgame.feature.profile.ProfileScreen
import com.habitgame.feature.savings.SavingsScreen
import com.habitgame.feature.settings.*
import com.habitgame.feature.shop.ShopScreen
import com.habitgame.feature.tasks.TasksScreen
import com.habitgame.feature.setup.AppSetupScreen
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack

@Composable
fun HabitGameNavGraph(navController: NavHostController) {
    // Back goes to the previous screen; if there is none, to Home
    val goBack: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate("dashboard") { launchSingleTop = true }
        }
    }
    val openSettings: () -> Unit = { navController.navigate("settings") { launchSingleTop = true } }

    // Every screen except Home and Settings: back arrow on the left, settings gear on the right
    @Composable
    fun ScreenFrame(content: @Composable () -> Unit) = WithBackButton(goBack, openSettings, content)

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateFocus = { taskId -> navController.navigate(if (taskId != null) "focus?taskId=$taskId" else "focus") },
                onNavigateCalendar = { navController.navigate("calendar") },
                onNavigateProfile = { navController.navigate("profile") },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }
        composable("tasks") {
            ScreenFrame {
                TasksScreen(onNavigateFocus = { taskId -> navController.navigate("focus?taskId=$taskId") })
            }
        }
        composable("habits") {
            ScreenFrame { HabitsScreen() }
        }
        composable("journal") {
            ScreenFrame { JournalScreen() }
        }
        composable("savings") {
            ScreenFrame { SavingsScreen() }
        }
        composable("shop") {
            ScreenFrame { ShopScreen() }
        }
        composable("profile") {
            ScreenFrame {
                ProfileScreen(onNavigateSettings = { navController.navigate("settings") })
            }
        }
        composable(
            "app_setup?start={start}",
            arguments = listOf(navArgument("start") { type = NavType.StringType; defaultValue = "" })
        ) {
            ScreenFrame {
                AppSetupScreen(onSetupComplete = { navController.popBackStack() })
            }
        }
        // The first-launch guide, replayed from Settings
        composable("onboarding") {
            OnboardingScreen { lockApps ->
                navController.popBackStack()
                if (lockApps) navController.navigate("app_setup?start=guide")
            }
        }
        // Non-bottom-nav routes
        composable("focus?taskId={taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull()
            ScreenFrame { FocusScreen(taskId = taskId) }
        }
        composable("focus") {
            ScreenFrame { FocusScreen(taskId = null) }
        }
        composable("calendar") {
            ScreenFrame { CalendarScreen() }
        }
        // Settings and its pages have their own top bars with a back arrow
        composable("settings") {
            SettingsScreen(onBack = goBack, onOpen = { route -> navController.navigate(route) { launchSingleTop = true } })
        }
        composable(SettingsRoutes.PROFILE) { ProfileEditScreen(onBack = goBack) }
        composable(SettingsRoutes.GENERAL) {
            GeneralSettingsScreen(onBack = goBack, onShowIntro = { navController.navigate("onboarding") })
        }
        composable(SettingsRoutes.DEVICES) { DevicesScreen(onBack = goBack) }
        composable(SettingsRoutes.NOTIFICATIONS) { NotificationSettingsScreen(onBack = goBack) }
        composable(SettingsRoutes.CUSTOMIZATION) { CustomizationScreen(onBack = goBack) }
        composable(SettingsRoutes.APP_LOCK) { AppLockScreen(onBack = goBack) }
        composable(SettingsRoutes.MANUAL_SYNC) { ManualSyncScreen(onBack = goBack) }
    }
}

/** Puts a back arrow in the top-left corner and a settings gear in the top-right corner above a screen. */
@Composable
private fun WithBackButton(onBack: () -> Unit, onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                AppIcon(
                    "ArrowBack",
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                AppIcon(
                    "Settings",
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
