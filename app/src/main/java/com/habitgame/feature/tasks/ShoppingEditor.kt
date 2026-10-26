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
import com.habitgame.core.database.entity.Difficulty
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.domain.engine.EconomyManager
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.ShoppingText
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.components.ChoiceChip
import com.habitgame.ui.components.EditorActions
import com.habitgame.ui.components.EditorLabel
import com.habitgame.ui.theme.DangerRed
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The price range and deadline a shopping item has while it's being added or edited. */
class ShoppingForm(priceMin: Double = 0.0, priceMax: Double = 0.0, deadlineAt: Long? = null) {
    var priceMin by mutableStateOf(if (priceMin > 0) TaskReminderWorker.formatAmount(priceMin) else "")
    var priceMax by mutableStateOf(if (priceMax > 0) TaskReminderWorker.formatAmount(priceMax) else "")
    var deadline by mutableStateOf(deadlineAt?.let { Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() })

    val deadlineMillis: Long? get() = deadline?.let { TaskTiming.millisOf(it) }
    val deadlinePassed: Boolean get() = deadline?.isBefore(LocalDateTime.now()) == true

    /** [base] with this form's price range and deadline. */
    fun applyTo(base: TaskEntity): TaskEntity = base.copy(
        priceMin = ShoppingText.parseAmount(priceMin),
        priceMax = ShoppingText.parseAmount(priceMax),
        deadlineAt = deadlineMillis
    )
}

private fun amountInput(text: String) = text.replace(',', '.').filter { it.isDigit() || it == '.' }.take(10)

/**
 * Price range (optional) and deadline (off by default). With a deadline, the item is removed if it
 * isn't bought in time; [reminderText] says when the reminders come.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingFields(form: ShoppingForm, currency: String, reminderText: String) {
    EditorLabel("💲 Price range (optional)")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = form.priceMin, onValueChange = { form.priceMin = amountInput(it) },
            label = { Text("From") }, prefix = { if (currency.isNotBlank()) Text(currency) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f), colors = fieldColors()
        )
        OutlinedTextField(
            value = form.priceMax, onValueChange = { form.priceMax = amountInput(it) },
            label = { Text("To") }, prefix = { if (currency.isNotBlank()) Text(currency) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f), colors = fieldColors()
        )
    }

    // Deadline: none by default, so the item can be bought any time
    DeadlineField(
        deadline = form.deadline,
        onChange = { form.deadline = it },
        offText = "None: buy it whenever you like",
        onText = "Not bought by then? It's removed from your list.",
        reminderText = reminderText
    )
}

/**
 * A "⏳ Deadline" switch (off = no deadline) with a date and time once it's on. [onText] says what
 * happens at the deadline; [reminderText] when the reminders come.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineField(
    deadline: LocalDateTime?,
    onChange: (LocalDateTime?) -> Unit,
    offText: String,
    onText: String,
    reminderText: String
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("⏳ Deadline", color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (deadline == null) offText else onText,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = deadline != null,
            onCheckedChange = { on ->
                // Starts at tomorrow, at the next whole hour
                onChange(if (on) LocalDateTime.now().plusDays(1).plusHours(1).withMinute(0).withSecond(0).withNano(0) else null)
            }
        )
    }
    deadline?.let { at ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { pickingDate = true }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) { Text("📅 " + at.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurface) }
            OutlinedButton(
                onClick = { pickingTime = true }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) { Text("⏰ " + TaskTiming.formatTime(at.toLocalTime()), color = MaterialTheme.colorScheme.onSurface) }
        }
        if (at.isBefore(LocalDateTime.now())) {
            Text("That time has already passed. Pick a later one.", color = DangerRed, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("🔔 Reminders $reminderText.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        if (pickingDate) {
            val today = LocalDate.now()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = at.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
                }
            )
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atTime(at.toLocalTime()))
                        }
                        pickingDate = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
            ) { DatePicker(state = state) }
        }
        if (pickingTime) {
            val state = rememberTimePickerState(initialHour = at.hour, initialMinute = at.minute)
            AlertDialog(
                onDismissRequest = { pickingTime = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Deadline time") },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = { onChange(at.toLocalDate().atTime(LocalTime.of(state.hour, state.minute))); pickingTime = false }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } }
            )
        }
    }
}

/**
 * The "🎯 Focus session" switch: whether the task gets a Start button that opens the focus timer,
 * and for how many minutes ([minutes] 0 = off).
 */
@Composable
fun FocusSessionField(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("🎯 Focus session", color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (minutes > 0) "Start a focus timer from this task" else "Off: just tick it off when it's done",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(checked = minutes > 0, onCheckedChange = { on -> onChange(if (on) DEFAULT_FOCUS_MINUTES else 0) })
    }
    if (minutes > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (FOCUS_CHOICES + minutes).distinct().sorted().forEach { m -> ChoiceChip("$m min", minutes == m) { onChange(m) } }
        }
    }
}

const val DEFAULT_FOCUS_MINUTES = 25
private val FOCUS_CHOICES = listOf(15, 25, 45, 60)

/** Difficulty sets the XP and coins for buying the item. */
@Composable
fun DifficultyPicker(difficulty: Difficulty, onChange: (Difficulty) -> Unit) {
    EditorLabel("Difficulty")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Difficulty.entries.forEach { d -> ChoiceChip(d.name.lowercase().replaceFirstChar { it.uppercase() }, difficulty == d) { onChange(d) } }
    }
    Text(
        "Buying it earns ⭐${EconomyManager.xpForDifficulty(difficulty)} XP and 🪙${EconomyManager.coinsForDifficulty(difficulty)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
    )
}

/** Change a shopping item's name, difficulty, price range and deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShoppingSheet(
    task: TaskEntity,
    currency: String,
    reminderText: String,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(task.id) { mutableStateOf(task.name) }
    var difficulty by remember(task.id) { mutableStateOf(task.difficulty) }
    val form = remember(task.id) { ShoppingForm(task.priceMin, task.priceMax, task.deadlineAt) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Edit shopping item", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("What to buy") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors()
            )
            DifficultyPicker(difficulty) { difficulty = it }
            ShoppingFields(form, currency, reminderText)
            EditorActions(
                saveLabel = "Save changes",
                onSave = {
                    if (name.isBlank() || form.deadlinePassed) return@EditorActions
                    onSave(
                        form.applyTo(task).copy(
                            name = name.trim(),
                            difficulty = difficulty,
                            xpReward = EconomyManager.xpForDifficulty(difficulty),
                            coinReward = EconomyManager.coinsForDifficulty(difficulty).toInt()
                        )
                    )
                },
                onDelete = onDelete
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
