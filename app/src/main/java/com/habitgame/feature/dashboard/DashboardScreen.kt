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
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focus by viewModel.focus.collectAsState()
    val updater = rememberAppUpdater()
    val update by updater.state.collectAsState()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

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
            onDismiss = { editing = null },
            onSave = { viewModel.updateTask(it); editing = null },
            onDelete = { viewModel.deleteTask(task); editing = null }
        )
    }

    // Level up dialog
    if (state.showLevelUpDialog) {
        LevelUpDialog(
            level = state.levelUpLevel,
            title = state.levelUpTitle,
            isTierChange = state.isTierChange
        ) {
            viewModel.dismissLevelUp()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with profile avatar
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Profile avatar (opens Profile)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreakChip(state.user.currentStreak)
                    CoinBalanceChip(state.user.totalCoins)
                    IconButton(onClick = onNavigateSettings) {
                        AppIcon(
                            "Settings",
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                ActiveSessionsCard(state.sessions.filter { !it.isFinished(now) }, now)
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

        // XP Bar
        item {
            GameCard {
                XpProgressBar(
                    level = state.user.level,
                    title = state.user.title,
                    progress = state.xpProgress,
                    currentXp = state.user.currentXp,
                    xpRequired = state.xpRequired
                )
            }
        }

        // Stats row
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreakBadge(state.user.currentStreak, Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .cardSurface(RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.habitgame.ui.PackEmoji("⚡", 20.dp)
                    Text("Combo ×${state.comboCount % 3 + 1}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                }
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
                OutlinedButton(
                    onClick = onNavigateCalendar,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    AppIcon("CalendarMonth", contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Calendar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Tasks / Habits tabs, plus "Any time" for tasks not tied to a day (e.g. shopping)
        val showAnytime = state.anytimeTasks.isNotEmpty()
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    label = {
                        Text(
                            if (showAnytime) "📋 Today (${state.todayTasks.count { !it.isCompleted }})" else "📋 Today (${state.todayTasks.count { !it.isCompleted }} left)",
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                FilterChip(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    label = { Text("🔄 Habits (${state.todayHabits.count { !it.isCompletedToday }})", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (showAnytime) FilterChip(
                    selected = state.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    label = { Text("🗂️ Any time (${state.anytimeTasks.size})", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Content based on selected tab
        if (state.selectedTab == 2 && showAnytime) {
            item {
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
                    onEdit = { editing = task }
                )
            }
        } else if (state.selectedTab != 1) {
            if (state.todayTasks.none { !it.isCompleted }) {
                item {
                    GameCard {
                        Text("🎉 All tasks done! Great work.", color = SuccessGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            items(state.todayTasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onComplete = { viewModel.completeTask(task) },
                    onStartFocus = { onNavigateFocus(task.id) },
                    onEdit = { editing = task }
                )
            }
        } else {
            if (state.todayHabits.isEmpty()) {
                item {
                    GameCard {
                        Text("🔄 No habits for today.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                items(state.todayHabits) { habitStatus ->
                    HabitDashboardRow(
                        habitStatus = habitStatus,
                        onToggle = { viewModel.toggleHabit(habitStatus.habit) }
                    )
                }
            }
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
                    0 -> Column { BoardPage(challenge, selectedDay, onDayClick, onPickDate = { showDatePicker = true }) }
                    1 -> XpChartPage(charts, chartHeight, onPickWeek = { showWeekPicker = true }, onThisWeek = { onPickWeek(java.time.LocalDate.now()) })
                    2 -> FocusChartPage(charts, chartHeight, onPickWeek = { showWeekPicker = true }, onThisWeek = { onPickWeek(java.time.LocalDate.now()) })
                    else -> SavingsChartPage(charts, chartHeight, onPickWeek = { showWeekPicker = true }, onThisWeek = { onPickWeek(java.time.LocalDate.now()) })
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
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        ringColor, -90f, 360f * fraction, false, androidx.compose.ui.geometry.Offset(inset, inset), arc,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Text(if (isBreak) "☕" else "🎯", fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (isBreak) "Break" else "Focus") + if (run.plan.rounds > 1) " · round ${run.round} of ${run.plan.rounds}" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                Text(
                    com.habitgame.domain.focus.FocusRun.clock(run.left(now)) + if (run.paused) " · paused" else " left",
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
            }
            FilledTonalButton(onClick = if (run.paused) onResume else onPause) {
                AppIcon(if (run.paused) "PlayArrow" else "Pause", contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (run.paused) "Resume" else "Pause")
            }
        }
    }
}

/** First page of the Home card: the day count, this phase's tip and the 100 circles. */
@Composable
private fun ColumnScope.BoardPage(challenge: ChallengeUi, selectedDay: Int?, onDayClick: (Int) -> Unit, onPickDate: () -> Unit) {
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

@Composable
fun TaskRow(task: TaskEntity, onComplete: () -> Unit, onStartFocus: () -> Unit, onEdit: () -> Unit = {}) {
    val diffColor = when (task.difficulty) {
        Difficulty.EASY   -> SuccessGreen
        Difficulty.MEDIUM -> CyanXp
        Difficulty.HARD   -> WarningAmber
        Difficulty.EPIC   -> NeonPink
    }
    val time = TaskTiming.timeOfDay(task)
    GameCard(Modifier.clickable(onClickLabel = "Edit task", onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundCheck(checked = task.isCompleted, onClick = { if (!task.isCompleted) onComplete() }, enabled = !task.isCompleted)
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
                    Text(task.name, color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyBadge(task.difficulty.name, diffColor)
                    Text("⭐${task.xpReward} XP  🪙${task.coinReward}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                // Deadline: amber when less than an hour is left
                if (!task.isCompleted) task.deadlineAt?.let { deadline ->
                    val soon = deadline - System.currentTimeMillis() < 60 * 60_000L
                    Text(
                        (if (task.isShopping) "⏳ Buy " else "⏳ Due ") + com.habitgame.domain.tracker.ShoppingText.deadline(deadline),
                        color = if (soon) WarningAmber.readable() else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (task.hasFocusTimer && !task.isCompleted) {
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

@Composable
fun HabitDashboardRow(habitStatus: HabitWithStatus, onToggle: () -> Unit) {
    val habit = habitStatus.habit
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
                    Text(habit.name, color = if (habitStatus.isCompletedToday) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                }
                if (habitStatus.streak > 0) {
                    Text("🔥 ${habitStatus.streak} day streak", color = WarningAmber, fontSize = 11.sp)
                }
            }
            RoundCheck(checked = habitStatus.isCompletedToday, onClick = onToggle)
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
