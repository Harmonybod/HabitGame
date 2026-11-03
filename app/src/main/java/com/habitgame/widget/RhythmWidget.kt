package com.habitgame.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.domain.tracker.HabitRate
import kotlin.math.roundToInt

/** Weekly rhythm, as on the Progress tab: how often habits get done on each weekday, the best day brightest. */
class RhythmWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        suspend fun load() = WidgetData.look(WidgetData.user(context)) to WidgetData.rhythm(context)
        val first = load()
        provideContent {
            val (look, rates) = rememberWidgetData(first) { load() }
            Content(context, look, rates)
        }
    }

    @Composable
    private fun Content(context: Context, look: WidgetLook, rates: List<HabitRate>) {
        val p = look.palette
        val size = LocalSize.current
        val letters = listOf("M", "T", "W", "T", "F", "S", "S")
        val best = rates.mapNotNull { it.percent }.maxOrNull()
        // Room for the bars: the widget minus the header, the % and the day letters
        val barRoom = (size.height.value - 24f - 30f - 34f).coerceIn(20f, 140f)
        Column(GlanceModifier.fillMaxSize().widgetBackground(look).padding(12.dp).clickable(openApp(context))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetEmoji("📊", look, 18.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text("Weekly rhythm", style = textStyle(p.onSurface, 14.sp, bold = true), modifier = GlanceModifier.defaultWeight())
                Text("last ${WidgetData.RHYTHM_DAYS} days", style = textStyle(p.onSurfaceVariant, 11.sp))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (rates.all { it.percent == null }) {
                Text("Tick some habits and your best days show up here.", style = textStyle(p.onSurfaceVariant, 12.sp))
            } else {
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
                    rates.forEachIndexed { i, rate ->
                        val pct = rate.percent
                        Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.Bottom) {
                            Text(pct?.let { "${it.roundToInt()}%" } ?: "–", style = textStyle(p.onSurfaceVariant, 9.sp))
                            Spacer(GlanceModifier.height(2.dp))
                            Box(
                                GlanceModifier
                                    .width(14.dp)
                                    .height((barRoom * ((pct ?: 0f) / 100f)).coerceAtLeast(3f).dp)
                                    .cornerRadius(4.dp)
                                    .background((if (pct != null && pct == best) p.highlight else p.empty).provider())
                            ) {}
                            Spacer(GlanceModifier.height(3.dp))
                            Text(letters[i], style = textStyle(p.onSurfaceVariant, 11.sp, bold = pct != null && pct == best))
                        }
                    }
                }
            }
        }
    }
}

class RhythmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RhythmWidget()
}
