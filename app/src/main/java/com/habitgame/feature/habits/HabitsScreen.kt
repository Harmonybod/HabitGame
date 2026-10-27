package com.habitgame.feature.habits

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.*
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.*
import java.time.LocalDate

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val challengeEnd by viewModel.challengeEnd.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }

    if (showAddSheet) {
        HabitEditorSheet(
            initial = null,
            challengeEnd = challengeEnd,
            onDismiss = { showAddSheet = false },
            onSave = { habit -> viewModel.addHabit(habit); showAddSheet = false },
            onDelete = null
        )
    }
    editing?.let { habit ->
        HabitEditorSheet(
            initial = habit,
            challengeEnd = challengeEnd,
            onDismiss = { editing = null },
            onSave = { viewModel.updateHabit(it); editing = null },
            onDelete = { viewModel.deleteHabit(habit); editing = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { AppIcon("Add", contentDescription = "Add Habit", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Today's Habits", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("Tap a habit to change its time, days or how long it repeats", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            if (state.sections.isEmpty() && state.notToday.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔄", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("No habits yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to create one!", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.sections.forEach { section ->
                item(key = "header_${section.title}") {
                    val done = section.habits.count { it.isCompletedToday }
                    SectionHeader(section.title, "$done of ${section.habits.size} done")
                }
                items(section.habits, key = { it.habit.id }) { habitStatus ->
                    HabitListItem(
                        habitStatus = habitStatus,
                        onToggle = { viewModel.toggleHabit(habitStatus.habit) },
                        onEdit = { editing = habitStatus.habit }
                    )
                }
            }

            if (state.notToday.isNotEmpty()) {
                item(key = "header_not_today") { SectionHeader("Not today", "Other days, or finished repeating") }
                items(state.notToday, key = { it.id }) { habit ->
                    HabitListItem(
                        habitStatus = HabitWithStatus(habit, isCompletedToday = false, streak = 0),
                        onToggle = null,
                        onEdit = { editing = habit },
                        modifier = Modifier.alpha(0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HabitListItem(
    habitStatus: HabitWithStatus,
    onToggle: (() -> Unit)?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitStatus.habit
    val diffColor = when (habit.difficulty) {
        Difficulty.EASY   -> SuccessGreen
        Difficulty.MEDIUM -> CyanXp
        Difficulty.HARD   -> WarningAmber
        Difficulty.EPIC   -> NeonPink
    }
    val time = TaskTiming.timeOfDay(habit)
    GameCard(modifier.clickable(onClickLabel = "Edit habit", onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(habit.iconEmoji, fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text(habit.name, color = if (habitStatus.isCompletedToday) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(
                        time?.let { "🔔 " + TaskTiming.formatTime(it) },
                        TaskTiming.repeatRule(habit).describe()
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DifficultyBadge(habit.difficulty.name, diffColor)
                    if (habitStatus.streak > 0) {
                        Text("🔥${habitStatus.streak}", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("⭐${habit.xpReward} XP  🪙${habit.coinReward}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onToggle != null) {
                RoundCheck(checked = habitStatus.isCompletedToday, onClick = onToggle)
            }
        }
    }
}

/** Create a habit, or edit one: name, icon, difficulty, time, repeat days and how long it repeats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    initial: HabitEntity?,
    challengeEnd: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onDelete: (() -> Unit)?
) {
    val rule = remember(initial?.id) { initial?.let { TaskTiming.repeatRule(it) } }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.iconEmoji ?: "🎯") }
    var difficulty by remember(initial?.id) { mutableStateOf(initial?.difficulty ?: Difficulty.MEDIUM) }
    var time by remember(initial?.id) { mutableStateOf(initial?.let { TaskTiming.timeOfDay(it) }) }
    var days by remember(initial?.id) { mutableStateOf(rule?.days ?: emptySet()) }
    var endDate by remember(initial?.id) { mutableStateOf(rule?.endDate) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var showOnHome by remember(initial?.id) { mutableStateOf(initial?.showOnHome ?: false) }

    val availableIcons = listOf("🎯", "📚", "🏋️", "💻", "🧘", "🏃", "💧", "🍎", "📝", "🎵", "🍳", "🍌", "🍛", "🥤", "🍽️", "🥛")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (initial == null) "New Habit" else "Edit habit",
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold
            )
            if (initial?.groupName?.isNotEmpty() == true) {
                Text("Part of ${initial.groupName}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Habit name") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("e.g. Today: learn loops in Python") },
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

            EditorActions(
                saveLabel = if (initial == null) "Add Habit" else "Save changes",
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
                            repeatEndDate = endDate?.toString()
                        )
                    )
                },
                onDelete = onDelete
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
