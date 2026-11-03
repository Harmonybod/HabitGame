package com.habitgame.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.domain.notification.TaskReminderWorker

/** A small (2×1) savings strip: saved so far, the goal and a fill bar. Tap to add money. */
class SavingsMiniWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        suspend fun load() = WidgetData.look(WidgetData.user(context)) to WidgetData.savings(context)
        val first = load()
        provideContent {
            val (look, savings) = rememberWidgetData(first) { load() }
            Content(context, look, savings)
        }
    }

    @Composable
    private fun Content(context: Context, look: WidgetLook, s: WidgetData.Savings) {
        val p = look.palette
        fun money(amount: Double) = s.currency + TaskReminderWorker.formatAmount(Math.round(amount * 100) / 100.0)
        Column(
            GlanceModifier.fillMaxSize().widgetBackground(look).padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(openApp(context, WidgetLaunch.SAVINGS_ADD)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetEmoji("💰", look, 18.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(money(s.saved), maxLines = 1, style = textStyle(p.onSurface, 16.sp, bold = true), modifier = GlanceModifier.defaultWeight())
                Text(
                    if (s.goal > 0) "${(s.fraction * 100).toInt()}%" else "Set goal",
                    maxLines = 1, style = textStyle(p.onSurfaceVariant, 12.sp, bold = true)
                )
            }
            Spacer(GlanceModifier.height(5.dp))
            LinearProgressIndicator(
                progress = s.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = p.highlight.provider(),
                backgroundColor = p.empty.provider()
            )
        }
    }
}

class SavingsMiniWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavingsMiniWidget()
}
