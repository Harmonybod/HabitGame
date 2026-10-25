package com.habitgame.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitgame.domain.tracker.TaskTiming
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared pieces for editing when a task or habit happens: time, repeat days and how long it repeats. */

@Composable
fun EditorLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = chipColors())
}

/** Time of day with an on/off switch. Off = no set time and no reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEditor(
    time: LocalTime?,
    onTimeChange: (LocalTime?) -> Unit,
    switchLabel: String = "Set a time & remind me"
) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        val state = rememberTimePickerState(initialHour = time?.hour ?: 9, initialMinute = time?.minute ?: 0)
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Select time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { onTimeChange(LocalTime.of(state.hour, state.minute)); picking = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(switchLabel, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = time != null,
            onCheckedChange = { on -> onTimeChange(if (on) (time ?: LocalTime.of(9, 0)) else null) }
        )
    }
    if (time != null) {
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text("⏰ " + TaskTiming.formatTime(time), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Every day, or specific weekdays. An empty set means every day. */
@Composable
fun RepeatDaysEditor(days: Set<String>, onDaysChange: (Set<String>) -> Unit) {
    var specific by remember { mutableStateOf(days.isNotEmpty() && days.size < 7) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Every day", !specific) { specific = false; onDaysChange(emptySet()) }
        ChoiceChip("Specific days", specific) {
            specific = true
            if (days.isEmpty()) onDaysChange(setOf(TaskTiming.dayAbbrev(LocalDate.now().dayOfWeek)))
        }
    }
    if (specific) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TaskTiming.WEEK.forEach { day ->
                val on = day in days
                FilledTonalIconToggleButton(
                    checked = on,
                    onCheckedChange = {
                        val next = if (on) days - day else days + day
                        if (next.isNotEmpty()) onDaysChange(next)
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(day.take(2).lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private enum class RepeatLength(val label: String) {
    NO_END("No end"), WEEK("1 week"), MONTH("30 days"), CHALLENGE("Until day 100"), DATE("Pick a date")
}

/** How long something keeps repeating. null = no end. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatEndEditor(endDate: LocalDate?, challengeEnd: LocalDate?, onEndChange: (LocalDate?) -> Unit) {
    val today = LocalDate.now()
    var choice by remember {
        mutableStateOf(
            when (endDate) {
                null -> RepeatLength.NO_END
                challengeEnd -> RepeatLength.CHALLENGE
                else -> RepeatLength.DATE
            }
        )
    }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: today.plusDays(13)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onEndChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RepeatLength.entries
            .filter { it != RepeatLength.CHALLENGE || challengeEnd != null }
            .forEach { option ->
                ChoiceChip(option.label, choice == option) {
                    choice = option
                    when (option) {
                        RepeatLength.NO_END -> onEndChange(null)
                        RepeatLength.WEEK -> onEndChange(today.plusDays(6))
                        RepeatLength.MONTH -> onEndChange(today.plusDays(29))
                        RepeatLength.CHALLENGE -> onEndChange(challengeEnd)
                        RepeatLength.DATE -> picking = true
                    }
                }
            }
    }
    Text(
        endDate?.let {
            val label = it.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
            if (it.isBefore(today)) "Ended on $label" else "Repeats until $label"
        } ?: "Repeats with no end date",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Bottom row of an editor sheet: delete on the left, save on the right. */
@Composable
fun EditorActions(saveLabel: String, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete this?") },
            text = { Text("Its reminders will stop. This can't be undone.", textAlign = TextAlign.Start) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onDelete != null) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text(saveLabel, fontWeight = FontWeight.Bold) }
    }
}
