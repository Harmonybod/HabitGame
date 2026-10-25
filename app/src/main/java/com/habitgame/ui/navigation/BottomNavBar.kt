package com.habitgame.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.dp
import com.habitgame.ui.IconProvider

@Composable
fun HabitGameBottomBar(navController: NavHostController, iconPack: String = "default") {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    // Profile opens from the avatar on Home, leaving room for Journal and Savings
    val routes = listOf(
        Triple("dashboard", "Home",    "dashboard"),
        Triple("tasks",     "Tasks",   "tasks"),
        Triple("habits",    "Habits",  "habits"),
        Triple("journal",   "Journal", "journal"),
        Triple("notes",     "Notes",   "notes"),
        Triple("savings",   "Savings", "savings"),
        Triple("shop",      "Shop",    "shop")
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        routes.forEach { (route, label, _) ->
            val isSelected = currentDest?.hierarchy?.any { it.route == route } == true
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (route == "dashboard") {
                        // Home always means Home: drop Settings, Profile or anything else on top of it
                        navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { com.habitgame.ui.NavIcon(route, isSelected, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = MaterialTheme.colorScheme.primary,
                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                    indicatorColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
            )
        }
    }
}
