package com.habitgame.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.domain.engine.UserProgressEngine
import com.habitgame.ui.Rank

/** The character card: rank shield, level and title, XP bar, coins and streak. */
class CharacterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetData.user(context)
        provideContent {
            val user = rememberWidgetData(first) { WidgetData.user(context) }
            Content(context, WidgetData.look(user), user)
        }
    }

    @Composable
    private fun Content(context: Context, look: WidgetLook, user: UserEntity) {
        val p = look.palette
        val rank = Rank.forLevel(user.level)
        Row(
            GlanceModifier.fillMaxSize().widgetBackground(look).padding(12.dp).clickable(openApp(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank shield with the level on a tag underneath
            Box(GlanceModifier.size(64.dp), contentAlignment = Alignment.BottomCenter) {
                Image(ImageProvider(rank.icon), contentDescription = "Rank ${rank.label}", modifier = GlanceModifier.size(58.dp))
                Text(
                    "LV ${user.level}",
                    style = textStyle(p.onPrimary, 9.sp, bold = true),
                    modifier = GlanceModifier.background(p.primary.provider()).cornerRadius(6.dp).padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.displayName, maxLines = 1, style = textStyle(p.onSurface, 17.sp, bold = true))
                    if (user.selectedBadge.isNotEmpty()) {
                        Spacer(GlanceModifier.width(4.dp))
                        WidgetEmoji(user.selectedBadge, look, 20.dp)
                    }
                }
                Text("${user.title} · ${rank.label}", maxLines = 1, style = textStyle(p.onSurfaceVariant, 12.sp))
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = WidgetData.xpFraction(user),
                    modifier = GlanceModifier.fillMaxWidth().height(7.dp).cornerRadius(4.dp),
                    color = p.primary.provider(),
                    backgroundColor = p.empty.provider()
                )
                Spacer(GlanceModifier.height(3.dp))
                Text("${user.currentXp} / ${UserProgressEngine.xpRequired(user.level)} XP", style = textStyle(p.onSurfaceVariant, 11.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Stat(look, "🪙", "${user.totalCoins}")
                Spacer(GlanceModifier.height(6.dp))
                Stat(look, "🔥", "${user.currentStreak}")
            }
        }
    }

    @Composable
    private fun Stat(look: WidgetLook, emoji: String, value: String) {
        Row(
            GlanceModifier.widgetCard(look).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetEmoji(emoji, look, 18.dp)
            Spacer(GlanceModifier.width(4.dp))
            Text(value, style = textStyle(look.palette.onSurface, 14.sp, bold = true))
        }
    }
}

class CharacterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CharacterWidget()
}
