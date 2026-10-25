package com.habitgame.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.compositionLocalOf
import com.habitgame.core.database.entity.ActivityCategory

/**
 * Central icon provider. All screens should call functions here instead of using
 * Icons.Default.* directly, so that icon packs can swap them out app-wide.
 *
 * Packs: "default" (filled), "elite" (outlined) and "retro" (sharp, square-cornered).
 */
val LocalIconPack = compositionLocalOf { "default" }

object IconProvider {

    /** Returns the nav bar icon for a given route and pack */
    fun navIcon(route: String, pack: String = "default", selected: Boolean = false): ImageVector {
        return when (pack) {
            "elite" -> eliteNavIcon(route, selected)
            "retro" -> retroNavIcon(route)
            else    -> defaultNavIcon(route, selected)
        }
    }

    /** Returns a general-purpose UI icon (add, delete, edit…) for the given pack */
    fun generalIcon(name: String, pack: String = "default"): ImageVector {
        return when (pack) {
            "elite" -> eliteGeneralIcon(name)
            "retro" -> retroGeneralIcon(name)
            else    -> defaultGeneralIcon(name)
        }
    }

    /** Returns the activity category icon for task creation */
    fun activityIcon(category: ActivityCategory, pack: String = "default"): ImageVector {
        return when (pack) {
            "elite" -> eliteActivityIcon(category)
            "retro" -> retroActivityIcon(category)
            else    -> defaultActivityIcon(category)
        }
    }

    // ─── Default pack: filled ───────────────────────────────────────────────────

    private fun defaultNavIcon(route: String, selected: Boolean): ImageVector = when (route) {
        "dashboard" -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        "tasks"     -> if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
        "habits"    -> Icons.Filled.Loop
        "journal"   -> if (selected) Icons.Filled.EditNote else Icons.Outlined.EditNote
        "savings"   -> if (selected) Icons.Filled.Savings else Icons.Outlined.Savings
        "shop"      -> if (selected) Icons.Filled.Store else Icons.Outlined.Store
        "profile"   -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
        else        -> Icons.Filled.Circle
    }

    private fun defaultActivityIcon(category: ActivityCategory): ImageVector = when (category) {
        ActivityCategory.STUDY      -> Icons.Filled.MenuBook
        ActivityCategory.GYM        -> Icons.Filled.FitnessCenter
        ActivityCategory.WORK       -> Icons.Filled.Work
        ActivityCategory.READING    -> Icons.Filled.Book
        ActivityCategory.MEDITATION -> Icons.Filled.SelfImprovement
        ActivityCategory.CODING     -> Icons.Filled.Code
        ActivityCategory.SAVINGS    -> Icons.Filled.Savings
        ActivityCategory.MEAL       -> Icons.Filled.Restaurant
        ActivityCategory.OTHER      -> Icons.Filled.Star
    }

    private fun defaultGeneralIcon(name: String): ImageVector = when (name.lowercase()) {
        "add" -> Icons.Filled.Add
        "delete", "deleteforever" -> Icons.Filled.Delete
        "edit" -> Icons.Filled.Edit
        "warning" -> Icons.Filled.Warning
        "arrowback" -> Icons.Filled.ArrowBack
        "arrowforward" -> Icons.Filled.ArrowForward
        "lock" -> Icons.Filled.Lock
        "checkcircle" -> Icons.Filled.CheckCircle
        "settings" -> Icons.Filled.Settings
        "chevronleft" -> Icons.Filled.ChevronLeft
        "chevronright" -> Icons.Filled.ChevronRight
        "stop" -> Icons.Filled.Stop
        "playarrow" -> Icons.Filled.PlayArrow
        "pause" -> Icons.Filled.Pause
        "expandmore" -> Icons.Filled.ExpandMore
        "expandless" -> Icons.Filled.ExpandLess
        "timer" -> Icons.Filled.Timer
        "calendarmonth" -> Icons.Filled.CalendarMonth
        "locationon" -> Icons.Filled.LocationOn
        "star" -> Icons.Filled.Star
        "place" -> Icons.Filled.Place
        else -> Icons.Filled.Circle
    }

    // ─── Elite pack: outlined ───────────────────────────────────────────────────

    private fun eliteNavIcon(route: String, selected: Boolean): ImageVector = when (route) {
        "dashboard" -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
        "tasks"     -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
        "habits"    -> Icons.Filled.Autorenew
        "journal"   -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
        "savings"   -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
        "shop"      -> if (selected) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag
        "profile"   -> if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
        else        -> Icons.Outlined.Circle
    }

