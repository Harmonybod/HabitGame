package com.habitgame.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.*
import com.habitgame.domain.engine.Checklist
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Habits and tasks on one page, in folders by area. The chips on top show everything, only habits,
 * only tasks, the shopping list, or one area. + adds a habit (from the folders of ready-made ones),
 * a task, a shopping item or an area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    onOpenTemplates: (area: String) -> Unit = {},
    onNavigateFocus: (Long) -> Unit = {},
    startFilter: PlanFilter? = null,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val challengeEnd by viewModel.challengeEnd.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val deadlineReminders by viewModel.deadlineReminders.collectAsState()

    LaunchedEffect(startFilter) { startFilter?.let { viewModel.setFilter(it) } }

    var showAddMenu by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<PlanItem?>(null) }
    var editingTask by remember { mutableStateOf<PlanItem?>(null) }
    var addingTask by remember { mutableStateOf<Boolean?>(null) }       // true = shopping item
    var areaSheet by remember { mutableStateOf<AreaEntity?>(null) }
    var newArea by remember { mutableStateOf(false) }
    val folded = remember { mutableStateMapOf<String, Boolean>() }
    var showLater by rememberSaveable { mutableStateOf(false) }
    var showBefore by rememberSaveable { mutableStateOf(false) }
    val currentArea = (state.filter as? PlanFilter.Area)?.name.orEmpty()

    // ── Sheets ──
    if (showAddMenu) {
        AddMenuSheet(
            area = currentArea,
            onDismiss = { showAddMenu = false },
            onHabit = { showAddMenu = false; onOpenTemplates(currentArea) },
            onTask = { showAddMenu = false; addingTask = false },
            onShopping = { showAddMenu = false; addingTask = true },
            onArea = { showAddMenu = false; newArea = true }
        )
    }
    editingHabit?.let { item ->
        HabitEditorSheet(
            initial = item.habit,
            challengeEnd = challengeEnd,
            areas = state.areas,
            steps = item.steps.map { it.text },
            onDismiss = { editingHabit = null },
            onSave = { habit, steps -> viewModel.updateHabit(habit, steps); editingHabit = null },
            onDelete = { viewModel.deleteHabit(item.habit!!); editingHabit = null }
        )
    }
    editingTask?.let { item ->
        val task = item.task!!
        if (task.isShopping) {
            com.habitgame.feature.tasks.EditShoppingSheet(
                task = task, currency = currency, reminderText = deadlineReminders,
                onDismiss = { editingTask = null },
                onSave = { viewModel.updateTask(it); editingTask = null },
                onDelete = { viewModel.deleteTask(task); editingTask = null }
            )
        } else {
            com.habitgame.feature.tasks.EditTaskSheet(
                task = task, challengeEnd = challengeEnd, deadlineReminders = deadlineReminders,
                areas = state.areas, checklist = item.steps.map { it.text },
                onDismiss = { editingTask = null },
                onSave = { t, steps -> viewModel.updateTask(t, steps); editingTask = null },
                onDelete = { viewModel.deleteTask(task); editingTask = null }
            )
        }
    }
    addingTask?.let { shopping ->
        com.habitgame.feature.tasks.AddTaskBottomSheet(
            currency = currency,
            deadlineReminders = deadlineReminders,
            startAsShopping = shopping,
            areas = state.areas,
            initialArea = currentArea,
            onDismiss = { addingTask = null },
            onAdd = { task, steps -> viewModel.addTask(task, steps); addingTask = null }
        )
    }
    if (newArea || areaSheet != null) {
        val editing = areaSheet
        AreaSheet(
            initial = editing,
            takenNames = state.areas.map { it.name }.toSet(),
            onDismiss = { newArea = false; areaSheet = null },
            onSave = { area ->
                viewModel.saveArea(area, editing?.name)
                if (editing == null) viewModel.setFilter(PlanFilter.Area(area.name))
                newArea = false; areaSheet = null
            },
            onDelete = editing?.let { { viewModel.deleteArea(it.name); areaSheet = null } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMenu = true }, containerColor = MaterialTheme.colorScheme.primary) {
                AppIcon("Add", contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "title") {
                Text("Habits & Tasks", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(
                    "Today · " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            item(key = "filters") {
                FilterRow(
                    selected = state.filter,
                    areas = state.areas,
                    onSelect = viewModel::setFilter,
                    onNewArea = { newArea = true }
                )
            }

            if (state.loaded && state.folders.isEmpty() && state.notToday.isEmpty() && state.doneBefore.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        text = when (state.filter) {
                            PlanFilter.Shopping -> "Your shopping list is empty."
                            PlanFilter.Tasks -> "No tasks yet."
                            else -> "Nothing here yet."
                        },
                        onAdd = { showAddMenu = true }
                    )
                }
            }

            state.folders.forEach { folder ->
                val open = folded[folder.key] != true
                item(key = "folder_${folder.key}") {
                    FolderHeader(
                        folder = folder,
                        open = open,
                        onToggle = { folded[folder.key] = open },
                        onEdit = folder.area?.let { area -> { areaSheet = area } }
                    )
                }
                if (open) {
                    if (folder.items.isEmpty()) {
                        item(key = "empty_${folder.key}") {
                            EmptyHint(
                                text = if (folder.area != null) "No habits or tasks in ${folder.title} today." else "Nothing here yet.",
                                onAdd = {
                                    if (folder.area != null) onOpenTemplates(folder.area.name) else showAddMenu = true
                                }
                            )
                        }
                    }
                    items(folder.items, key = { "${folder.key}_${it.key}" }) { item ->
                        PlanRow(
                            item = item,
                            onToggle = { viewModel.toggle(item) },
                            onToggleStep = viewModel::toggleStep,
                            onEdit = { if (item.habit != null) editingHabit = item else editingTask = item },
                            onStartFocus = { item.task?.let { onNavigateFocus(it.id) } },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            if (state.notToday.isNotEmpty()) {
                item(key = "later_header") { FoldRow("💤 Not today", state.notToday.size, showLater) { showLater = !showLater } }
                if (showLater) {
                    items(state.notToday, key = { "later_${it.key}" }) { item ->
                        PlanRow(
                            item = item, onToggle = null, onToggleStep = {},
                            onEdit = { if (item.habit != null) editingHabit = item else editingTask = item },
                            onStartFocus = {}, dim = true
                        )
                    }
                }
            }
            if (state.doneBefore.isNotEmpty()) {
                item(key = "before_header") { FoldRow("✓ Done earlier", state.doneBefore.size, showBefore) { showBefore = !showBefore } }
                if (showBefore) {
                    items(state.doneBefore, key = { "before_${it.key}" }) { item ->
                        PlanRow(item = item, onToggle = null, onToggleStep = {}, onEdit = { editingTask = item }, onStartFocus = {}, dim = true)
                    }
                }
            }
        }
    }
}

/** All · Habits · Tasks · Shopping, then each area, then + Area. */
@Composable
private fun FilterRow(selected: PlanFilter, areas: List<AreaEntity>, onSelect: (PlanFilter) -> Unit, onNewArea: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf(PlanFilter.All, PlanFilter.Habits, PlanFilter.Tasks, PlanFilter.Shopping)) { f ->
            val emoji = when (f) {
                PlanFilter.Habits -> "🔁 "
                PlanFilter.Tasks -> "📋 "
                PlanFilter.Shopping -> "🛒 "
                else -> ""
            }
            ChoiceChip(emoji + f.label, selected == f) { onSelect(f) }
        }
        items(areas, key = { "area_" + it.name }) { area ->
            val on = (selected as? PlanFilter.Area)?.name == area.name
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on) PlanFilter.All else PlanFilter.Area(area.name)) },
                label = { Text("${area.iconEmoji} ${area.name}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = areaColor(area.colorHex),
                    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                    containerColor = areaColor(area.colorHex).copy(alpha = 0.14f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = null
            )
        }
        item(key = "new_area") {
            AssistChip(onClick = onNewArea, label = { Text("+ Area") })
        }
    }
}

/** A folder's title: icon, name, how many are done today, and (for areas) an edit button. */
@Composable
private fun FolderHeader(folder: PlanFolder, open: Boolean, onToggle: () -> Unit, onEdit: (() -> Unit)?) {
    val tint = folder.colorHex?.let { areaColor(it) } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (open) "Fold" else "Unfold", onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(folder.emoji, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (folder.items.isNotEmpty()) {
                Text("${folder.doneCount} of ${folder.items.size} done", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onEdit != null) {
            TextButton(onClick = onEdit) { Text("Edit", fontSize = 13.sp) }
        }
        AppIcon(if (open) "ExpandLess" else "ExpandMore", contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FoldRow(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$title ($count)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(if (open) "Hide" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyHint(text: String, onAdd: () -> Unit) {
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAdd) { Text("+ Add", fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * One habit or task: tick on the left, name with its time, repeat and streak, its checklist under it,
 * and Start for tasks with a focus timer. Tap to edit.
 */
@Composable
fun PlanRow(
    item: PlanItem,
    onToggle: (() -> Unit)?,
    onToggleStep: (ChecklistItemEntity) -> Unit,
    onEdit: () -> Unit,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier,
    dim: Boolean = false
) {
    val today = LocalDate.now().toString()
    var showSteps by remember(item.key) { mutableStateOf(!item.done) }
    val struck = item.done || item.skipped
    GameCard(modifier.alpha(if (dim) 0.65f else 1f).clickable(onClickLabel = "Edit", onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onToggle != null) {
                // A done task stays done; a habit can be unticked
                RoundCheck(checked = item.done, onClick = onToggle, enabled = !item.skipped && (item.habit != null || !item.done))
            }
            item.habit?.let { Text(it.iconEmoji, fontSize = 24.sp) }
            item.task?.let { task ->
                when (task.categoryType) {
                    TaskCategoryType.ACTIVITY -> com.habitgame.ui.ActivityIcon(task.activityCategory, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    TaskCategoryType.LOCATION -> AppIcon("LocationOn", contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    TaskCategoryType.SHOPPING -> com.habitgame.ui.PackEmoji("🛒", 24.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.name, fontWeight = FontWeight.Medium,
                    color = if (struck || dim) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (struck) TextDecoration.LineThrough else null
                )
                val meta = listOfNotNull(
                    item.time?.let { "⏰ " + TaskTiming.formatTime(it) },
                    item.habit?.let { TaskTiming.repeatRule(it).describe() },
                    item.task?.let { t ->
                        when {
                            t.isShopping -> com.habitgame.domain.tracker.ShoppingText.priceRange(t.priceMin, t.priceMax, "")?.let { "💲 $it" } ?: "Any time"
                            t.isRecurring -> TaskTiming.repeatRule(t).describe()
                            TaskTiming.assignedDate(t) == null -> "Any time"
                            else -> TaskTiming.assignedDate(t)!!.let { d ->
                                if (d == LocalDate.now()) "Today" else d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                            }
                        }
                    },
                    if (item.skipped) "⏭ Skipped today" else null
                )
                Text(meta.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.streak > 0) Text("🔥${item.streak}", color = WarningAmber.readable(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val xp = item.habit?.xpReward ?: item.task!!.xpReward
                    val coins = item.habit?.coinReward ?: item.task!!.coinReward
                    Text("⭐$xp  🪙$coins", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    if (item.steps.isNotEmpty()) {
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = if (showSteps) "Hide checklist" else "Show checklist") { showSteps = !showSteps }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChecklistBadge(item.stepsDone, item.steps.size)
                            Text(if (showSteps) " ▴" else " ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (item.task?.hasFocusTimer == true && !struck && onToggle != null) {
                Button(
                    onClick = onStartFocus,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (showSteps && item.steps.isNotEmpty()) {
            ChecklistSteps(
                items = item.steps,
                isChecked = { Checklist.isChecked(it, item.repeats, today) },
                enabled = onToggle != null && !item.skipped && (item.habit != null || !item.done),
                onToggle = onToggleStep
            )
        }
    }
}

/** What + adds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMenuSheet(area: String, onDismiss: () -> Unit, onHabit: () -> Unit, onTask: () -> Unit, onShopping: () -> Unit, onArea: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (area.isNotEmpty()) {
                Text("Adding to $area", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            AddMenuRow("🌱", SuccessGreen, "New habit", "Pick from folders of ready-made habits, or make your own", onHabit)
            AddMenuRow("📋", CyanXp, "New task", "Something to do once or on repeat, with an optional checklist", onTask)
            AddMenuRow("🛒", WarningAmber, "Shopping item", "Something to buy, with a price and deadline", onShopping)
            AddMenuRow("📁", NeonPink, "New area", "A folder for habits and tasks that go together", onArea)
        }
    }
}

@Composable
private fun AddMenuRow(emoji: String, tint: androidx.compose.ui.graphics.Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Create a habit, or edit one: name, icon, area, checklist, difficulty, time, repeat days and how long
 * it repeats. A habit with id 0 is new (possibly filled in from a ready-made one).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    initial: HabitEntity?,
    challengeEnd: LocalDate?,
    areas: List<AreaEntity>,
    steps: List<String>,
    onDismiss: () -> Unit,
    onSave: (HabitEntity, List<String>) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isNew = initial == null || initial.id == 0L
    val rule = remember(initial) { initial?.let { TaskTiming.repeatRule(it) } }
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember(initial) { mutableStateOf(initial?.iconEmoji ?: "🎯") }
    var difficulty by remember(initial) { mutableStateOf(initial?.difficulty ?: Difficulty.MEDIUM) }
    var time by remember(initial) { mutableStateOf(initial?.let { TaskTiming.timeOfDay(it) }) }
    var days by remember(initial) { mutableStateOf(rule?.days ?: emptySet()) }
    var endDate by remember(initial) { mutableStateOf(rule?.endDate) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var showOnHome by remember(initial) { mutableStateOf(initial?.showOnHome ?: false) }
    var area by remember(initial) { mutableStateOf(initial?.groupName.orEmpty()) }
    var checklist by remember(initial) { mutableStateOf(steps) }

    val availableIcons = (listOf(icon) + listOf("🎯", "📚", "🏋️", "💻", "🧘", "🏃", "💧", "🍎", "📝", "🎵", "🍳", "🍌", "🍛", "🥤", "🍽️", "🥛", "🪥", "🚿", "😴", "🧹")).distinct()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (isNew) "New habit" else "Edit habit", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Habit name") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description or goal (optional)") },
                placeholder = { Text("e.g. 20 minutes a day") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show on Home", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (description.isBlank()) "Add a description to show it on Home" else "Appears as a card under the 100-day board",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = showOnHome && description.isNotBlank(), enabled = description.isNotBlank(), onCheckedChange = { showOnHome = it })
            }

            EditorLabel("Icon")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableIcons) { emoji ->
                    FilterChip(
                        selected = icon == emoji, onClick = { icon = emoji },
                        label = { Text(emoji, fontSize = 18.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            AreaPicker(areas, area) { area = it }

            EditorLabel("Difficulty")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Difficulty.entries.toList()) { d -> ChoiceChip(d.name, difficulty == d) { difficulty = d } }
            }

            EditorLabel("⏰ Time")
            TimeEditor(time = time, onTimeChange = { time = it })

            EditorLabel("🔁 Repeat")
            RepeatDaysEditor(days = days, onDaysChange = { days = it })

            EditorLabel("How long")
            RepeatEndEditor(endDate = endDate, challengeEnd = challengeEnd, onEndChange = { endDate = it })

            ChecklistEditor(checklist) { checklist = it }

            EditorActions(
                saveLabel = if (isNew) "Add habit" else "Save changes",
                onSave = {
                    if (name.isBlank()) return@EditorActions
                    val base = initial ?: HabitEntity(name = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            description = description.trim(),
                            showOnHome = showOnHome && description.isNotBlank(),
                            iconEmoji = icon,
                            difficulty = difficulty,
                            xpReward = EconomyManager.xpForDifficulty(difficulty),
                            coinReward = EconomyManager.coinsForDifficulty(difficulty).toInt(),
                            reminderTimeMillis = time?.let { TaskTiming.millisForTime(it) },
                            frequency = if (days.isEmpty()) HabitFrequency.DAILY else HabitFrequency.SPECIFIC_DAYS,
                            frequencyDays = days.joinToString(","),
                            repeatEndDate = endDate?.toString(),
                            groupName = area,
                            // A new habit starts today, even when filled in from a ready-made one
                            createdAt = if (isNew) System.currentTimeMillis() else base.createdAt
                        ),
                        checklist.map { it.trim() }.filter { it.isNotEmpty() }
                    )
                },
                onDelete = if (isNew) null else onDelete
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
