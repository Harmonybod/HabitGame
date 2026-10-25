package com.habitgame.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.R
import com.habitgame.domain.tracker.TaskTiming

/**
 * Today's tasks and habits. Tap a circle to tick one off (XP and coins are paid out as in
 * the app; a habit can be unticked again). Tap the header to open HabitGame.
 */
class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        suspend fun load() = WidgetData.look(WidgetData.user(context)) to WidgetData.checklist(context)
        val first = load()
        provideContent {
            val (look, items) = rememberWidgetData(first) { load() }
            Content(context, look, items)
        }
    }

    @Composable
    private fun Content(context: Context, look: WidgetLook, items: List<ChecklistItem>) {
        val p = look.palette
        val done = items.count { it.done }
        Column(GlanceModifier.fillMaxSize().widgetBackground(look).padding(12.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().clickable(openApp(context)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today", style = textStyle(p.onSurface, 16.sp, bold = true), modifier = GlanceModifier.defaultWeight())
                Text(
                    if (items.isEmpty()) "" else "$done of ${items.size} done",
                    style = textStyle(p.onSurfaceVariant, 12.sp)
                )
            }
            if (items.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = done.toFloat() / items.size,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                    color = p.highlight.provider(),
                    backgroundColor = p.empty.provider()
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (items.isEmpty()) {
                Box(GlanceModifier.fillMaxSize().clickable(openApp(context)), contentAlignment = Alignment.Center) {
                    Text("Nothing due today 🎉", style = textStyle(p.onSurfaceVariant, 13.sp))
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(items, itemId = { (if (it.isHabit) 1_000_000_000L else 0L) + it.id }) { item ->
                        Column {
                            ItemRow(look, item)
                            Spacer(GlanceModifier.height(5.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ItemRow(look: WidgetLook, item: ChecklistItem) {
        val p = look.palette
        val toggle = actionRunCallback<ToggleItemAction>(
            actionParametersOf(ToggleItemAction.ID to item.id, ToggleItemAction.HABIT to item.isHabit)
        )
        Row(
            GlanceModifier.fillMaxWidth().widgetCard(look).padding(horizontal = 10.dp, vertical = 8.dp).clickable(toggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                ImageProvider(if (item.done) R.drawable.widget_check_on else R.drawable.widget_check_off),
                contentDescription = if (item.done) "Done" else "Tick off ${item.name}",
                colorFilter = ColorFilter.tint((if (item.done) p.highlight else p.onSurfaceVariant).provider()),
                modifier = GlanceModifier.size(22.dp)
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                (if (item.isHabit) "🔄 " else "") + item.name,
                maxLines = 1,
                style = textStyle(if (item.done) p.onSurfaceVariant else p.onSurface, 14.sp, bold = !item.done),
                modifier = GlanceModifier.defaultWeight()
            )
            item.time?.let {
                Spacer(GlanceModifier.width(6.dp))
                Text(TaskTiming.formatTime(it), style = textStyle(p.onSurfaceVariant, 11.sp))
            }
        }
    }
}

/** Ticks (or, for a habit, unticks) one checklist line, then redraws the widgets. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        WidgetData.toggle(context, id, parameters[HABIT] ?: false)
        WidgetUpdater.updateAllNow(context)
    }

    companion object {
        val ID = ActionParameters.Key<Long>("item_id")
        val HABIT = ActionParameters.Key<Boolean>("is_habit")
    }
}

class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}
