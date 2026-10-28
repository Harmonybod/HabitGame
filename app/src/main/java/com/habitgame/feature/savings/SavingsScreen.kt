package com.habitgame.feature.savings

import com.habitgame.ui.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.core.database.entity.SavingsEntryEntity
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.ProgressRing
import com.habitgame.ui.components.RoundCheck
import com.habitgame.ui.components.SavingsProgressChart
import com.habitgame.ui.components.boardColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SavingsScreen(viewModel: SavingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showGoalDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    // Opened from the Savings widget's "+ Add"
    val widgetTarget by com.habitgame.widget.WidgetLaunch.pending.collectAsState()
    LaunchedEffect(widgetTarget) {
        if (com.habitgame.widget.WidgetLaunch.consume(com.habitgame.widget.WidgetLaunch.SAVINGS_ADD)) showDepositDialog = true
    }
    var showTaskDialog by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf<TaskEntity?>(null) }

    if (showGoalDialog) {
        GoalDialog(state.goal, state.currency, onDismiss = { showGoalDialog = false }) { goal, currency ->
            viewModel.setGoal(goal, currency); showGoalDialog = false
        }
    }
    if (showDepositDialog) {
        AmountDialog(
            title = "Add money",
            initialAmount = 0.0,
            withNote = true,
            onDismiss = { showDepositDialog = false }
        ) { amount, note -> viewModel.addDeposit(amount, note); showDepositDialog = false }
    }
    if (showTaskDialog) {
        SavingsTaskDialog(
            suggestedAmount = state.perDayToFinish,
            onDismiss = { showTaskDialog = false }
        ) { name, amount, time, repeat -> viewModel.addSavingsTask(name, amount, time, repeat); showTaskDialog = false }
    }
    completing?.let { task ->
        AmountDialog(
            title = "How much did you put aside?",
            initialAmount = task.savingAmount,
            withNote = false,
            onDismiss = { completing = null }
        ) { amount, _ -> viewModel.completeTask(task, amount); completing = null }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Savings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Put a little aside every day. Each savings task reminds you and earns XP.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        // Goal ring
        item {
            GameCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(progress = state.progress, modifier = Modifier.size(190.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.money(state.total), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (state.goal > 0) "of ${state.money(state.goal)}" else "saved",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.goal > 0) {
                                Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        when {
                            state.goal <= 0 -> "Set a goal for your 100 days to see how much to save each day."
                            state.remaining <= 0 -> "Goal reached. Amazing! 🎉"
                            else -> "${state.money(state.remaining)} to go · about ${state.money(Math.ceil(state.perDayToFinish * 100) / 100)} a day for ${state.daysLeft} days"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.savedToday > 0) {
                        Text("Saved today: ${state.money(state.savedToday)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showGoalDialog = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(if (state.goal > 0) "Edit goal" else "Set goal")
                        }
                        Button(onClick = { showDepositDialog = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("Add money", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Saved so far against the plan, over the 100 days
        if (state.days.isNotEmpty()) item {
            GameCard {
                Text("📈 100-day progress", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Tap a day to see what you'd saved by then and how it compares with the plan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                SavingsProgressChart(state.days, state.goal, state.currency, height = 240.dp)
            }
        }

        // Savings tasks
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Savings tasks", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { showTaskDialog = true }) {
                    AppIcon("Add", contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New task")
                }
            }
        }
        if (state.tasks.isEmpty()) {
            item { Text("No savings tasks yet. Add one and you'll get a reminder at its time.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.tasks, key = { it.id }) { task ->
            SavingsTaskRow(
                task = task,
                money = state::money,
                onComplete = { completing = task },
                onDelete = { viewModel.deleteTask(task) }
            )
        }

        // History
        item {
            Text("History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        }
        if (state.entries.isEmpty()) {
            item { Text("Money you put aside will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.entries, key = { it.id }) { entry ->
            DepositRow(entry, state::money) { viewModel.deleteDeposit(entry) }
        }
    }
}

@Composable
private fun SavingsTaskRow(task: TaskEntity, money: (Double) -> String, onComplete: () -> Unit, onDelete: () -> Unit) {
    val time = TaskTiming.timeOfDay(task)
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundCheck(checked = task.isCompleted, onClick = onComplete, enabled = !task.isCompleted)
            Column(Modifier.weight(1f)) {
                Text(task.name, fontWeight = FontWeight.Medium, color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface)
                Text(
                    listOfNotNull(
                        time?.let { "🔔 " + TaskTiming.formatTime(it) },
                        if (task.isRecurring) "every day" else "once",
                        if (task.savingAmount > 0) money(task.savingAmount) else null
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                AppIcon("Delete", contentDescription = "Delete ${task.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
}

@Composable
private fun DepositRow(entry: SavingsEntryEntity, money: (Double) -> String, onDelete: () -> Unit) {
    val date = runCatching { LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) }.getOrDefault(entry.date)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.note, color = MaterialTheme.colorScheme.onSurface)
            Text(date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+" + money(entry.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onDelete) {
            AppIcon("Delete", contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun GoalDialog(goal: Double, currency: String, onDismiss: () -> Unit, onSave: (Double, String) -> Unit) {
    var goalText by remember { mutableStateOf(if (goal > 0) TaskReminderWorker.formatAmount(goal) else "") }
    var currencyText by remember { mutableStateOf(currency) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Savings goal for 100 days") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = goalText, onValueChange = { goalText = cleanNumber(it) },
                    label = { Text("Goal") }, placeholder = { Text("e.g. 500") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currencyText, onValueChange = { currencyText = it.take(4) },
                    label = { Text("Currency symbol (optional)") }, placeholder = { Text("$, €, ETB…") }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                Text("Your daily “Put money aside” task will be set to goal ÷ 100.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(goalText.toDoubleOrNull() ?: 0.0, currencyText) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AmountDialog(
    title: String,
    initialAmount: Double,
    withNote: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (initialAmount > 0) TaskReminderWorker.formatAmount(initialAmount) else "") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = cleanNumber(it) },
                    label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (withNote) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") }, singleLine = true,
                        colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(amountText.toDoubleOrNull() ?: 0.0, note) }) { Text(if (withNote) "Add" else "Done ✓") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsTaskDialog(
    suggestedAmount: Double,
    onDismiss: () -> Unit,
    onSave: (String, Double, LocalTime, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("Put money aside") }
    var amountText by remember { mutableStateOf(if (suggestedAmount > 0) TaskReminderWorker.formatAmount(Math.ceil(suggestedAmount * 100) / 100) else "") }
    var repeatDaily by remember { mutableStateOf(true) }
    var pickingTime by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = 20, initialMinute = 0)

    if (pickingTime) {
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = { TimePicker(state = timeState) },
            confirmButton = { TextButton(onClick = { pickingTime = false }) { Text("OK") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("New savings task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Task") }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = cleanNumber(it) },
                    label = { Text("Amount to save") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text("🔔 Remind me at " + TaskTiming.formatTime(LocalTime.of(timeState.hour, timeState.minute)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat every day", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = repeatDaily, onCheckedChange = { repeatDaily = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, amountText.toDoubleOrNull() ?: 0.0, LocalTime.of(timeState.hour, timeState.minute), repeatDaily)
            }) { Text("Add task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun cleanNumber(input: String): String {
    val normalized = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = normalized.indexOf('.')
    return if (firstDot < 0) normalized else normalized.substring(0, firstDot + 1) + normalized.substring(firstDot + 1).replace(".", "")
}