    private fun eliteActivityIcon(category: ActivityCategory): ImageVector = when (category) {
        ActivityCategory.STUDY      -> Icons.Outlined.School
        ActivityCategory.GYM        -> Icons.Outlined.SportsMartialArts
        ActivityCategory.WORK       -> Icons.Outlined.BusinessCenter
        ActivityCategory.READING    -> Icons.Outlined.AutoStories
        ActivityCategory.MEDITATION -> Icons.Outlined.Spa
        ActivityCategory.CODING     -> Icons.Outlined.Terminal
        ActivityCategory.SAVINGS    -> Icons.Outlined.AccountBalanceWallet
        ActivityCategory.MEAL       -> Icons.Outlined.LunchDining
        ActivityCategory.OTHER      -> Icons.Outlined.EmojiEvents
    }

    private fun eliteGeneralIcon(name: String): ImageVector = when (name.lowercase()) {
        "add" -> Icons.Outlined.AddCircleOutline
        "delete", "deleteforever" -> Icons.Outlined.DeleteOutline
        "edit" -> Icons.Outlined.Edit
        "warning" -> Icons.Outlined.WarningAmber
        "arrowback" -> Icons.Outlined.ArrowBackIosNew
        "arrowforward" -> Icons.Outlined.ArrowForwardIos
        "lock" -> Icons.Outlined.Lock
        "checkcircle" -> Icons.Outlined.CheckCircleOutline
        "settings" -> Icons.Outlined.Settings
        "chevronleft" -> Icons.Outlined.ChevronLeft
        "chevronright" -> Icons.Outlined.ChevronRight
        "stop" -> Icons.Outlined.StopCircle
        "playarrow" -> Icons.Outlined.PlayCircle
        "pause" -> Icons.Outlined.PauseCircle
        "expandmore" -> Icons.Outlined.ExpandMore
        "expandless" -> Icons.Outlined.ExpandLess
        "timer" -> Icons.Outlined.Timer
        "calendarmonth" -> Icons.Outlined.CalendarMonth
        "locationon" -> Icons.Outlined.LocationOn
        "star" -> Icons.Outlined.StarOutline
        "place" -> Icons.Outlined.Place
        else -> Icons.Outlined.Circle
    }

    // ─── Retro pack: sharp, square-cornered ─────────────────────────────────────

    private fun retroNavIcon(route: String): ImageVector = when (route) {
        "dashboard" -> Icons.Sharp.GridView
        "tasks"     -> Icons.Sharp.Checklist
        "habits"    -> Icons.Sharp.Refresh
        "journal"   -> Icons.Sharp.Book
        "savings"   -> Icons.Sharp.Savings
        "shop"      -> Icons.Sharp.Storefront
        "profile"   -> Icons.Sharp.Badge
        else        -> Icons.Sharp.Square
    }

    private fun retroActivityIcon(category: ActivityCategory): ImageVector = when (category) {
        ActivityCategory.STUDY      -> Icons.Sharp.School
        ActivityCategory.GYM        -> Icons.Sharp.FitnessCenter
        ActivityCategory.WORK       -> Icons.Sharp.Work
        ActivityCategory.READING    -> Icons.Sharp.MenuBook
        ActivityCategory.MEDITATION -> Icons.Sharp.SelfImprovement
        ActivityCategory.CODING     -> Icons.Sharp.Terminal
        ActivityCategory.SAVINGS    -> Icons.Sharp.Savings
        ActivityCategory.MEAL       -> Icons.Sharp.Fastfood
        ActivityCategory.OTHER      -> Icons.Sharp.VideogameAsset
    }

    private fun retroGeneralIcon(name: String): ImageVector = when (name.lowercase()) {
        "add" -> Icons.Sharp.AddBox
        "delete", "deleteforever" -> Icons.Sharp.Delete
        "edit" -> Icons.Sharp.Edit
        "warning" -> Icons.Sharp.Warning
        "arrowback" -> Icons.Sharp.ArrowBack
        "arrowforward" -> Icons.Sharp.ArrowForward
        "lock" -> Icons.Sharp.Lock
        "checkcircle" -> Icons.Sharp.CheckBox
        "settings" -> Icons.Sharp.Settings
        "chevronleft" -> Icons.Sharp.ChevronLeft
        "chevronright" -> Icons.Sharp.ChevronRight
        "stop" -> Icons.Sharp.Stop
        "playarrow" -> Icons.Sharp.PlayArrow
        "pause" -> Icons.Sharp.Pause
        "expandmore" -> Icons.Sharp.ExpandMore
        "expandless" -> Icons.Sharp.ExpandLess
        "timer" -> Icons.Sharp.HourglassBottom
        "calendarmonth" -> Icons.Sharp.CalendarMonth
        "locationon" -> Icons.Sharp.LocationOn
        "star" -> Icons.Sharp.Star
        "place" -> Icons.Sharp.Place
        else -> Icons.Sharp.Square
    }
}
