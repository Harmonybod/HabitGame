package com.habitgame.domain.restriction

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toBitmap
import com.habitgame.MainActivity
import kotlin.math.abs

/**
 * A small floating bubble shown over other apps while unlock time is being used, or while a
 * focus session runs: the HabitGame icon with a ring around it (in the accent colour) that drains
 * as the time runs out, and the time left underneath. Drag it to move it; tap it to open HabitGame.
 */
class CountdownBubble(
    private val context: Context,
    private val topDp: Float = 160f,
    private val openTarget: String? = null   // screen to open when tapped (see WidgetLaunch)
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null

    /** Show (or update) the bubble. [fractionLeft] goes from 1 to 0; [label] is e.g. "12m"; [ringColor] is an ARGB colour. */
    fun show(fractionLeft: Float, label: String, ringColor: Int) {
        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) return@post
            val bubble = view ?: createView() ?: return@post
            bubble.fractionLeft = fractionLeft
            bubble.label = label
            bubble.ringColor = ringColor
            bubble.invalidate()
        }
    }

    fun hide() {
        mainHandler.post {
            view?.let { runCatching { windowManager.removeView(it) } }
            view = null
        }
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): BubbleView? {
        val bubble = BubbleView(context)
        val lp = WindowManager.LayoutParams(
            dp(64f), dp(86f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12f)
            y = dp(topDp)
        }

        // Drag to move; a tap without moving opens HabitGame
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = event.rawX; touchY = event.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(4f) || abs(dy) > dp(4f)) moved = true
                    lp.x = startX - dx  // gravity END: x grows to the left
                    lp.y = startY + dy
                    runCatching { windowManager.updateViewLayout(bubble, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        context.startActivity(
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                .apply { openTarget?.let { putExtra(com.habitgame.widget.WidgetLaunch.EXTRA, it) } }
                        )
                    }
                    true
                }
                else -> false
            }
        }

        return runCatching {
            windowManager.addView(bubble, lp)
            view = bubble
            params = lp
            bubble
        }.getOrNull()
    }

    private class BubbleView(context: Context) : View(context) {
        var fractionLeft = 1f
        var label = ""

        private val density = context.resources.displayMetrics.density
        private val icon: Bitmap? = runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(128, 128)
        }.getOrNull()

        var ringColor: Int
            get() = ringPaint.color
            set(value) { ringPaint.color = value }
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6141420") }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * density; color = Color.parseColor("#33FFFFFF")
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f * density; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#F5C02E")
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6141420") }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 11f * density; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        private val clip = Path()

        override fun onDraw(canvas: Canvas) {
            val size = width.toFloat()
            val cx = size / 2f
            val cy = size / 2f
            val stroke = ringPaint.strokeWidth
            val radius = size / 2f - stroke / 2f - density

            canvas.drawCircle(cx, cy, radius, backgroundPaint)
            canvas.drawCircle(cx, cy, radius, trackPaint)
            val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arc, -90f, 360f * fractionLeft.coerceIn(0f, 1f), false, ringPaint)

            icon?.let {
                val iconRadius = radius - stroke - 2f * density
                clip.reset()
                clip.addCircle(cx, cy, iconRadius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(it, null, RectF(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius), iconPaint)
                canvas.restore()
            }

            // Minutes left under the circle
            if (label.isNotEmpty()) {
                val textWidth = textPaint.measureText(label)
                val pillTop = size + 2f * density
                val pill = RectF(cx - textWidth / 2 - 6 * density, pillTop, cx + textWidth / 2 + 6 * density, pillTop + 18 * density)
                canvas.drawRoundRect(pill, 9 * density, 9 * density, pillPaint)
                canvas.drawText(label, cx, pill.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
            }
        }
    }
}
