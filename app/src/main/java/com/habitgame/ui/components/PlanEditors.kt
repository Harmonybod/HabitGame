package com.habitgame.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.core.database.entity.AreaEntity
import com.habitgame.core.database.entity.ChecklistItemEntity
import com.habitgame.domain.habits.HabitTemplates
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.AppIcon

/** An area's colour from its "#RRGGBB" text. */
fun areaColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF7C4DFF))

/** Which area (folder) a habit or task goes in. "" = no area. */
@Composable
fun AreaPicker(areas: List<AreaEntity>, selected: String, onSelect: (String) -> Unit) {
    EditorLabel("📁 Area")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("None", selected.isEmpty()) { onSelect("") }
        // An area typed in for a template that isn't saved yet still shows
        val names = areas.map { it.name }
        val all = if (selected.isNotEmpty() && selected !in names) areas + AreaEntity(selected, HabitTemplates.emojiFor(selected)) else areas
        all.forEach { area -> ChoiceChip("${area.iconEmoji} ${area.name}", area.name == selected) { onSelect(area.name) } }
    }
}

/** The steps of a checklist, edited as a list of lines. Ticking every step completes the task or habit. */
@Composable
fun ChecklistEditor(items: List<String>, onChange: (List<String>) -> Unit) {
    EditorLabel("☑ Checklist")
    Text(
        "Tick all the steps and it's done by itself.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
    )
    items.forEachIndexed { index, text ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> onChange(items.toMutableList().also { it[index] = value }) },
                placeholder = { Text("Step ${index + 1}") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = fieldColors()
            )
            IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(index) }) }) {
                AppIcon("Delete", contentDescription = "Remove step", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    TextButton(onClick = { onChange(items + "") }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text("+ Add a step", fontWeight = FontWeight.SemiBold)
    }
}

/** A checklist under a task or habit: tap a step to tick it. */
@Composable
fun ChecklistSteps(items: List<ChecklistItemEntity>, isChecked: (ChecklistItemEntity) -> Boolean, enabled: Boolean, onToggle: (ChecklistItemEntity) -> Unit) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            val checked = isChecked(item)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, onClickLabel = if (checked) "Untick" else "Tick") { onToggle(item) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .then(
                            if (checked) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    item.text,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** "☑ 2/5" next to a task or habit with a checklist. */
@Composable
fun ChecklistBadge(done: Int, total: Int) {
    val complete = done >= total
    Text(
        "☑ $done/$total",
        color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
    )
}

/** Create an area, or rename / recolour / delete one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AreaSheet(
    initial: AreaEntity?,
    takenNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (AreaEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.iconEmoji ?: "📁") }
    var color by remember { mutableStateOf(initial?.colorHex ?: HabitTemplates.AREA_COLORS.first()) }
    val trimmed = name.trim()
    val clash = trimmed.isNotEmpty() && trimmed != initial?.name && takenNames.any { it.equals(trimmed, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (initial == null) "New area" else "Edit area", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "A folder for habits and tasks that belong together, like a morning routine or a goal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) },
                label = { Text("Area name") }, singleLine = true,
                isError = clash,
                supportingText = if (clash) ({ Text("There's already an area with that name") }) else null,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )
            EditorLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitTemplates.AREA_EMOJI.forEach { e ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (e == emoji) areaColor(color).copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClickLabel = "Use $e") { emoji = e },
                        contentAlignment = Alignment.Center
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
            EditorLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitTemplates.AREA_COLORS.forEach { hex ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(areaColor(hex))
                            .then(if (hex == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable(onClickLabel = "Use this colour") { color = hex }
                    )
                }
            }
            EditorActions(
                saveLabel = if (initial == null) "Create area" else "Save area",
                onSave = {
                    if (trimmed.isEmpty() || clash) return@EditorActions
                    onSave((initial ?: AreaEntity(name = trimmed)).copy(name = trimmed, iconEmoji = emoji, colorHex = color))
                },
                onDelete = onDelete
            )
            if (onDelete != null) {
                Text(
                    "Deleting an area keeps its habits and tasks; they just won't be in a folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
