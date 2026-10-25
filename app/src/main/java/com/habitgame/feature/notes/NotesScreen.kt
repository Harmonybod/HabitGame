package com.habitgame.feature.notes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.NoteEntity
import com.habitgame.domain.notes.SharedNotes
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.GameCard

/** The Notes tab: your notes and the ones shared in from Samsung Notes, with search. */
@Composable
fun NotesScreen(onOpen: (Long) -> Unit, viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val incoming by SharedNotes.pending.collectAsState()
    val context = LocalContext.current

    // A note shared with "Save to notes": keep it and open it
    LaunchedEffect(incoming) {
        val note = incoming ?: return@LaunchedEffect
        SharedNotes.pending.value = null
        viewModel.saveIncoming(note, onOpen)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(0L) }, containerColor = MaterialTheme.colorScheme.primary) {
                AppIcon("Add", contentDescription = "New note", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            item {
                Text("Notes", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search notes") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                )
            }
            item { SamsungNotesCard(context) }
            if (notes.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (query.isBlank()) "No notes yet. Tap + to write one." else "No notes match “$query”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            }
            items(notes, key = { it.id }) { note ->
                NoteCard(note, onClick = { onOpen(note.id) }, onTogglePin = { viewModel.togglePin(note) })
            }
        }
    }
}

/** How to bring notes over from Samsung Notes, with a button that opens it. */
@Composable
private fun SamsungNotesCard(context: android.content.Context) {
    val samsungNotes = remember { context.packageManager.getLaunchIntentForPackage(SharedNotes.SAMSUNG_NOTES) }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗒️", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("From Samsung Notes", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    "Open a note → Share → Text file (or Text) → HabitGame “Save to notes”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            if (samsungNotes != null) {
                TextButton(onClick = { context.startActivity(samsungNotes.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open") }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onClick: () -> Unit, onTogglePin: () -> Unit) {
    GameCard(Modifier.clickable(onClickLabel = "Open note", onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { SharedNotes.titleFromBody(note.body).ifBlank { "Untitled" } },
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(note.body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.source.isNotBlank()) {
                        Text(
                            note.source, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(updatedLabel(note.updatedAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onTogglePin) {
                Text("📌", fontSize = 18.sp, modifier = Modifier.alpha(if (note.pinned) 1f else 0.3f))
            }
        }
    }
}

private fun updatedLabel(time: Long): String {
    val date = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today " + com.habitgame.domain.tracker.TaskTiming.formatTime(com.habitgame.domain.tracker.TaskTiming.localTimeOf(time))
        today.minusDays(1) -> "Yesterday"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault()))
    }
}

/** Writing or editing one note. Changes are saved as you type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(onBack: () -> Unit, viewModel: NoteEditorViewModel = hiltViewModel()) {
    val note by viewModel.note.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete this note?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("It's removed from HabitGame. The original in Samsung Notes (if it came from there) isn't touched.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("Delete", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.source?.takeIf { it.isNotBlank() }?.let { "From $it" } ?: "Note", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon("ArrowBack", "Back") } },
                actions = {
                    note?.let { n ->
                        IconButton(onClick = viewModel::togglePin) { Text("📌", fontSize = 18.sp, modifier = Modifier.alpha(if (n.pinned) 1f else 0.3f)) }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, n.title)
                                .putExtra(Intent.EXTRA_TEXT, listOf(n.title, n.body).filter { it.isNotBlank() }.joinToString("\n\n"))
                            context.startActivity(Intent.createChooser(send, "Share note"))
                        }) { Text("📤", fontSize = 18.sp) }
                        IconButton(onClick = { confirmDelete = true }) { AppIcon("Delete", contentDescription = "Delete note") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val n = note ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp).imePadding()) {
            val plain = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
            TextField(
                value = n.title, onValueChange = { viewModel.edit(title = it) },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                colors = plain, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = n.body, onValueChange = { viewModel.edit(body = it) },
                placeholder = { Text("Write something…") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = plain, modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
