package com.habitgame.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.dao.DaySkipDao
import com.habitgame.core.database.entity.AreaEntity
import com.habitgame.core.database.entity.DaySkipEntity
import com.habitgame.core.database.entity.HabitEntity
import com.habitgame.core.database.repository.AreaRepository
import com.habitgame.core.database.repository.FocusSessionRepository
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.tracker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One habit's card: streaks, how often it was done in the range and the one before, and a strip of its days. */
data class HabitCardUi(
    val habit: HabitEntity,
    val streaks: Streaks,
    val rate: HabitRate,
    val previous: HabitRate,
    val strip: List<HabitDayStatus?>
)

data class AreaCardUi(val area: AreaEntity, val rate: HabitRate, val habits: Int)

data class ZoneUi(val zone: DayZone, val rate: HabitRate, val habits: Int)

/** Skipped vs missed in the range, and the habit skipped (missed) most. */
data class MissedUi(
    val skipped: Int = 0,
    val missed: Int = 0,
    val mostSkipped: Pair<String, Int>? = null,
    val mostMissed: Pair<String, Int>? = null
)

/** Everything the Progress page shows for the chosen stretch of days. */
data class ProgressUi(
    val rangeDays: Int = 28,
    val area: String? = null,                       // only this area's habits; null = everything
    val areas: List<AreaEntity> = emptyList(),
    val dates: List<LocalDate> = emptyList(),
    val scores: List<Float?> = emptyList(),         // each day's score, 0–100
    val average: Float? = null,
    val previousAverage: Float? = null,
    val performance: Performance = Performance(),
    val rhythm: List<HabitRate> = List(7) { HabitRate() },
    val zones: List<ZoneUi> = emptyList(),
    val missed: MissedUi = MissedUi(),
    val areaCards: List<AreaCardUi> = emptyList(),
    val habitCards: List<HabitCardUi> = emptyList(),
    val daysTracked: Int = 0,                       // days since the first habit began
    val xp: List<Int> = emptyList(),
    val focusMinutes: List<Int> = emptyList(),
    val savings: List<SavingsDay> = emptyList(),    // all 100 challenge days
    val goal: Double = 0.0,
    val currency: String = "",
    val challengeStart: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val loaded: Boolean = false
) {
    /** The challenge days (1-based) that fall in the chosen dates, for the savings chart. */
    val savingsDays: IntRange
        get() {
            if (dates.isEmpty()) return 1..TrackerPlan.TOTAL_DAYS
            fun dayOf(d: LocalDate) = (ChronoUnit.DAYS.between(challengeStart, d).toInt() + 1).coerceIn(1, TrackerPlan.TOTAL_DAYS)
            return dayOf(dates.first())..dayOf(dates.last())
        }

    companion object {
        /** Patterns need this many days of habits before they mean anything. */
        const val PATTERN_DAYS = 7
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val trackerRepo: TrackerRepository,
    private val focusRepo: FocusSessionRepository,
    private val habitRepo: HabitRepository,
    private val areaRepo: AreaRepository,
    private val skipDao: DaySkipDao
) : ViewModel() {

    private val range = MutableStateFlow(28)
    private val area = MutableStateFlow<String?>(null)

    private data class HabitData(
        val habits: List<HabitEntity>,
        val done: Map<Long, Set<String>>,
        val skipped: Map<Long, Set<String>>,
        val areas: List<AreaEntity>,
        val progress: List<com.habitgame.core.database.entity.DayProgressEntity>
    )

    val state: StateFlow<ProgressUi> = combine(range, area, trackerRepo.observeChallenge()) { days, area, challenge -> Triple(days, area, challenge) }
        .flatMapLatest { (days, areaName, challenge) ->
            val today = LocalDate.now()
            val start = challenge?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            val dates = ProgressStats.range(days, today, start)
            val habitData = combine(
                habitRepo.observeActiveHabits(),
                habitRepo.observeAllCompletions(),
                skipDao.observeAll(),
                areaRepo.areas,
                trackerRepo.observeDayProgress()
            ) { habits, completions, skips, areas, progress ->
                HabitData(
                    habits,
                    completions.groupBy { it.habitId }.mapValues { (_, list) -> list.map { it.date }.toSet() },
                    skips.filter { it.kind == DaySkipEntity.KIND_HABIT }.groupBy { it.itemId }.mapValues { (_, list) -> list.map { it.date }.toSet() },
                    areas,
                    progress
                )
            }
            val charts = combine(
                trackerRepo.observeDailyXp(dates.first().toString(), dates.last().toString()),
                focusRepo.observeDailyFocus(dates.first().toString(), dates.last().toString()),
                trackerRepo.observeSavings()
            ) { xp, focus, savings -> Triple(xp, focus, savings) }

            combine(habitData, charts) { data, (xp, focus, savings) ->
                val xpByDay = xp.associate { it.date to it.xp }
                val focusByDay = focus.associate { it.date to it.minutes.toInt() }
                val goal = challenge?.savingsGoal ?: 0.0
                build(days, areaName, dates, today, data).copy(
                    xp = dates.map { xpByDay[it.toString()] ?: 0 },
                    focusMinutes = dates.map { focusByDay[it.toString()] ?: 0 },
                    savings = ProgressStats.savings(start, goal, savings, today),
                    goal = goal,
                    currency = challenge?.currency.orEmpty(),
                    challengeStart = start
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUi())

    private fun build(days: Int, areaName: String?, dates: List<LocalDate>, today: LocalDate, data: HabitData): ProgressUi {
        val previousDates = HabitStats.previous(dates)
        fun done(h: HabitEntity) = data.done[h.id].orEmpty()
        fun skipped(h: HabitEntity) = data.skipped[h.id].orEmpty()
        val habits = if (areaName == null) data.habits else data.habits.filter { it.groupName == areaName }
        val now = habits.associateWith { HabitStats.days(it, dates, today, done(it), skipped(it)) }
        val before = habits.associateWith { HabitStats.days(it, previousDates, today, done(it), skipped(it)) }

        // Everything: the share of each day's tasks and habits done (as on the 100-day board). One area: its habits.
        val scores = if (areaName == null) ProgressStats.completionRates(dates, data.progress, today) else HabitStats.dayScores(now.values.toList(), dates.size)
        val previousScores = if (areaName == null) ProgressStats.completionRates(previousDates, data.progress, today) else HabitStats.dayScores(before.values.toList(), previousDates.size)

        val zones = DayZone.entries.mapNotNull { zone ->
            val inZone = habits.filter { DayZone.of(TaskTiming.timeOfDay(it)) == zone }
            if (inZone.isEmpty()) null else ZoneUi(zone, inZone.fold(HabitRate()) { acc, h -> acc + HabitStats.rate(now.getValue(h)) }, inZone.size)
        }
        val skipCounts = habits.associateWith { h -> now.getValue(h).count { it == HabitDayStatus.SKIPPED } }
        val missCounts = habits.associateWith { h -> now.getValue(h).count { it == HabitDayStatus.MISSED } }
        val missed = MissedUi(
            skipped = skipCounts.values.sum(),
            missed = missCounts.values.sum(),
            mostSkipped = skipCounts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { it.key.name to it.value },
            mostMissed = missCounts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { it.key.name to it.value }
        )
        val areaCards = if (areaName != null) emptyList() else data.areas.mapNotNull { area ->
            val inArea = data.habits.filter { it.groupName == area.name }
            if (inArea.isEmpty()) null else AreaCardUi(area, inArea.fold(HabitRate()) { acc, h -> acc + HabitStats.rate(now.getValue(h)) }, inArea.size)
        }
        val cards = habits.map { h ->
            val statuses = now.getValue(h)
            HabitCardUi(
                habit = h,
                streaks = HabitStats.streaks(h, today, done(h), skipped(h)),
                rate = HabitStats.rate(statuses),
                previous = HabitStats.rate(before.getValue(h)),
                strip = statuses.filterIndexed { i, _ -> !dates[i].isAfter(today) }.takeLast(STRIP_DAYS)
            )
        }.sortedWith(compareByDescending<HabitCardUi> { it.rate.percent ?: -1f }.thenBy { it.habit.name })
        val firstDay = data.habits.minOfOrNull { HabitStats.startOf(it, done(it)) }

        return ProgressUi(
            rangeDays = days,
            area = areaName,
            areas = data.areas,
            dates = dates,
            scores = scores,
            average = HabitStats.average(scores),
            previousAverage = HabitStats.average(previousScores),
            performance = HabitStats.performance(scores),
            rhythm = HabitStats.weeklyRhythm(now.values.toList(), dates),
            zones = zones,
            missed = missed,
            areaCards = areaCards,
            habitCards = cards,
            daysTracked = firstDay?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 0,
            today = today,
            loaded = true
        )
    }

    fun setRange(days: Int) { range.value = days }
    fun setArea(name: String?) { area.value = name }

    companion object {
        const val STRIP_DAYS = 30
    }
}
