package com.habitgame.feature.habits

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.entity.AreaEntity
import com.habitgame.core.database.entity.DaySkipEntity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.HabitFrequency
import com.habitgame.core.database.repository.AreaRepository
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.engine.ChecklistManager
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.habits.HabitTemplate
import com.habitgame.domain.habits.HabitTemplates
import com.habitgame.domain.habits.TemplateFolder
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.areaColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val habitRepo: HabitRepository,
    private val areaRepo: AreaRepository,
    private val checklistManager: ChecklistManager,
    private val trackerManager: TrackerManager,
    trackerRepo: TrackerRepository
) : ViewModel() {

    val areas: StateFlow<List<AreaEntity>> = areaRepo.areas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Names of the habits already made, per area, so ready-made ones you have show a tick. */
    val existing: StateFlow<Map<String, Set<String>>> = habitRepo.observeActiveHabits()
        .map { habits -> habits.groupBy { it.groupName }.mapValues { (_, list) -> list.map { it.name.lowercase() }.toSet() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val challengeEnd: StateFlow<LocalDate?> = trackerRepo.observeChallenge()
        .map { c -> c?.startDate?.let { runCatching { LocalDate.parse(it).plusDays(99) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** A ready-made habit as a new habit in its folder's area. */
    fun draft(template: HabitTemplate, folder: TemplateFolder): HabitEntity = HabitEntity(
        name = template.name,
        description = template.goal,
        iconEmoji = template.emoji,
        frequency = if (template.days.isEmpty()) HabitFrequency.DAILY else HabitFrequency.SPECIFIC_DAYS,
        frequencyDays = template.days.joinToString(","),
        reminderTimeMillis = template.time?.let { TaskTiming.millisForTime(it) },
        difficulty = template.difficulty,
        xpReward = EconomyManager.xpForDifficulty(template.difficulty),
        coinReward = EconomyManager.coinsForDifficulty(template.difficulty).toInt(),
        groupName = folder.name
    )

    fun add(habit: HabitEntity, steps: List<String>) {
        viewModelScope.launch { insert(habit, steps) }
    }

    /** Adds every habit of the folder you don't have yet. Returns how many through [onDone]. */
    fun addAll(folder: TemplateFolder, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val have = existing.value[folder.name].orEmpty()
            val missing = folder.templates.filter { it.name.lowercase() !in have }
            missing.forEach { insert(draft(it, folder), it.checklist) }
            onDone(missing.size)
        }
    }

    private suspend fun insert(habit: HabitEntity, steps: List<String>) {
        val folder = HabitTemplates.folder(habit.groupName)
        if (folder != null) areaRepo.ensure(folder.name, folder.emoji, folder.colorHex) else areaRepo.ensure(habit.groupName)
        val id = habitRepo.insertHabit(habit)
        checklistManager.save(DaySkipEntity.KIND_HABIT, id, steps)
        trackerManager.scheduleReminder(habit.copy(id = id))
        trackerManager.snapshotToday()
    }
}

/**
 * "New habit": folders of ready-made habits (each one an area), and Create your own.
 * Tapping a ready-made habit opens the habit form filled in; "Add all" adds the whole folder.
 */
@Composable
fun TemplatesScreen(
    area: String = "",
    onDone: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val areas by viewModel.areas.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val challengeEnd by viewModel.challengeEnd.collectAsState()
    // Opened from inside an area that has a folder: start in it
    var openFolder by remember { mutableStateOf(HabitTemplates.folder(area)) }
    var draft by remember { mutableStateOf<Pair<HabitEntity, List<String>>?>(null) }

    BackHandler(enabled = openFolder != null) { openFolder = null }

    draft?.let { (habit, steps) ->
        HabitEditorSheet(
            initial = habit,
            challengeEnd = challengeEnd,
            areas = areas,
            steps = steps,
            onDismiss = { draft = null },
            onSave = { h, s ->
                viewModel.add(h, s)
                draft = null
                Toast.makeText(context, "Added “${h.name}”" + if (h.groupName.isNotEmpty()) " to ${h.groupName}" else "", Toast.LENGTH_SHORT).show()
                onDone()
            },
            onDelete = null
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val folder = openFolder
            if (folder == null) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Text("New habit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Pick a folder of ready-made habits. Each folder becomes an area you can keep adding to.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(HabitTemplates.FOLDERS, key = { it.name }) { f ->
                    val have = existing[f.name].orEmpty()
                    val added = f.templates.count { it.name.lowercase() in have }
                    FolderCard(f, added) { openFolder = f }
                }
            } else {
                item(span = { GridItemSpan(2) }) {
                    val have = existing[folder.name].orEmpty()
                    val missing = folder.templates.count { it.name.lowercase() !in have }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openFolder = null }) {
                                AppIcon("ArrowBack", contentDescription = "All folders", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text("${folder.emoji} ${folder.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(folder.blurb, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                        Spacer(Modifier.height(8.dp))
                        if (missing > 0) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.addAll(folder) { n ->
                                        Toast.makeText(context, "Added $n habits to ${folder.name}", Toast.LENGTH_SHORT).show()
                                        onDone()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Add all $missing to the “${folder.name}” area", fontWeight = FontWeight.SemiBold) }
                        } else {
                            Text("✓ You have every habit in this folder.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                items(folder.templates, key = { folder.name + it.name }) { t ->
                    val added = t.name.lowercase() in existing[folder.name].orEmpty()
                    TemplateCard(t, folder, added) { draft = viewModel.draft(t, folder) to t.checklist }
                }
            }
        }
        // Your own habit, in the area you came from
        Button(
            onClick = {
                val own = HabitEntity(name = "", groupName = openFolder?.name ?: area, difficulty = com.habitgame.core.database.entity.Difficulty.MEDIUM)
                draft = own to emptyList()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Create your own", fontWeight = FontWeight.Bold) }
    }
}

/** A folder: its colour, a big icon, its name and how many of its habits you have. */
@Composable
private fun FolderCard(folder: TemplateFolder, added: Int, onClick: () -> Unit) {
    val color = Color(folder.color)
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f))))
            .clickable(onClickLabel = "Open ${folder.name}", onClick = onClick)
            .padding(14.dp)
    ) {
        Text(folder.emoji, fontSize = 40.sp)
        Spacer(Modifier.weight(1f))
        Text(folder.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        Text(
            "${folder.templates.size} habits" + if (added > 0) " · $added added" else "",
            color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp
        )
    }
}

/** A ready-made habit: name on top, its picture (a big emoji) at the bottom right, like a card. */
@Composable
private fun TemplateCard(template: HabitTemplate, folder: TemplateFolder, added: Boolean, onClick: () -> Unit) {
    val color = areaColor(folder.colorHex)
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.22f))
            .clickable(onClickLabel = "Add ${template.name}", onClick = onClick)
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth(0.95f)) {
            Text(template.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (template.goal.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(template.goal, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            template.time?.let { Text("⏰ " + TaskTiming.formatTime(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(template.emoji, fontSize = 52.sp, modifier = Modifier.align(Alignment.BottomEnd))
        if (added) {
            Text(
                "✓ Added", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
