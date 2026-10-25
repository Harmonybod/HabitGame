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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNavigateFocus: (Long) -> Unit = {},
    viewModel: TasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val challengeEnd by viewModel.challengeEnd.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val deadlineReminders by viewModel.deadlineReminderText.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

    editing?.takeIf { it.isShopping }?.let { task ->
        EditShoppingSheet(
            task = task,
            currency = currency,
            reminderText = deadlineReminders,
            onDismiss = { editing = null },
            onSave = { viewModel.updateTask(it); editing = null },
            onDelete = { viewModel.deleteTask(task); editing = null }
        )
    }
    editing?.takeIf { !it.isShopping }?.let { task ->
        EditTaskSheet(
            task = task,
            challengeEnd = challengeEnd,
            onDismiss = { editing = null },
            onSave = { viewModel.updateTask(it); editing = null },
            onDelete = { viewModel.deleteTask(task); editing = null }
        )
    }
    val filters = listOf("All", "Pending", "Done", "Shopping", "Repeating", "Activity", "Location")

    if (showAddSheet) {
        AddTaskBottomSheet(
            currency = currency,
            deadlineReminders = deadlineReminders,
            startAsShopping = state.filter == "Shopping",
            onDismiss = { showAddSheet = false },
            onAdd = { task -> viewModel.addTask(task); showAddSheet = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { AppIcon("Add", contentDescription = "Add Task", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            ScrollableFilterRow(filters, state.filter, viewModel::setFilter)
            Spacer(Modifier.height(12.dp))

            if (state.tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val shopping = state.filter == "Shopping"
                        Text(if (shopping) "🛒" else "📋", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(if (shopping) "Your shopping list is empty." else "No tasks here yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (shopping) "Tap + to add something you want to buy." else "Tap + to add one!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskListItem(
                            task = task,
                            currency = currency,
                            onComplete = { viewModel.completeTask(task) },
                            onEdit = { editing = task },
                            onDelete = { viewModel.deleteTask(task) },
                            onStartFocus = { onNavigateFocus(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableFilterRow(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filters) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter) },
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

@Composable
fun TaskListItem(task: TaskEntity, currency: String = "", onComplete: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onStartFocus: () -> Unit) {
    val diffColor = when (task.difficulty) {
        Difficulty.EASY   -> SuccessGreen
        Difficulty.MEDIUM -> CyanXp
        Difficulty.HARD   -> WarningAmber
        Difficulty.EPIC   -> NeonPink
    }
    GameCard(Modifier.clickable(onClickLabel = "Edit task", onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundCheck(checked = task.isCompleted, onClick = onComplete, enabled = !task.isCompleted)
            when (task.categoryType) {
                TaskCategoryType.ACTIVITY -> com.habitgame.ui.ActivityIcon(task.activityCategory, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                TaskCategoryType.LOCATION -> AppIcon("LocationOn", contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                TaskCategoryType.SHOPPING -> com.habitgame.ui.PackEmoji("🛒", 26.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(task.name, color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DifficultyBadge(task.difficulty.name, diffColor)
                    if (task.isShopping) {
                        val price = com.habitgame.domain.tracker.ShoppingText.priceRange(task.priceMin, task.priceMax, currency)
                        Text(
                            listOfNotNull(price?.let { "💲 $it" }, if (task.deadlineAt == null) "no deadline" else null).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontSize = 10.sp
                        )
                    } else {
                        val time = com.habitgame.domain.tracker.TaskTiming.timeOfDay(task)
                        Text(
                            listOfNotNull(
                                time?.let { (if (task.notifyEnabled) "🔔 " else "") + com.habitgame.domain.tracker.TaskTiming.formatTime(it) },
                                if (task.isRecurring) com.habitgame.domain.tracker.TaskTiming.repeatRule(task).describe() else "once"
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontSize = 10.sp
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⭐${task.xpReward} XP  🪙${task.coinReward}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                // Shopping deadline: amber when less than an hour is left
                if (task.isShopping && !task.isCompleted) task.deadlineAt?.let { deadline ->
                    val soon = deadline - System.currentTimeMillis() < 60 * 60_000L
                    Text(
                        "⏳ Buy " + com.habitgame.domain.tracker.ShoppingText.deadline(deadline),
                        color = if (soon) WarningAmber.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (task.hasFocusTimer && !task.isCompleted) {
                Button(
                    onClick = onStartFocus,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            IconButton(onClick = onDelete) {
                AppIcon("Delete", contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
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
    onDismiss: () -> Unit,
    onAdd: (TaskEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
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
                            selected = activityCategory == cat, onClick = { activityCategory = cat },
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

            // Date picker
            if (!shopping) Text("📅 Date", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            if (!shopping) OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = if (selectedDateMillis != null) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                        "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}/${cal.get(java.util.Calendar.YEAR)}"
                    } else "Select Date",
                    color = if (selectedDateMillis != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // All-day toggle
            if (!shopping) Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (!isAllDay && !shopping) {
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
                            )
                        )
                    } else if (name.isNotBlank() && !shopping) {
                        val xp = com.habitgame.domain.engine.EconomyManager.xpForDifficulty(difficulty)
                        val coins = com.habitgame.domain.engine.EconomyManager.coinsForDifficulty(difficulty)
                        val timeMillis = if (!isAllDay) {
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
                            isAllDay = isAllDay, isRecurring = repetition == RepetitionType.REPEATING,
                            recurrenceDays = if (repetition == RepetitionType.REPEATING) repeatDays.joinToString(",") else "",
                            repeatEndDate = if (repetition == RepetitionType.REPEATING) repeatEnd?.toString() else null,
                            xpReward = xp, coinReward = coins.toInt(),
                            scheduledDateMillis = selectedDateMillis,
                            scheduledTimeMillis = timeMillis,
                            notifyEnabled = timeMillis != null,
                            notifyAtMillis = timeMillis,
                            locationZoneId = if (categoryType == TaskCategoryType.LOCATION) selectedLocationZoneId else null,
                            // Meals are just ticked off; other activities keep the focus timer
                            focusDurationMinutes = if (activityCategory == ActivityCategory.MEAL) 0 else 25
                        ))
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
