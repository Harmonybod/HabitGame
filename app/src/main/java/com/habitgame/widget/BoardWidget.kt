package com.habitgame.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.ui.theme.WidgetPalette

/** The 100-day board, like on Home: each circle fills as that day's checklist gets done. */
class BoardWidget : GlanceAppWidget() {

    // Drawn at the widget's real size, so the circles stay sharp however it's resized
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        suspend fun load() = WidgetData.look(WidgetData.user(context)) to WidgetData.board(context)
        val first = load()
        provideContent {
            val (look, board) = rememberWidgetData(first) { load() }
            Content(context, look, board)
        }
    }

    @Composable
    private fun Content(context: Context, look: WidgetLook, board: WidgetData.Board) {
        val p = look.palette
        val size = LocalSize.current
        val density = context.resources.displayMetrics.density
        // Room left for the board under the one-line header
        val side = minOf(size.width.value - 24f, size.height.value - 24f - 30f).coerceAtLeast(60f)
        val wide = size.width.value >= 230f
        val px = (side * density).toInt().coerceAtMost(900)
        val bitmap = remember(board, px, p) { drawBoard(board, p, px, density) }

        Column(
            GlanceModifier.fillMaxSize().widgetBackground(look).padding(12.dp).clickable(openApp(context)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Day ${board.currentDay.coerceAtMost(TrackerPlan.TOTAL_DAYS)}", maxLines = 1, style = textStyle(p.onSurface, 16.sp, bold = true))
                Text(" of 100", maxLines = 1, style = textStyle(p.onSurfaceVariant, 13.sp, bold = true), modifier = GlanceModifier.defaultWeight())
                // The phase ("Days 1–10: Show up") only when there's room for it
                if (wide) Text(board.phaseName, maxLines = 1, style = textStyle(p.onSurfaceVariant, 11.sp))
            }
            Spacer(GlanceModifier.height(6.dp))
            Image(
                ImageProvider(bitmap),
                contentDescription = "100-day board. Day ${board.currentDay}.",
                modifier = GlanceModifier.size(side.dp)
            )
        }
    }

    /** 10×10 circles: filled from the bottom up by how much of that day got done; today has a ring. */
    private fun drawBoard(board: WidgetData.Board, p: WidgetPalette, sizePx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gap = 4f * density
        val cell = (sizePx - gap * 9) / 10f
        val radius = cell / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.highlight.toArgb() }
        val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.empty.toArgb() }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.onSurface.copy(alpha = 0.55f).toArgb() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * density; color = p.onSurface.toArgb()
        }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f * density; color = android.graphics.Color.argb(90, 255, 255, 255)
        }
        for (i in 0 until TrackerPlan.TOTAL_DAYS) {
            val day = i + 1
            val cx = (i % 10) * (cell + gap) + radius
            val cy = (i / 10) * (cell + gap) + radius
            val score = board.scores.getOrElse(i) { 0f }
            val alpha = if (day > board.currentDay) 115 else 255
            fill.alpha = alpha; empty.alpha = (p.empty.alpha * alpha).toInt(); dot.alpha = (140 * alpha / 255)
            if (score >= 1f) {
                canvas.drawCircle(cx, cy, radius, fill)
            } else {
                canvas.drawCircle(cx, cy, radius, empty)
                if (score > 0f) {
                    canvas.save()
                    canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
                    canvas.drawRect(cx - radius, cy + radius - cell * score, cx + radius, cy + radius, fill)
                    canvas.restore()
                }
                if (day in TrackerPlan.MILESTONES && score == 0f) canvas.drawCircle(cx, cy, radius * 0.24f, dot)
            }
            if (p.glass) {
                // Glass: a light rim and a soft shine, like a bead
                canvas.drawCircle(cx, cy, radius - density * 0.5f, rim)
                val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx - radius * 0.3f, cy - radius * 0.35f, radius * 0.6f,
                        android.graphics.Color.argb(110, 255, 255, 255), android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(cx, cy, radius, shine)
            }
            if (day == board.currentDay) canvas.drawCircle(cx, cy, radius - ring.strokeWidth / 2f, ring)
        }
        return bitmap
    }
}

class BoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoardWidget()
}
