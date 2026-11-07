package com.habitgame.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Which checklists are open on Home and the Habits page. Each one stays as it was left; ones never
 * opened or closed follow the last choice made (closed until the first one).
 */
class ChecklistFolds(private val prefs: SharedPreferences) {
    private val open = mutableStateMapOf<String, Boolean>().apply {
        prefs.all.forEach { (k, v) -> if (k.startsWith(PREFIX) && v is Boolean) put(k.removePrefix(PREFIX), v) }
    }
    private var last by mutableStateOf(prefs.getBoolean(KEY_LAST, false))

    /** [key] is the checklist's owner, e.g. "task:12" or "habit:3". */
    fun isOpen(key: String): Boolean = open[key] ?: last

    fun toggle(key: String) {
        val now = !isOpen(key)
        open[key] = now
        last = now
        prefs.edit().putBoolean(PREFIX + key, now).putBoolean(KEY_LAST, now).apply()
    }

    companion object {
        private const val FILE = "checklist_folds"
        private const val PREFIX = "open:"
        private const val KEY_LAST = "last"

        fun from(context: Context) = ChecklistFolds(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

@Composable
fun rememberChecklistFolds(): ChecklistFolds {
    val context = LocalContext.current
    return remember { ChecklistFolds.from(context) }
}

/** The button that stands in for the tick on something with a checklist: "2/5" and an arrow to open or close the steps. */
@Composable
fun StepsToggle(done: Int, total: Int, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(if (open) 0f else 180f, label = "steps arrow")
    val complete = done >= total
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (open) 0.28f else 0.16f))
            .clickable(onClickLabel = if (open) "Hide checklist" else "Show checklist", onClick = onClick)
            .padding(start = 6.dp, end = 9.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).rotate(turn)
        )
        Text(
            "$done/$total", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
