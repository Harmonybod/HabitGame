package com.habitgame.feature.notes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.NoteEntity
import com.habitgame.domain.notes.NoteFormat
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
                    Text(NoteFormat.plain(note.body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                    val (ticked, boxes) = NoteFormat.checklist(note.body)
                    if (boxes > 0) Text("☑ $ticked/$boxes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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

/** Writing or editing one note: a formatting bar, lists that carry on as you type, and changes saved as you go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(onBack: () -> Unit, viewModel: NoteEditorViewModel = hiltViewModel()) {
    val note by viewModel.note.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    // The body as the editor holds it (text + cursor), filled once the note has loaded
    var body by remember { mutableStateOf<TextFieldValue?>(null) }
    val undo = remember { mutableStateListOf<TextFieldValue>() }
    val redo = remember { mutableStateListOf<TextFieldValue>() }
    var lastSnapshot by remember { mutableLongStateOf(0L) }
    LaunchedEffect(note != null) { if (body == null) note?.let { body = TextFieldValue(it.body) } }

    /** Takes the new body: remembers the old one for Undo (a pause in typing starts a new step) and saves. */
    fun commit(next: TextFieldValue, forceStep: Boolean = false) {
        val current = body ?: return
        if (next.text != current.text) {
            val now = System.currentTimeMillis()
            if (forceStep || now - lastSnapshot > 900 || undo.isEmpty()) {
                undo.add(current)
                if (undo.size > 100) undo.removeAt(0)
            }
            lastSnapshot = now
            redo.clear()
            viewModel.edit(body = next.text)
        }
        body = next
    }

    fun format(change: (NoteFormat.Edit) -> NoteFormat.Edit) {
        val current = body ?: return
        val e = change(current.toEdit())
        commit(e.toValue(), forceStep = true)
    }

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
                    // Undo / redo
                    IconButton(onClick = {
                        val current = body ?: return@IconButton
                        undo.removeLastOrNull()?.let { prev -> redo.add(current); body = prev; viewModel.edit(body = prev.text) }
                    }, enabled = undo.isNotEmpty()) { Text("↶", fontSize = 22.sp, modifier = Modifier.alpha(if (undo.isNotEmpty()) 1f else 0.3f)) }
                    IconButton(onClick = {
                        val current = body ?: return@IconButton
                        redo.removeLastOrNull()?.let { next -> undo.add(current); body = next; viewModel.edit(body = next.text) }
                    }, enabled = redo.isNotEmpty()) { Text("↷", fontSize = 22.sp, modifier = Modifier.alpha(if (redo.isNotEmpty()) 1f else 0.3f)) }
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
        val value = body ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val plain = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
            val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true)
            TextField(
                value = n.title, onValueChange = { viewModel.edit(title = it) },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = sentences,
                colors = plain, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            val styling = rememberNoteStyling()
            TextField(
                value = value,
                onValueChange = { next ->
                    val before = value.toEdit()
                    val after = next.toEdit()
                    when {
                        // Tapped a checklist box: tick it
                        next.text == value.text -> NoteFormat.tapToggle(before, after)?.let { commit(it.toValue(), forceStep = true) } ?: run { body = next }
                        else -> commit(NoteFormat.afterTyping(before, after).toValue(), forceStep = next.text.length > value.text.length && next.text.getOrNull(next.selection.start - 1) == '\n')
                    }
                },
                placeholder = { Text("Write something… Use the bar below for lists, checkboxes and headings.") },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = sentences,
                visualTransformation = styling,
                colors = plain, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)
            )
            // Words, characters and checklist progress
            val (ticked, boxes) = NoteFormat.checklist(value.text)
            Text(
                listOfNotNull(
                    "${NoteFormat.words(value.text)} words",
                    "${value.text.length} characters",
                    if (boxes > 0) "☑ $ticked/$boxes" else null
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            FormatBar(
                onBullets = { format { NoteFormat.toggleList(it, NoteFormat.ListKind.BULLET) } },
                onNumbers = { format { NoteFormat.toggleList(it, NoteFormat.ListKind.NUMBER) } },
                onChecklist = { format { NoteFormat.toggleList(it, NoteFormat.ListKind.CHECK) } },
                onHeading = { level -> format { NoteFormat.toggleHeading(it, level) } },
                onWrap = { mark -> format { NoteFormat.wrap(it, mark) } },
                onIndent = { dir -> format { NoteFormat.indent(it, dir) } }
            )
        }
    }
}

/** The formatting bar above the keyboard. */
@Composable
private fun FormatBar(
    onBullets: () -> Unit,
    onNumbers: () -> Unit,
    onChecklist: () -> Unit,
    onHeading: (Int) -> Unit,
    onWrap: (String) -> Unit,
    onIndent: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton("H1", "Big heading", bold = true) { onHeading(1) }
        BarButton("H2", "Small heading", bold = true) { onHeading(2) }
        BarButton("B", "Bold", bold = true) { onWrap("**") }
        BarButton("I", "Italic", italic = true) { onWrap("*") }
        BarButton("U", "Underline", underline = true) { onWrap("__") }
        BarButton("S", "Strikethrough", strike = true) { onWrap("~~") }
        BarButton("•", "Bullet list", bold = true) { onBullets() }
        BarButton("1.", "Numbered list", bold = true) { onNumbers() }
        BarButton("☑", "Checklist") { onChecklist() }
        BarButton("⇤", "Move left") { onIndent(-1) }
        BarButton("⇥", "Move right") { onIndent(+1) }
    }
}

