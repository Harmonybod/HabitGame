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
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.domain.notification.TaskReminderWorker

/** The savings jar: saved against the goal, a fill bar, the daily amount still needed, and "+" to add money. */
class SavingsWidget : GlanceAppWidget() {

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
        Column(GlanceModifier.fillMaxSize().widgetBackground(look).padding(12.dp).clickable(openApp(context))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetEmoji("💰", look, 20.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text("Savings", style = textStyle(p.onSurface, 14.sp, bold = true), modifier = GlanceModifier.defaultWeight())
                // Opens HabitGame with the "Add money" box ready
                Text(
                    "+ Add",
                    style = textStyle(p.onPrimary, 13.sp, bold = true),
                    modifier = GlanceModifier
                        .background(p.primary.provider())
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable(openApp(context, WidgetLaunch.SAVINGS_ADD))
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(money(s.saved), style = textStyle(p.onSurface, 24.sp, bold = true))
            Text(
                if (s.goal > 0) "of ${money(s.goal)} · ${(s.fraction * 100).toInt()}%" else "Set a goal in the Savings tab",
                style = textStyle(p.onSurfaceVariant, 12.sp)
            )
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = s.fraction,
                modifier = GlanceModifier.fillMaxWidth().height(10.dp).cornerRadius(5.dp),
                color = p.highlight.provider(),
                backgroundColor = p.empty.provider()
            )
            if (s.goal > 0) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    if (s.saved >= s.goal) "Goal reached 🎉" else "${money(s.perDay)} a day for ${s.daysLeft} days",
                    style = textStyle(p.onSurfaceVariant, 11.sp)
                )
            }
        }
    }
}

class SavingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavingsWidget()
}
