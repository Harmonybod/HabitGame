package com.habitgame.feature.calendar

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.Difficulty
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // Month navigation header
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.changeMonth(-1) }) {
                    AppIcon("ChevronLeft", contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "${state.currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.currentMonth.year}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.changeMonth(1) }) {
                    AppIcon("ChevronRight", contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Day-of-week headers
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Calendar grid
        item {
            CalendarGrid(
                yearMonth = state.currentMonth,
                selectedDate = state.selectedDate,
                onDateSelected = { viewModel.selectDate(it) }
            )
        }

        // Selected date agenda
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "📋 ${state.selectedDate}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        // Tasks for selected date
        if (state.tasksForDate.isEmpty() && state.habitsForDate.isEmpty()) {
            item {
                GameCard {
                    Text("No tasks or habits for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (state.tasksForDate.isNotEmpty()) {
            item { Text("Tasks", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
            items(state.tasksForDate) { task ->
                val diffColor = when (task.difficulty) {
                    Difficulty.EASY -> SuccessGreen; Difficulty.MEDIUM -> CyanXp
                    Difficulty.HARD -> WarningAmber; Difficulty.EPIC -> NeonPink
                }
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (task.isCompleted) "✅" else "⏳", fontSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text(task.name, color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DifficultyBadge(task.difficulty.name, diffColor)
                                Text("⭐${task.xpReward} XP", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (state.habitsForDate.isNotEmpty()) {
            item { Text("Habits", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
            items(state.habitsForDate) { habit ->
                val isCompleted = habit.id in state.completedHabitIds
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(habit.iconEmoji, fontSize = 22.sp)
                        Text(habit.name, color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(if (isCompleted) "✅" else "⬜", fontSize = 18.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun CalendarGrid(yearMonth: YearMonth, selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val firstDay = yearMonth.atDay(1)
    val firstDow = (firstDay.dayOfWeek.value - 1) // Monday=0
    val daysInMonth = yearMonth.lengthOfMonth()
    val today = LocalDate.now()

    val cells = mutableListOf<LocalDate?>()
    repeat(firstDow) { cells.add(null) }
    for (d in 1..daysInMonth) { cells.add(yearMonth.atDay(d)) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
        userScrollEnabled = false
    ) {
        items(cells) { date ->
            if (date == null) {
                Box(Modifier.size(40.dp))
            } else {
                val isSelected = date == selectedDate
                val isToday = date == today
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                            else if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                        .clickable { onDateSelected(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${date.dayOfMonth}",
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 14.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