@Composable
private fun BarButton(
    label: String,
    description: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> null
            }
        )
    }
}

private fun TextFieldValue.toEdit() = NoteFormat.Edit(text, minOf(selection.start, selection.end), maxOf(selection.start, selection.end))
private fun NoteFormat.Edit.toValue() = TextFieldValue(text, TextRange(start, end))

/** Shows the formatting while you write: headings bigger, lists coloured, ticked items struck through, bold and so on; the marks fade. */
@Composable
private fun rememberNoteStyling(): VisualTransformation {
    val accent = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val done = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(accent, faint, done) { NoteStyling(accent, faint, done) }
}

private class NoteStyling(val accent: Color, val faint: Color, val done: Color) : VisualTransformation {
    private val bold = Regex("""\*\*(.+?)\*\*""")
    private val italic = Regex("""(?<![*\w])\*(?!\*)(\S(?:.*?\S)?)\*(?![*\w])""")
    private val strike = Regex("""~~(.+?)~~""")
    private val underline = Regex("""__(.+?)__""")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            var start = 0
            raw.split('\n').forEach { line ->
                val prefix = NoteFormat.prefixOf(line)
                val bodyStart = start + prefix.length
                val end = start + line.length
                if (prefix.kind != null) {
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), start + prefix.indent.length, bodyStart)
                }
                val heading = NoteFormat.headingPrefix(line.substring(prefix.length))
                if (heading.isNotEmpty()) {
                    addStyle(SpanStyle(fontSize = if (heading == "# ") 24.sp else 19.sp, fontWeight = FontWeight.Bold), bodyStart, end)
                    addStyle(SpanStyle(color = faint), bodyStart, bodyStart + heading.length)
                }
                if (prefix.marker == NoteFormat.CHECKED) {
                    addStyle(SpanStyle(color = done, textDecoration = TextDecoration.LineThrough), bodyStart, end)
                }
                fun marks(regex: Regex, markLength: Int, style: SpanStyle) {
                    regex.findAll(line).forEach { m ->
                        val s = start + m.range.first
                        val e = start + m.range.last + 1
                        addStyle(style, s + markLength, e - markLength)
                        addStyle(SpanStyle(color = faint), s, s + markLength)
                        addStyle(SpanStyle(color = faint), e - markLength, e)
                    }
                }
                marks(bold, 2, SpanStyle(fontWeight = FontWeight.Bold))
                marks(italic, 1, SpanStyle(fontStyle = FontStyle.Italic))
                marks(strike, 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
                marks(underline, 2, SpanStyle(textDecoration = TextDecoration.Underline))
                start = end + 1
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
