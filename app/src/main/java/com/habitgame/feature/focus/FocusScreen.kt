package com.habitgame.feature.focus

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.habitgame.domain.focus.FocusPhase
import com.habitgame.domain.focus.FocusPlan
import com.habitgame.domain.focus.FocusResult
import com.habitgame.domain.focus.FocusRun
import com.habitgame.domain.focus.FocusTimer
import com.habitgame.feature.journal.fieldColors
import com.habitgame.ui.AppIcon
import com.habitgame.ui.components.ChoiceChip
import com.habitgame.ui.components.EditorLabel
import com.habitgame.ui.components.GameCard
import com.habitgame.ui.components.rememberNow
import com.habitgame.ui.theme.*

@Composable
fun FocusScreen(taskId: Long? = null, viewModel: FocusViewModel = hiltViewModel()) {
    val timer by viewModel.timerState.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val taskName by viewModel.taskName.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { viewModel.setLinkedTaskId(taskId) }

    timer.finished?.let { result ->
        FocusRewardOverlay(result, onDismiss = viewModel::dismissResult)
        return
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Stop the session?", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "The block you're in now won't be rewarded. Finished blocks keep their XP and coins.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.stop(); confirmStop = false }) { Text("Stop", color = DangerRed.readable()) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going!") } }
        )
    }

    val run = timer.run
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                run == null -> "Focus Session"
                run.phase == FocusPhase.BREAK -> "Break time ☕"
                run.paused -> "Paused ⏸"
                else -> "Stay focused 🎯"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        taskName?.let {
            Spacer(Modifier.height(4.dp))
            Text("For: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(28.dp))

        if (run == null) {
            CircularTimer(fraction = 1f, time = FocusRun.clock(plan.focusMinutes * 60_000L), caption = "Ready", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))
            PlanEditor(plan, viewModel)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::start,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                AppIcon("PlayArrow", contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            RunningTimer(run, viewModel, onStop = { confirmStop = true })
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Focus length, break length and number of rounds, with the rewards they'll earn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanEditor(plan: FocusPlan, viewModel: FocusViewModel) {
    var editing by remember { mutableStateOf<String?>(null) }   // "focus" or "break"
    editing?.let { which ->
        MinutesDialog(
            title = if (which == "focus") "Focus length" else "Break length",
            max = if (which == "focus") FocusPlan.MAX_FOCUS else FocusPlan.MAX_BREAK,
            onDismiss = { editing = null },
            onSet = { if (which == "focus") viewModel.setFocusMinutes(it) else viewModel.setBreakMinutes(it); editing = null }
        )
    }

    GameCard {
        EditorLabel("🎯 Focus length")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val choices = listOf(15, 25, 45, 60)
            choices.forEach { m -> ChoiceChip("$m min", plan.focusMinutes == m) { viewModel.setFocusMinutes(m) } }
            ChoiceChip(if (plan.focusMinutes in choices) "Custom…" else "${plan.focusMinutes} min ✎", plan.focusMinutes !in choices) { editing = "focus" }
        }
        Spacer(Modifier.height(14.dp))

        EditorLabel("🔁 Rounds")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { viewModel.setRounds(plan.rounds - 1) }, enabled = plan.rounds > 1, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−", fontSize = 20.sp) }
            Text(
                "${plan.rounds}",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.width(48.dp)
            )
            OutlinedButton(onClick = { viewModel.setRounds(plan.rounds + 1) }, enabled = plan.rounds < FocusPlan.MAX_ROUNDS, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (plan.rounds == 1) "One block, no break" else "Pomodoro: ${plan.rounds} blocks with breaks between",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
        }

        if (plan.rounds > 1) {
            Spacer(Modifier.height(14.dp))
            EditorLabel("☕ Break length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val choices = listOf(5, 10, 15)
                choices.forEach { m -> ChoiceChip("$m min", plan.breakMinutes == m) { viewModel.setBreakMinutes(m) } }
                ChoiceChip(if (plan.breakMinutes in choices) "Custom…" else "${plan.breakMinutes} min ✎", plan.breakMinutes !in choices) { editing = "break" }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.6f))
        Spacer(Modifier.height(12.dp))
        val total = plan.totalFocusMinutes
        val totalWithBreaks = total + plan.breakMinutes * (plan.rounds - 1)
        Text(
            "${formatDuration(total)} of focus" + if (plan.rounds > 1) " · ${formatDuration(totalWithBreaks)} with breaks" else "",
            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            RewardPreviewBadge("⭐", "${(FocusTimer.xpFor(total) * FocusTimer.FOCUS_MULTIPLIER).toInt()} XP", MaterialTheme.colorScheme.secondary)
            RewardPreviewBadge("🪙", "${(FocusTimer.coinsFor(total) * FocusTimer.FOCUS_MULTIPLIER).toInt()} coins", GoldCoin.readable())
            RewardPreviewBadge("⚡", "×${FocusTimer.FOCUS_MULTIPLIER} focus bonus", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Each finished block is rewarded by itself, plus your streak bonus. The timer keeps going if you leave the app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RunningTimer(run: FocusRun, viewModel: FocusViewModel, onStop: () -> Unit) {
    val now = rememberNow()
    val breakColor = MaterialTheme.colorScheme.tertiary
    CircularTimer(
        fraction = run.fractionLeft(now),
        time = FocusRun.clock(run.left(now)),
        caption = when {
            run.paused -> "Paused"
            run.phase == FocusPhase.BREAK -> "Break"
            else -> "Focus"
        },
        color = if (run.phase == FocusPhase.BREAK) breakColor else MaterialTheme.colorScheme.primary
    )
    if (run.plan.rounds > 1) {
        Spacer(Modifier.height(16.dp))
        // One dot per round: done, current, still to come
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(run.plan.rounds) { i ->
                val round = i + 1
                val done = round < run.round || (round == run.round && run.phase == FocusPhase.BREAK)
                Box(
                    Modifier
                        .size(if (round == run.round) 14.dp else 10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> MaterialTheme.colorScheme.primary
                                round == run.round -> MaterialTheme.colorScheme.primary.copy(0.5f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(0.15f)
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Round ${run.round} of ${run.plan.rounds}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    if (run.xpEarned > 0) {
        Spacer(Modifier.height(8.dp))
        Text("Earned so far: ⭐${run.xpEarned} XP · 🪙${run.coinsEarned}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { if (run.paused) viewModel.resume() else viewModel.pause() },
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            AppIcon(if (run.paused) "PlayArrow" else "Pause", contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (run.paused) "Resume" else "Pause", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed.readable()),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, DangerRed.copy(0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            AppIcon("Stop", contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
    if (run.phase == FocusPhase.BREAK) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::skipBreak) { Text("Skip break and start round ${run.round + 1}") }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "You can leave the app: the timer keeps counting in a bubble and the notification, and rings when it's done.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
    )
}

@Composable
private fun MinutesDialog(title: String, max: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..max
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutes (1–$max)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotEmpty() && !valid) Text("Pick 1 to $max minutes.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { minutes?.let(onSet) }, enabled = valid) { Text("Set", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDuration(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h" + (if (minutes % 60 > 0) " ${minutes % 60}m" else "") else "$minutes min"

/** The big ring: full at the start of a block, empty at its end. */
@Composable
fun CircularTimer(fraction: Float, time: String, caption: String, color: Color) {
    val animated by animateFloatAsState(targetValue = fraction, animationSpec = tween(900, easing = LinearEasing), label = "timerProgress")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val inset = 7.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke, topLeft = topLeft, size = arcSize)
            drawArc(color = color, startAngle = -90f, sweepAngle = animated * 360f, useCenter = false, style = stroke, topLeft = topLeft, size = arcSize)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, fontSize = if (time.length > 5) 40.sp else 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RewardPreviewBadge(icon: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FocusRewardOverlay(result: FocusResult, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text("🎊", fontSize = 72.sp)
            Text("Session Complete!", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${formatDuration(result.minutes)} of focus" + if (result.rounds > 1) " in ${result.rounds} rounds" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Added to your progress:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⭐", fontSize = 32.sp)
                    Text("+${result.xp} XP", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🪙", fontSize = 32.sp)
                    Text("+${result.coins}", color = GoldCoin.readable(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Nice!", fontWeight = FontWeight.Bold) }
        }
    }
}
