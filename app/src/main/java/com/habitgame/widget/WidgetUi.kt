package com.habitgame.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.habitgame.MainActivity
import com.habitgame.ui.GameIconPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

fun Color.provider() = ColorProvider(this)

/** The widget's outer box: rounded, in the theme's colours (see-through on Glass). */
fun GlanceModifier.widgetBackground(look: WidgetLook): GlanceModifier =
    background(look.palette.background.provider()).cornerRadius(22.dp)

/** A rounded inner panel, like a card in the app. */
fun GlanceModifier.widgetCard(look: WidgetLook): GlanceModifier =
    background(look.palette.card.provider()).cornerRadius(14.dp)

fun textStyle(color: Color, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = color.provider(), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** An emoji, or the Game icon pack's picture for it when that pack is on. */
@Composable
fun WidgetEmoji(emoji: String, look: WidgetLook, size: Dp) {
    val picture = if (look.iconPack == GameIconPack.KEY) GameIconPack.emoji(emoji) else null
    if (picture != null) {
        Image(ImageProvider(picture), contentDescription = null, modifier = GlanceModifier.size(size))
    } else {
        Text(emoji, style = TextStyle(fontSize = (size.value * 0.8f).sp))
    }
}

/** Opens HabitGame, optionally straight to a screen or action (see [WidgetLaunch]). */
fun openApp(context: Context, target: String? = null): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (target != null) putExtra(WidgetLaunch.EXTRA, target) }
    )

/** Redraws every HabitGame widget. */
object WidgetUpdater {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Goes up on every change; widgets that are already showing reload their data when it does. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0L)

    suspend fun updateAllNow(context: Context) {
        version.value++
        ChecklistWidget().updateAll(context)
        CharacterWidget().updateAll(context)
        BoardWidget().updateAll(context)
        SavingsWidget().updateAll(context)
    }

    /** Ask for a redraw; bursts of changes (e.g. finishing a task) become one redraw. */
    fun requestUpdate() {
        changes.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope) {
        scope.launch {
            changes.debounce(700).collect { runCatching { updateAllNow(context) } }
        }
    }
}

/**
 * A widget's data: [initial] for the first draw, then loaded again by [load] whenever
 * HabitGame's data changes while the widget is showing.
 */
@Composable
fun <T> rememberWidgetData(initial: T, load: suspend () -> T): T {
    val version by WidgetUpdater.version.collectAsState()
    val data by produceState(initial, version) { value = load() }
    return data
}

/** What a widget asked the app to open, e.g. the Savings "Add money" box. */
object WidgetLaunch {
    const val EXTRA = "habitgame.widget.open"
    const val SAVINGS_ADD = "savings_add"
    const val FOCUS = "focus"          // the focus timer's notification and bubble

    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun consume(target: String): Boolean {
        if (pending.value != target) return false
        pending.value = null
        return true
    }
}
