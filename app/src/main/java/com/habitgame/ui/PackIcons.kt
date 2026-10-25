package com.habitgame.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.R
import com.habitgame.core.database.entity.ActivityCategory

/**
 * The "Game" icon pack: full-colour picture icons. Where it has a picture for an icon it's
 * used instead of the plain icon; anything it doesn't cover (arrows, play/stop) keeps the
 * normal icon.
 */
object GameIconPack {
    const val KEY = "game"

    @DrawableRes
    fun nav(route: String): Int? = when (route) {
        "dashboard" -> R.drawable.gp_home
        "tasks" -> R.drawable.gp_tasks
        "habits" -> R.drawable.gp_habits
        "journal" -> R.drawable.gp_journal
        "savings" -> R.drawable.gp_savings
        "shop" -> R.drawable.gp_shop
        "profile" -> R.drawable.gp_profile
        else -> null
    }

    @DrawableRes
    fun general(name: String): Int? = when (name.lowercase()) {
        "add" -> R.drawable.gp_add
        "delete", "deleteforever" -> R.drawable.gp_delete
        "edit" -> R.drawable.gp_edit
        "warning" -> R.drawable.gp_warning
        "lock" -> R.drawable.gp_lock
        "checkcircle" -> R.drawable.gp_check
        "settings" -> R.drawable.gp_settings
        "timer" -> R.drawable.gp_timer
        "calendarmonth" -> R.drawable.gp_calendar
        "locationon", "place" -> R.drawable.gp_location
        "star" -> R.drawable.gp_star
        "info" -> R.drawable.gp_info
        else -> null
    }

    @DrawableRes
    fun activity(category: ActivityCategory): Int = when (category) {
        ActivityCategory.STUDY -> R.drawable.gp_scroll
        ActivityCategory.GYM -> R.drawable.gp_arm
        ActivityCategory.WORK -> R.drawable.gp_backpack
        ActivityCategory.READING -> R.drawable.gp_book
        ActivityCategory.MEDITATION -> R.drawable.gp_leaf
        ActivityCategory.CODING -> R.drawable.gp_codes
        ActivityCategory.SAVINGS -> R.drawable.gp_cash
        ActivityCategory.MEAL -> R.drawable.gp_burger
        ActivityCategory.OTHER -> R.drawable.gp_star
    }

    /** Pictures that stand in for the emoji used around the app (coins, streak fire, combo…). */
    @DrawableRes
    fun emoji(emoji: String): Int? = when (emoji) {
        "🪙" -> R.drawable.gp_coin
        "🔥" -> R.drawable.gp_fire
        "⚡" -> R.drawable.gp_bolt
        "⭐" -> R.drawable.gp_star
        "🏆" -> R.drawable.gp_trophy
        "🏅" -> R.drawable.gp_medal
        "🔒" -> R.drawable.gp_lock
        "📅" -> R.drawable.gp_calendar
        "🎁" -> R.drawable.gp_gift
        "🎯" -> R.drawable.gp_target
        "💰" -> R.drawable.gp_cash
        "📱" -> R.drawable.gp_key
        "🎬" -> R.drawable.gp_music
        "🎨" -> R.drawable.gp_crown
        "🖼️" -> R.drawable.gp_crown
        "⏳" -> R.drawable.gp_timer
        "🚀" -> R.drawable.gp_rocket
        "📊" -> R.drawable.gp_stats
        else -> null
    }
}

/** A named UI icon ("Add", "Delete", "Settings"…) in the current icon pack. */
@Composable
fun AppIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val pack = LocalIconPack.current
    val picture = if (pack == GameIconPack.KEY) GameIconPack.general(name) else null
    if (picture != null) {
        Icon(painterResource(picture), contentDescription, modifier, tint = Color.Unspecified)
    } else {
        Icon(IconProvider.generalIcon(name, pack), contentDescription, modifier, tint = tint)
    }
}

/** A bottom-bar tab icon in the current icon pack. */
@Composable
fun NavIcon(route: String, selected: Boolean, contentDescription: String?) {
    val pack = LocalIconPack.current
    val picture = if (pack == GameIconPack.KEY) GameIconPack.nav(route) else null
    if (picture != null) {
        Icon(painterResource(picture), contentDescription, Modifier.size(if (selected) 28.dp else 24.dp), tint = Color.Unspecified)
    } else {
        Icon(IconProvider.navIcon(route, pack, selected), contentDescription)
    }
}

/** A task's activity icon (gym, reading, coding…) in the current icon pack. */
@Composable
fun ActivityIcon(category: ActivityCategory, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val pack = LocalIconPack.current
    if (pack == GameIconPack.KEY) {
        Icon(painterResource(GameIconPack.activity(category)), null, modifier, tint = Color.Unspecified)
    } else {
        Icon(IconProvider.activityIcon(category, pack), null, modifier, tint = tint)
    }
}

/** An emoji such as 🪙 or 🔥, shown as the Game pack's picture when that pack is on. */
@Composable
fun PackEmoji(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    val picture = if (LocalIconPack.current == GameIconPack.KEY) GameIconPack.emoji(emoji) else null
    if (picture != null) {
        Image(painterResource(picture), contentDescription = null, modifier = modifier.size(size))
    } else {
        Text(emoji, fontSize = (size.value * 0.8f).sp, modifier = modifier)
    }
}
