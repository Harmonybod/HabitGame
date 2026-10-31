package com.habitgame.feature.tasks

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.*
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.*

/**
 * A one-time task due at a set time: that moment is its deadline, which drives the reminders before it.
 * [dateMillis] is the date picker's UTC midnight (null = today); [timeMillis] a time of day.
 */
internal fun dueDeadline(due: Boolean, dateMillis: Long?, timeMillis: Long?): Long? {
    if (!due || timeMillis == null) return null
    val date = dateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() } ?: java.time.LocalDate.now()
    return com.habitgame.domain.tracker.TaskTiming.millisOf(date.atTime(com.habitgame.domain.tracker.TaskTiming.localTimeOf(timeMillis)))
}

private fun activityIcon(category: ActivityCategory): String = when (category) {
    ActivityCategory.STUDY      -> "📚"
    ActivityCategory.GYM        -> "🏋️"
    ActivityCategory.WORK       -> "💼"
    ActivityCategory.READING    -> "📖"
    ActivityCategory.MEDITATION -> "🧘"
    ActivityCategory.CODING     -> "💻"
    ActivityCategory.MEAL       -> "🍽️"
    ActivityCategory.SAVINGS    -> "💰"
    ActivityCategory.OTHER      -> "🎯"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    currency: String = "",
    deadlineReminders: String = "1 h, 15 min and 5 min before",
    startAsShopping: Boolean = false,
    areas: List<AreaEntity> = emptyList(),
    initialArea: String = "",
    onDismiss: () -> Unit,
    onAdd: (TaskEntity, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf(initialArea) }
    var checklist by remember { mutableStateOf(emptyList<String>()) }
    var description by remember { mutableStateOf("") }
    // Off: a one-time task with no day, done whenever (it then doesn't count on the 100-day board)
    var hasDueDate by remember { mutableStateOf(false) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var repetition by remember { mutableStateOf(RepetitionType.ONE_TIME) }
    var categoryType by remember { mutableStateOf(if (startAsShopping) TaskCategoryType.SHOPPING else TaskCategoryType.ACTIVITY) }
    val shoppingForm = remember { ShoppingForm() }
    val shopping = categoryType == TaskCategoryType.SHOPPING
    var activityCategory by remember { mutableStateOf(ActivityCategory.OTHER) }
    var isAllDay by remember { mutableStateOf(true) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }
    var selectedLocationZoneId by remember { mutableStateOf<Long?>(null) }
    var selectedLocationName by remember { mutableStateOf<String?>(null) }
    // Fetch available zones from viewmodel
    val tasksViewModel = hiltViewModel<TasksViewModel>()
    val locationZones by tasksViewModel.locationZones.collectAsState()

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0)
    val challengeEnd by tasksViewModel.challengeEnd.collectAsState()
    var repeatDays by remember { mutableStateOf(emptySet<String>()) }
    var repeatEnd by remember { mutableStateOf<java.time.LocalDate?>(null) }
    // Every activity except meals gets a focus timer
    var focusMinutes by remember { mutableIntStateOf(DEFAULT_FOCUS_MINUTES) }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time", color = MaterialTheme.colorScheme.onSurface) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedHour = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showMapPicker) {
        LocationPickerDialog(
            onDismiss = { showMapPicker = false },
            onLocationSelected = { latitude, longitude, locName ->
                tasksViewModel.saveLocationZone(locName, latitude, longitude) { newZoneId ->
                    selectedLocationZoneId = newZoneId
                    selectedLocationName = locName
                    showMapPicker = false
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (shopping) "Add to shopping list" else "New Task", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(if (shopping) "What to buy" else "Task name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            if (!shopping) DescriptionField(description) { description = it }
            if (!shopping) {
                AreaPicker(areas, area) { area = it }
                ChecklistEditor(checklist) { checklist = it }
            }

            // Difficulty
            Text("Difficulty", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Difficulty.entries.toList()) { d ->
                    FilterChip(
                        selected = difficulty == d, onClick = { difficulty = d },
                        label = { Text(d.name, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Repetition (shopping items are always one-time)
            if (!shopping) Text("Repetition", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            if (!shopping) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RepetitionType.entries.toList()) { r ->
                    FilterChip(
                        selected = repetition == r, onClick = { repetition = r },
                        label = { Text(r.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (repetition == RepetitionType.REPEATING && !shopping) {
                RepeatDaysEditor(days = repeatDays, onDaysChange = { repeatDays = it })
                EditorLabel("How long")
                RepeatEndEditor(endDate = repeatEnd, challengeEnd = challengeEnd, onEndChange = { repeatEnd = it })
            }

            // Category Type
            Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item {
                FilterChip(
                    selected = categoryType == TaskCategoryType.ACTIVITY, onClick = { categoryType = TaskCategoryType.ACTIVITY },
                    label = { Text("🎯 Activity") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = categoryType == TaskCategoryType.LOCATION, onClick = { categoryType = TaskCategoryType.LOCATION },
                    label = { Text("📍 Location") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.width(8.dp))
                // Something to buy in real life: done whenever it's bought
                FilterChip(
                    selected = shopping, onClick = { categoryType = TaskCategoryType.SHOPPING },
                    label = { Text("🛒 Shopping") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } }

            // Activity category (only for ACTIVITY type)
            if (categoryType == TaskCategoryType.ACTIVITY) {
                Text("Activity Type", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val mainCategories = listOf(ActivityCategory.STUDY, ActivityCategory.GYM, ActivityCategory.WORK, ActivityCategory.CODING, ActivityCategory.READING, ActivityCategory.MEDITATION, ActivityCategory.MEAL, ActivityCategory.OTHER)
                    items(mainCategories) { cat ->
                        FilterChip(
                            selected = activityCategory == cat,
                            onClick = { activityCategory = cat; if (cat == ActivityCategory.MEAL) focusMinutes = 0 },
                            label = { Text("${activityIcon(cat)} ${cat.name}", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(0.8f),
                                selectedLabelColor = MaterialTheme.colorScheme.background,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                FocusSessionField(focusMinutes) { focusMinutes = it }
            } else if (categoryType == TaskCategoryType.LOCATION) {
                Text("Map Location", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { showMapPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = selectedLocationName ?: "Pick Location on Map",
                        color = if (selectedLocationName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Existing zones row
                if (locationZones.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(locationZones) { zone ->
                            FilterChip(
                                selected = selectedLocationZoneId == zone.id,
                                onClick = { 
                                    selectedLocationZoneId = zone.id
                                    selectedLocationName = zone.name
                                },
                                label = { Text("${zone.iconEmoji} ${zone.name}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            if (shopping) {
                Text(
                    "Buy it any time to earn the XP and coins. One-time, with no deadline unless you add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                ShoppingFields(shoppingForm, currency, deadlineReminders)
            }

            // Due (one-time tasks): off means any day; on gives it a day and, if wanted, a time with reminders before it
            val oneTime = repetition == RepetitionType.ONE_TIME
            val showTime = !shopping && (!oneTime || hasDueDate)
            if (!shopping && oneTime) SwitchRow(
                "📅 Due",
                if (hasDueDate) "On that day's list and the 100-day board" else "None: do it any day you like",
                hasDueDate
            ) { hasDueDate = it }
            if (!shopping && oneTime && hasDueDate) OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "📅 " + (selectedDateMillis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.getDefault()))
                    } ?: "Today"),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // All-day toggle
            if (showTime) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All day", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Switch(
                    checked = isAllDay, onCheckedChange = { isAllDay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // Time picker (only when NOT all-day)
            if (!isAllDay && showTime) {
                Text("⏰ Time", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = String.format("%02d:%02d", selectedHour, selectedMinute),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // A due time on a one-time task is its deadline: reminders before it
                if (oneTime) Text("🔔 Reminders $deadlineReminders.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (shopping && name.isNotBlank() && !shoppingForm.deadlinePassed) {
                        onAdd(
                            shoppingForm.applyTo(
                                TaskEntity(
                                    name = name.trim(), difficulty = difficulty,
                                    repetition = RepetitionType.ONE_TIME, categoryType = TaskCategoryType.SHOPPING,
                                    xpReward = com.habitgame.domain.engine.EconomyManager.xpForDifficulty(difficulty),
                                    coinReward = com.habitgame.domain.engine.EconomyManager.coinsForDifficulty(difficulty).toInt(),
                                    isAllDay = true, notifyEnabled = false, focusDurationMinutes = 0
                                )
                            ),
                            emptyList()
                        )
                    } else if (name.isNotBlank() && !shopping) {
                        val xp = com.habitgame.domain.engine.EconomyManager.xpForDifficulty(difficulty)
                        val coins = com.habitgame.domain.engine.EconomyManager.coinsForDifficulty(difficulty)
                        val dated = repetition == RepetitionType.REPEATING || hasDueDate
                        val timeMillis = if (!isAllDay && dated) {
                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                                set(java.util.Calendar.MINUTE, selectedMinute)
                                set(java.util.Calendar.SECOND, 0)
                            }
                            cal.timeInMillis
                        } else null
                        onAdd(TaskEntity(
                            name = name.trim(), difficulty = difficulty, repetition = repetition,
                            categoryType = categoryType, activityCategory = activityCategory,
                            description = description.trim(),
                            isAllDay = timeMillis == null, isRecurring = repetition == RepetitionType.REPEATING,
                            recurrenceDays = if (repetition == RepetitionType.REPEATING) repeatDays.joinToString(",") else "",
                            repeatEndDate = if (repetition == RepetitionType.REPEATING) repeatEnd?.toString() else null,
                            xpReward = xp, coinReward = coins.toInt(),
                            // Due date on without one picked: today
                            scheduledDateMillis = if (repetition == RepetitionType.ONE_TIME && hasDueDate)
                                selectedDateMillis ?: java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                            else null,
                            scheduledTimeMillis = timeMillis,
                            notifyEnabled = timeMillis != null,
                            notifyAtMillis = timeMillis,
                            locationZoneId = if (categoryType == TaskCategoryType.LOCATION) selectedLocationZoneId else null,
                            focusDurationMinutes = if (categoryType == TaskCategoryType.ACTIVITY) focusMinutes else 0,
                            deadlineAt = dueDeadline(repetition == RepetitionType.ONE_TIME && hasDueDate, selectedDateMillis, timeMillis),
                            groupName = area
                        ), checklist.map { it.trim() }.filter { it.isNotEmpty() })
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (shopping) "Add to list" else "Add Task", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
