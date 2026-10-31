package com.habitgame.feature.dashboard

import com.habitgame.ui.AppIcon
import com.habitgame.ui.IconProvider
import com.habitgame.ui.LocalIconPack
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.habitgame.core.database.entity.Difficulty
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.entity.TaskEntity
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.domain.notification.TaskReminderWorker
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.feature.habits.HabitWithStatus
import com.habitgame.ui.components.*
import com.habitgame.ui.theme.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateFocus: (Long?) -> Unit,
    onNavigateCalendar: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateLevels: () -> Unit = {},
    onNavigateProgress: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focus by viewModel.focus.collectAsState()
    val updater = rememberAppUpdater()
    val update by updater.state.collectAsState()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var skipping by remember { mutableStateOf<DayItem?>(null) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var showAnytime by rememberSaveable { mutableStateOf(false) }

    // Skipping is asked first: it can't earn anything that day
    skipping?.let { dayItem ->
        AlertDialog(
            onDismissRequest = { skipping = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Skip “${dayItem.name}” today?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "It leaves today's list and won't count toward today's circle. No reminders for it today, and no XP or coins. You can undo it under Completed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.skipItem(dayItem); skipping = null }) { Text("Skip for today", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { skipping = null }) { Text("Cancel") } }
        )
    }

    editing?.takeIf { it.isShopping }?.let { task ->
        com.habitgame.feature.tasks.EditShoppingSheet(
            task = task,
            currency = state.challenge.currency,
            reminderText = com.habitgame.domain.notification.ReminderTimes.describe(
                com.habitgame.domain.notification.ReminderTimes.parse(state.user.deadlineReminderTimes)
            ),
            onDismiss = { editing = null },
            onSave = { viewModel.updateTask(it); editing = null },
            onDelete = { viewModel.deleteTask(task); editing = null }
        )
    }
    editing?.takeIf { !it.isShopping }?.let { task ->
        com.habitgame.feature.tasks.EditTaskSheet(
            task = task,
            challengeEnd = state.challenge.endDate,
            deadlineReminders = com.habitgame.domain.notification.ReminderTimes.describe(
                com.habitgame.domain.notification.ReminderTimes.parse(state.user.deadlineReminderTimes)
            ),
            areas = state.areas,
            checklist = state.steps["task:${task.id}"].orEmpty().map { it.text },
            onDismiss = { editing = null },
            onSave = { t, steps -> viewModel.updateTask(t, steps); editing = null },
            onDelete = { viewModel.deleteTask(task); editing = null }
        )
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Room at the bottom so the calendar button doesn't cover the last card
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: photo, greeting and name, then streak, coins and settings on the right.
        // While a booster runs its chip needs the room, so the name moves under the photo and the greeting goes.
        item {
            val now = rememberNow()
            val boosters = state.sessions.filter { it.item.boosterEffect.isNotEmpty() && !it.isFinished(now) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (boosters.isEmpty()) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(
                            imagePath = state.user.profileImageUri,
                            displayName = state.user.displayName,
                            size = 44.dp,
                            initialSize = 18.sp,
                            modifier = Modifier.clickable(onClickLabel = "Open profile", onClick = onNavigateProfile)
                        )
                        Column {
                            Text("Good ${greeting()} ⚡", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.user.displayName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                // The badge picked in the Shop or Settings
                                if (state.user.selectedBadge.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    com.habitgame.ui.PackEmoji(state.user.selectedBadge, 26.dp)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .widthIn(max = 110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "Open profile", onClick = onNavigateProfile),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfileAvatar(
                            imagePath = state.user.profileImageUri,
                            displayName = state.user.displayName,
                            size = 44.dp,
                            initialSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.user.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (state.user.selectedBadge.isNotEmpty()) {
                                Spacer(Modifier.width(3.dp))
                                com.habitgame.ui.PackEmoji(state.user.selectedBadge, 14.dp)
                            }
                        }
                    }
                    // Boosters, streak and coins packed to the right; scrolls sideways if several boosters run
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            boosters.forEach { BoosterChip(it, now) }
                            StreakChip(state.user.currentStreak)
                            CoinBalanceChip(state.user.totalCoins)
                        }
                    }
                }
                if (boosters.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StreakChip(state.user.currentStreak)
                        CoinBalanceChip(state.user.totalCoins)
                    }
                }
                IconButton(onClick = onNavigateSettings) {
                    AppIcon(
                        "Settings",
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // A newer version of HabitGame to install
        val showUpdate = when (val u = update) {
            is com.habitgame.domain.update.UpdateState.Available -> !updater.isSnoozed(u.release)
            com.habitgame.domain.update.UpdateState.None -> false
            is com.habitgame.domain.update.UpdateState.Failed -> u.release != null
            else -> true
        }
        if (showUpdate) item(key = "update") { UpdateBanner() }

        // A focus session running in the background, or one that just finished
        focus.run?.let { run ->
            item(key = "focus") {
                FocusRunningCard(
                    run = run,
                    onOpen = { onNavigateFocus(null) },
                    onPause = viewModel::pauseFocus,
                    onResume = viewModel::resumeFocus
                )
            }
        }
        focus.finished?.let { result ->
            item(key = "focus_done") {
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎉", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Focus session complete!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("+${result.xp} XP · +${result.coins} coins added", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = viewModel::dismissFocusResult) { Text("OK") }
                    }
                }
            }
        }

        // Running Shop timers (app unlocks, entertainment, boosters)
        if (state.sessions.isNotEmpty()) {
            item(key = "sessions") {
                val now = rememberNow()
                val finishedCount = state.sessions.count { it.isFinished(now) }
                LaunchedEffect(finishedCount) {
                    if (finishedCount > 0) viewModel.endFinishedSessions()
                }
                ActiveSessionsCard(state.sessions.filter { !it.isFinished(now) }, now, onExtend = viewModel::extendSession)
            }
        }

        // 100-day challenge board
        item {
            ChallengeCard(
                challenge = state.challenge,
                user = state.user,
                selectedDay = state.selectedDay,
                notes = state.homeNotes,
                charts = state.charts,
                onPickWeek = viewModel::showWeekOf,
                onOpenProgress = onNavigateProgress,
                onDayClick = viewModel::selectDay,
                onStartDateChange = viewModel::setStartDate
            )
        }

        // Today's song or album from the journal
        state.challenge.todayMusic?.let { music ->
            item(key = "music") {
                GameCard {
                    Text("🎵 Today's ${music.kind.label.lowercase()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    MusicCard(music)
                }
            }
        }

        // XP Bar: tap for every level and its shield
        item {
            GameCard(Modifier.clickable(onClickLabel = "See all levels", onClick = onNavigateLevels)) {
                XpProgressBar(
                    level = state.user.level,
                    title = state.user.title,
                    progress = state.xpProgress,
                    currentXp = state.user.currentXp,
                    xpRequired = state.xpRequired
                )
            }
        }

        // Quick actions
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onNavigateFocus(null) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    AppIcon("Timer", contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Focus", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Today: tasks and habits together, in time order. Swipe right for done, left to skip.
        val active = state.dayItems.filter { !it.done && !it.skipped }
        val finished = state.dayItems.filter { it.done || it.skipped }
        item(key = "today_header") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("📋 Today", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active.isEmpty()) "" else "${active.size} left · swipe → done, ← skip",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        if (active.isEmpty()) {
            item(key = "today_empty") {
                GameCard {
                    Text(
                        if (state.dayItems.isEmpty()) "Nothing planned for today." else "🎉 Everything for today is done!",
                        color = if (state.dayItems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else SuccessGreen.readable(),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        items(active, key = { it.key }) { dayItem ->
            SwipeableDayRow(
                onDone = { viewModel.completeItem(dayItem) },
                onSkip = { skipping = dayItem },
                modifier = Modifier.animateItem()
            ) { DayItemRow(dayItem, viewModel, onEdit = { editing = it }, onStartFocus = { onNavigateFocus(it) }, steps = state.stepsFor(dayItem)) }
        }

        // Tasks with no day of their own, e.g. shopping
        if (state.anytimeTasks.isNotEmpty()) {
            item(key = "anytime_header") {
                FoldHeader("🗂️ Any time", state.anytimeTasks.size, showAnytime) { showAnytime = !showAnytime }
            }
            if (showAnytime) {
                item(key = "anytime_note") {
                    Text(
                        "No set day, so they don't count toward the 100-day board. Do them whenever you can.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
                items(state.anytimeTasks, key = { "any_${it.id}" }) { task ->
                    TaskRow(
                        task = task,
                        onComplete = { viewModel.completeTask(task) },
                        onStartFocus = { onNavigateFocus(task.id) },
                        onEdit = { editing = task },
                        modifier = Modifier.animateItem(),
                        steps = state.stepsOf("task", task.id, task.isRecurring)
                    )
                }
            }
        }

        // Done and skipped today, folded away
        if (finished.isNotEmpty()) {
            item(key = "done_header") {
                FoldHeader("✓ Completed", finished.size, showCompleted) { showCompleted = !showCompleted }
            }
            if (showCompleted) {
                items(finished, key = { "done_" + it.key }) { dayItem ->
                    Box(Modifier.animateItem()) {
                        DayItemRow(dayItem, viewModel, onEdit = { editing = it }, onStartFocus = { onNavigateFocus(it) }, steps = state.stepsFor(dayItem))
                    }
                }
            }
        }
    }

    // Calendar: a day's tasks and habits
    FloatingActionButton(
        onClick = onNavigateCalendar,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)
    ) {
        AppIcon("CalendarMonth", contentDescription = "Calendar", modifier = Modifier.size(26.dp))
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeCard(
    challenge: ChallengeUi,
    user: UserEntity,
    selectedDay: Int?,
    notes: List<HabitEntity>,
    charts: com.habitgame.ui.components.WeekCharts,
    onPickWeek: (java.time.LocalDate) -> Unit,
    onOpenProgress: () -> Unit,
    onDayClick: (Int) -> Unit,
    onStartDateChange: (java.time.LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }

    // Pick any day up to today; the graphs then show that day's week
    if (showWeekPicker) {
        val todayMillis = java.time.LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = charts.weekStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= java.time.LocalDate.now().year
            }
        )
        DatePickerDialog(
            onDismissRequest = { showWeekPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPickWeek(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showWeekPicker = false
                }) { Text("Show week") }
            },
            dismissButton = { TextButton(onClick = { showWeekPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState, title = { Text("Pick a day to see its week", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = challenge.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onStartDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState, title = { Text("Challenge start date", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    // Short by default: the day count and this ten-day row. ">" opens the full card.
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (!expanded) {
        GameCard(Modifier.clickable(onClickLabel = "Show the 100-day board and graphs") { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${challenge.currentDay}",
                            fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 44.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "of 100 days", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BoardRow(challenge.scores, challenge.currentDay)
                }
                Spacer(Modifier.width(4.dp))
                ProgressButton(onOpenProgress)
                IconButton(onClick = { expanded = true }) {
                    AppIcon("ChevronRight", contentDescription = "Show more", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    // Swipe between the 100-day board and this week's XP, focus and savings graphs
    val pages = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val colors = boardColors()

    GameCard {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Graphs are as tall as the square board, so the card keeps its size while swiping
            val chartHeight = maxWidth
            // All three pages stay measured, so the card is always the height of the tallest
            HorizontalPager(state = pages, pageSpacing = 24.dp, verticalAlignment = Alignment.Top, beyondViewportPageCount = 3) { page ->
                when (page) {
                    0 -> Column { BoardPage(challenge, selectedDay, onDayClick, onPickDate = { showDatePicker = true }, onOpenProgress = onOpenProgress) }
                    1 -> XpChartPage(charts, chartHeight, onPickWeek = { showWeekPicker = true }, onThisWeek = { onPickWeek(java.time.LocalDate.now()) })
                    2 -> FocusChartPage(charts, chartHeight, onPickWeek = { showWeekPicker = true }, onThisWeek = { onPickWeek(java.time.LocalDate.now()) })
                    else -> HomeSavingsPage(challenge, chartHeight)
                }
            }
        }

        // Page dots: tap one to jump to it
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("100-day board", "XP graph", "Focus graph", "Savings graph").forEachIndexed { index, label ->
                val active = pages.currentPage == index
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Show $label") { scope.launch { pages.animateScrollToPage(index) } }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.sun else colors.ring.copy(alpha = 0.25f))
                    )
                }
            }
        }

        // Stats
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            ChallengeStat("${user.currentStreak}", "day streak", Modifier.weight(1f))
            ChallengeStat(formatMinutes(user.totalFocusMinutes), "focus", Modifier.weight(1f))
            ChallengeStat(challenge.currency + TaskReminderWorker.formatAmount(challenge.savedTotal), "saved", Modifier.weight(1f))
            ChallengeStat("${challenge.journalEntries}", "journal entries", Modifier.weight(1f))
        }

        // Notes from habits that have "Show on Home" turned on
        HabitNotesPager(notes, Modifier.padding(top = 14.dp))

        TextButton(onClick = { expanded = false }, modifier = Modifier.align(Alignment.End)) {
            Text("Show less")
            AppIcon("ExpandLess", contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** A focus session counting down while you're elsewhere: time left, round, and pause/resume. */
@Composable
private fun FocusRunningCard(
    run: com.habitgame.domain.focus.FocusRun,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val now = rememberNow()
    val isBreak = run.phase == com.habitgame.domain.focus.FocusPhase.BREAK
    val ringColor = if (isBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fraction = run.fractionLeft(now)
    GameCard(Modifier.clickable(onClickLabel = "Open the focus timer", onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.5.dp.toPx()
                    val inset = stroke / 2
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        ringColor, -90f, 360f * fraction, false, androidx.compose.ui.geometry.Offset(inset, inset), arc,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Text(if (isBreak) "☕" else "🎯", fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (if (isBreak) "Break" else "Focus") + if (run.plan.rounds > 1) " ${run.round}/${run.plan.rounds}" else "",
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            if (run.paused) {
                Text("⏸ Paused", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(com.habitgame.domain.focus.FocusRun.clock(run.left(now)), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            IconButton(onClick = if (run.paused) onResume else onPause, modifier = Modifier.size(36.dp)) {
                AppIcon(if (run.paused) "PlayArrow" else "Pause", contentDescription = if (run.paused) "Resume" else "Pause", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Opens the Progress page: completion rate, XP, focus and savings over a week up to 100 days. */
@Composable
private fun ProgressButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(androidx.compose.material.icons.Icons.Filled.Insights, contentDescription = "Progress", tint = MaterialTheme.colorScheme.primary)
    }
}

/** Last page of the Home card: savings so far against the plan, over the 100 days. */
@Composable
private fun HomeSavingsPage(challenge: ChallengeUi, chartHeight: androidx.compose.ui.unit.Dp) {
    val money = { v: Double -> challenge.currency + TaskReminderWorker.formatAmount(v) }
    val today = challenge.savingsDays.lastOrNull { it.actual != null }
    Column {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                money(today?.actual ?: 0.0),
                fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 44.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (challenge.savingsGoal > 0) "of ${money(challenge.savingsGoal)}" else "saved",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            when {
                challenge.savingsGoal <= 0 -> "Set a goal in the Savings tab to see your plan."
                today?.difference == null -> "Your plan: ${money(challenge.savingsGoal / 100)} a day."
                kotlin.math.abs(today.difference!!) < 0.5 -> "Right on plan. Tap a day for details."
                today.difference!! > 0 -> "${money(today.difference!!)} ahead of plan. Tap a day for details."
                else -> "${money(-today.difference!!)} behind plan. Tap a day for details."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        SavingsProgressChart(challenge.savingsDays, challenge.savingsGoal, challenge.currency, height = chartHeight - 40.dp)
    }
}

/** First page of the Home card: the day count, this phase's tip and the 100 circles. */
@Composable
private fun ColumnScope.BoardPage(challenge: ChallengeUi, selectedDay: Int?, onDayClick: (Int) -> Unit, onPickDate: () -> Unit, onOpenProgress: () -> Unit) {
    val phase = TrackerPlan.phaseFor(challenge.currentDay)
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            "${challenge.currentDay}",
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp,
            lineHeight = 60.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "of 100 days",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp).weight(1f)
        )
        ProgressButton(onOpenProgress)
        IconButton(onClick = onPickDate) {
            AppIcon(
                "CalendarMonth",
                contentDescription = "Change challenge start date",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(phase.name + ". ") }
            append(phase.tip)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
    )

    HundredDayBoard(
        scores = challenge.scores,
        currentDay = challenge.currentDay,
        selectedDay = selectedDay,
        onDayClick = onDayClick
    )
    Spacer(Modifier.height(10.dp))
    BoardLegend()

    if (selectedDay != null) {
        val date = challenge.dateOf(selectedDay)
        val pct = (challenge.scores.getOrElse(selectedDay - 1) { 0f } * 100).toInt()
        val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
        Text(
            "Day $selectedDay · $dateLabel · " + if (pct > 0) "$pct% done" else "nothing ticked off",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** Swipeable cards with each habit's description, and dots showing which one you're on. */
@Composable
private fun HabitNotesPager(notes: List<HabitEntity>, modifier: Modifier = Modifier) {
    val colors = boardColors()
    if (notes.isEmpty()) {
        Text(
            "Tip: give a habit a description and turn on “Show on Home” to pin it here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier
        )
        return
    }
    val pagerState = rememberPagerState { notes.size }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val habit = notes[page]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.empty)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${habit.iconEmoji} ${habit.name}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TaskTiming.timeOfDay(habit)?.let {
                        Text(TaskTiming.formatTime(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(habit.description, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (notes.size > 1) {
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(notes.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.sun else colors.ring.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 4.dp)) {
        // Long amounts like "ETB12500" get smaller so they don't run into the next stat
        val size = when {
            value.length > 8 -> 14.sp
            value.length > 6 -> 16.sp
            else -> 20.sp
        }
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = size, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 13.sp)
    }
}

private fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) String.format(Locale.getDefault(), "%.1fh", minutes / 60f) else "${minutes}m"

/** A row of today's list: the task or habit, done ones struck through, skipped ones with Undo. */
@Composable
private fun DayItemRow(dayItem: DayItem, viewModel: DashboardViewModel, onEdit: (TaskEntity) -> Unit, onStartFocus: (Long) -> Unit, steps: Pair<Int, Int>? = null) {
    val onUndoSkip = if (dayItem.skipped) ({ viewModel.unskipItem(dayItem) }) else null
    dayItem.task?.let { task ->
        TaskRow(
            task = task,
            onComplete = { viewModel.completeTask(task) },
            onStartFocus = { onStartFocus(task.id) },
            onEdit = { onEdit(task) },
            onUndoSkip = onUndoSkip,
            steps = steps
        )
    }
    dayItem.habit?.let { habit ->
        HabitDashboardRow(
            habitStatus = habit, onToggle = { viewModel.toggleHabit(habit.habit) }, onUndoSkip = onUndoSkip,
            steps = steps
        )
    }
}

/** Swipe left to right: done (green). Right to left: skip for today (asks first). The row springs back either way. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDayRow(onDone: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> onDone()
            SwipeToDismissBoxValue.EndToStart -> onSkip()
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false   // a done item moves to Completed by itself; a skip waits for the answer
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val toSkip = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = when {
                toDone -> SuccessGreen
                toSkip -> WarningAmber
                else -> Color.Transparent
            }
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(color).padding(horizontal = 24.dp),
                contentAlignment = if (toSkip) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (toDone) Text("✓ Done", color = Color.White, fontWeight = FontWeight.Bold)
                if (toSkip) Text("Skip today ⏭", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    ) { content() }
}

/** "✓ Completed (4)" with an arrow: tap to show or hide what's under it. */
@Composable
private fun FoldHeader(title: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (open) "Show less" else "Show", onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$title ($count)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(if (open) "Show less" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        AppIcon(if (open) "ExpandLess" else "ChevronRight", contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TaskRow(
    task: TaskEntity,
    onComplete: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
    onUndoSkip: (() -> Unit)? = null,
    steps: Pair<Int, Int>? = null
) {
    val diffColor = when (task.difficulty) {
        Difficulty.EASY   -> SuccessGreen
        Difficulty.MEDIUM -> CyanXp
        Difficulty.HARD   -> WarningAmber
        Difficulty.EPIC   -> NeonPink
    }
    val time = TaskTiming.timeOfDay(task)
    val struck = task.isCompleted || onUndoSkip != null
    GameCard(modifier.clickable(onClickLabel = "Edit task", onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundCheck(checked = task.isCompleted, onClick = { if (!task.isCompleted) onComplete() }, enabled = !task.isCompleted && onUndoSkip == null)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (time != null) {
                        Text(
                            TaskTiming.formatTime(time),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.widthIn(min = 70.dp)
                        )
                    }
                    Text(
                        task.name, fontWeight = FontWeight.Medium,
                        color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (struck) TextDecoration.LineThrough else null
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyBadge(task.difficulty.name, diffColor)
                    Text("⭐${task.xpReward} XP  🪙${task.coinReward}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    steps?.let { (done, total) -> ChecklistBadge(done, total) }
                }
                if (onUndoSkip != null) SkippedNote(onUndoSkip)
                // Deadline: amber when less than an hour is left
                if (!task.isCompleted) task.deadlineAt?.let { deadline ->
                    val soon = deadline - System.currentTimeMillis() < 60 * 60_000L
                    Text(
                        (if (task.isShopping) "⏳ Buy " else "⏰ Due ") + com.habitgame.domain.tracker.ShoppingText.deadline(deadline),
                        color = if (soon) WarningAmber.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (task.hasFocusTimer && !struck) {
                Button(
                    onClick = onStartFocus,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** "⏭ Skipped today · Undo" on a skipped row. */
@Composable
private fun SkippedNote(onUndo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⏭ Skipped today", color = WarningAmber.readable(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Undo", fontSize = 12.sp) }
    }
}

@Composable
fun HabitDashboardRow(habitStatus: HabitWithStatus, onToggle: () -> Unit, onUndoSkip: (() -> Unit)? = null, steps: Pair<Int, Int>? = null) {
    val habit = habitStatus.habit
    val struck = habitStatus.isCompletedToday || onUndoSkip != null
    GameCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(habit.iconEmoji, fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskTiming.timeOfDay(habit)?.let {
                        Text(
                            TaskTiming.formatTime(it),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.widthIn(min = 70.dp)
                        )
                    }
                    Text(
                        habit.name, fontWeight = FontWeight.Medium,
                        color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (struck) TextDecoration.LineThrough else null
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (habitStatus.streak > 0) {
                        Text("🔥 ${habitStatus.streak} day streak", color = WarningAmber, fontSize = 11.sp)
                    }
                    steps?.let { (done, total) -> ChecklistBadge(done, total) }
                }
                if (onUndoSkip != null) SkippedNote(onUndoSkip)
            }
            if (onUndoSkip == null) RoundCheck(checked = habitStatus.isCompletedToday, onClick = onToggle)
        }
    }
}

@Composable
fun LevelUpDialog(level: Int, title: String, isTierChange: Boolean = false, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("🎉 LEVEL UP!", color = GoldCoin, fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (isTierChange) {
                    // Tier change: show new title prominently
                    Text("🏆", fontSize = 36.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Level $level", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                } else {
                    // Same tier: show level number
                    Text("Level $level", color = MaterialTheme.colorScheme.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Awesome! 🚀", color = MaterialTheme.colorScheme.primary) }
        }
    )
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when {
        h < 12 -> "Morning"
        h < 17 -> "Afternoon"
        else   -> "Evening"
    }
}
