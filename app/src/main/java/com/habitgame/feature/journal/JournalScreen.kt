package com.habitgame.feature.journal

import com.habitgame.domain.music.music
import com.habitgame.ui.AppIcon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.boardColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalScreen(viewModel: JournalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf<String?>(null) }
    val dateLabel = state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with day navigation
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.isToday) "Journal" else "Day ${state.day}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Day ${state.day} · $dateLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                DayNavButton("ChevronLeft", "Previous day", state.canGoBack, viewModel::previousDay)
                Spacer(Modifier.width(8.dp))
                DayNavButton("ChevronRight", "Next day", state.canGoForward, viewModel::nextDay)
            }
        }

        // Mood + writing
        item {
            GameCard {
                Text("How are you feeling?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrackerPlan.MOODS.forEachIndexed { index, emoji ->
                        val isSelected = state.mood == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(boardColors().empty)
                                .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { viewModel.setMood(index) }
                                .semantics {
                                    contentDescription = "Mood ${index + 1} of 5"
                                    selected = isSelected
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                }
                Text(
                    state.prompt,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    placeholder = { Text("No rules. Write what's on your mind.") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Text(
                    state.saveNote,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Song or album of the day
        item {
            GameCard { MusicOfTheDay(state, viewModel) }
        }

        // Weight
        item {
            GameCard {
                Text("Weight gain", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Your meals and workout are in Habits. Log your weight here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.weight,
                    onValueChange = viewModel::setWeight,
                    label = { Text("Weight today (optional)") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Text(state.weightNote, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // Past entries
        item {
            Text("Past entries", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        if (state.pastEntries.isEmpty()) {
            item {
                Text("Your entries will show up here once you start writing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.pastEntries, key = { it.entry.date }) { past ->
            val isOpen = expanded == past.entry.date
            GameCard(Modifier.clickable { expanded = if (isOpen) null else past.entry.date }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Day ${past.day}" + (past.entry.mood?.let { " " + TrackerPlan.MOODS[it] } ?: ""),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        past.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (past.entry.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        past.entry.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isOpen) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                past.entry.music?.let { music ->
                    Spacer(Modifier.height(8.dp))
                    com.habitgame.ui.components.MusicCard(music, coverSize = 48.dp)
                }
            }
        }
    }
}

/**
 * The day's song or album: shows it (tap to play in its app), or a box to paste a link.
 * Songs shared from Spotify, YouTube Music, Apple Music… land here too.
 */
@Composable
private fun MusicOfTheDay(state: JournalUiState, viewModel: JournalViewModel) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var link by remember(state.selectedDate) { mutableStateOf("") }
    var changing by remember(state.selectedDate, state.music?.url) { mutableStateOf(false) }
    val music = state.music

    Text(
        if (music != null) "🎵 ${music.kind.label} of the day" else "🎵 Song or album of the day",
        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(10.dp))
    if (music != null && !changing) {
        com.habitgame.ui.components.MusicCard(music)
        Row {
            TextButton(onClick = { changing = true }) { Text("Change") }
            TextButton(onClick = viewModel::removeMusic) { Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    } else {
        Text(
            "Share a song or album from Spotify, YouTube Music, Apple Music, Deezer or SoundCloud to HabitGame, or paste its link here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            placeholder = { Text("https://open.spotify.com/track/…") },
            singleLine = true,
            trailingIcon = { TextButton(onClick = { clipboard.getText()?.text?.let { link = it } }) { Text("Paste") } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.addMusic(link); changing = false },
                enabled = link.isNotBlank() && !state.musicLoading
            ) { Text("Add") }
            if (changing) TextButton(onClick = { changing = false }) { Text("Cancel") }
            if (state.musicLoading) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Looking it up…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
    state.musicMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }

    // A song shared from another app: put it on today's entry
    val shared by com.habitgame.domain.music.SharedMusic.pending.collectAsState()
    LaunchedEffect(shared) {
        shared?.let {
            com.habitgame.domain.music.SharedMusic.pending.value = null
            viewModel.addMusic(it, forToday = true)
        }
    }
}

@Composable
private fun DayNavButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(40.dp)
    ) {
        AppIcon(icon, contentDescription = label)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)
