package com.habitgame.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.habitgame.core.database.entity.ActivityCategory
import com.habitgame.core.database.entity.RepetitionType
import com.habitgame.core.database.entity.TaskCategoryType
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RepeatMode(val label: String) { ONCE("Once"), REPEAT("Repeats") }

/** Change a task's name, time, repeat days and how long it repeats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(
    task: TaskEntity,
    challengeEnd: LocalDate?,
    deadlineReminders: String = "1 h, 15 min and 5 min before",
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: () -> Unit
) {
    val rule = remember(task.id) { TaskTiming.repeatRule(task) }
    var name by remember(task.id) { mutableStateOf(task.name) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    // A one-time task with no due date can be done any day
    // Tasks from 2.6–2.7 may have only a deadline: that becomes their due day and time
    var hasDueDate by remember(task.id) { mutableStateOf(TaskTiming.assignedDate(task) != null || (!task.isRecurring && task.deadlineAt != null)) }
    var time by remember(task.id) { mutableStateOf(TaskTiming.timeOfDay(task) ?: task.deadlineAt?.let { TaskTiming.localTimeOf(it) }) }
    var mode by remember(task.id) { mutableStateOf(if (task.isRecurring) RepeatMode.REPEAT else RepeatMode.ONCE) }
    var days by remember(task.id) { mutableStateOf(rule.days) }
    var endDate by remember(task.id) { mutableStateOf(rule.endDate) }
    var onceDate by remember(task.id) { mutableStateOf(TaskTiming.scheduledDate(task) ?: task.deadlineAt?.let { TaskTiming.localDateOf(it) } ?: LocalDate.now()) }
    var amountText by remember(task.id) { mutableStateOf(if (task.savingAmount > 0) TaskReminderWorker.formatAmount(task.savingAmount) else "") }
    var focusMinutes by remember(task.id) { mutableIntStateOf(task.focusDurationMinutes) }
    var pickingDate by remember { mutableStateOf(false) }
    val isSavings = task.activityCategory == ActivityCategory.SAVINGS
    // Only activities get a focus timer; savings are just ticked off
    val canFocus = task.categoryType == TaskCategoryType.ACTIVITY && !isSavings

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = onceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onceDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit task", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Task name") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            DescriptionField(description) { description = it }

            if (isSavings) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount to save") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            EditorLabel("🔁 Repeat")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatMode.entries.forEach { m -> ChoiceChip(m.label, mode == m) { mode = m } }
            }

            if (mode == RepeatMode.REPEAT) {
                RepeatDaysEditor(days = days, onDaysChange = { days = it })
                EditorLabel("How long")
                RepeatEndEditor(endDate = endDate, challengeEnd = challengeEnd, onEndChange = { endDate = it })
                EditorLabel("⏰ Time")
                TimeEditor(time = time, onTimeChange = { time = it })
            } else {
                SwitchRow(
                    "📅 Due",
                    if (hasDueDate) "On that day's list and the 100-day board" else "None: do it any day you like",
                    hasDueDate
                ) { hasDueDate = it }
                if (hasDueDate) {
                    OutlinedButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("📅 " + onceDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurface)
                    }
                    EditorLabel("⏰ Time")
                    TimeEditor(time = time, onTimeChange = { time = it })
                }
                if (hasDueDate && time != null) {
                    Text("🔔 Reminders $deadlineReminders.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (canFocus) FocusSessionField(focusMinutes) { focusMinutes = it }

            EditorActions(
                saveLabel = "Save changes",
                onSave = {
                    if (name.isBlank()) return@EditorActions
                    val repeating = mode == RepeatMode.REPEAT
                    // No due date: no day and no time
                    val dated = repeating || hasDueDate
                    val chosenTime = if (dated) time else null
                    val timeMillis = chosenTime?.let { TaskTiming.millisForTime(it) }
                    onSave(
                        task.copy(
                            name = name.trim(),
                            description = description.trim(),
                            isAllDay = chosenTime == null,
                            scheduledTimeMillis = timeMillis,
                            notifyEnabled = chosenTime != null,
                            notifyAtMillis = timeMillis,
                            repetition = if (repeating) RepetitionType.REPEATING else RepetitionType.ONE_TIME,
                            isRecurring = repeating,
                            recurrenceDays = if (repeating) days.joinToString(",") else "",
                            repeatEndDate = if (repeating) endDate?.toString() else null,
                            scheduledDateMillis = if (repeating || !hasDueDate) null else onceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                            savingAmount = if (isSavings) amountText.toDoubleOrNull() ?: 0.0 else task.savingAmount,
                            // A due time on a one-time task is its deadline: reminders before it
                            deadlineAt = if (!repeating && hasDueDate) chosenTime?.let { TaskTiming.millisOf(onceDate.atTime(it)) } else null,
                            focusDurationMinutes = if (canFocus) focusMinutes else task.focusDurationMinutes
                        )
                    )
                },
                onDelete = onDelete
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
