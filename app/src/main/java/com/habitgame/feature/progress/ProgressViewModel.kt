package com.habitgame.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitgame.core.database.repository.FocusSessionRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.domain.tracker.ProgressStats
import com.habitgame.domain.tracker.SavingsDay
import com.habitgame.domain.tracker.TrackerPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Everything the Progress page shows for the chosen stretch of days. */
data class ProgressUi(
    val rangeDays: Int = 7,
    val dates: List<LocalDate> = emptyList(),
    val completion: List<Float?> = emptyList(),    // % of each day's tasks and habits done
    val xp: List<Int> = emptyList(),
    val focusMinutes: List<Int> = emptyList(),
    val savings: List<SavingsDay> = emptyList(),    // all 100 challenge days
    val goal: Double = 0.0,
    val currency: String = "",
    val challengeStart: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now()
) {
    /** The challenge days (1-based) that fall in the chosen dates, for the savings chart. */
    val savingsDays: IntRange
        get() {
            if (dates.isEmpty()) return 1..TrackerPlan.TOTAL_DAYS
            fun dayOf(d: LocalDate) = (ChronoUnit.DAYS.between(challengeStart, d).toInt() + 1).coerceIn(1, TrackerPlan.TOTAL_DAYS)
            return dayOf(dates.first())..dayOf(dates.last())
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val trackerRepo: TrackerRepository,
    private val focusRepo: FocusSessionRepository
) : ViewModel() {

    private val range = MutableStateFlow(7)

    val state: StateFlow<ProgressUi> = combine(range, trackerRepo.observeChallenge()) { days, challenge -> days to challenge }
        .flatMapLatest { (days, challenge) ->
            val today = LocalDate.now()
            val start = challenge?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            val dates = ProgressStats.range(days, today, start)
            combine(
                trackerRepo.observeDayProgress(),
                trackerRepo.observeDailyXp(dates.first().toString(), dates.last().toString()),
                focusRepo.observeDailyFocus(dates.first().toString(), dates.last().toString()),
                trackerRepo.observeSavings()
            ) { progress, xp, focus, savings ->
                val xpByDay = xp.associate { it.date to it.xp }
                val focusByDay = focus.associate { it.date to it.minutes.toInt() }
                val goal = challenge?.savingsGoal ?: 0.0
                ProgressUi(
                    rangeDays = days,
                    dates = dates,
                    completion = ProgressStats.completionRates(dates, progress, today),
                    xp = dates.map { xpByDay[it.toString()] ?: 0 },
                    focusMinutes = dates.map { focusByDay[it.toString()] ?: 0 },
                    savings = ProgressStats.savings(start, goal, savings, today),
                    goal = goal,
                    currency = challenge?.currency.orEmpty(),
                    challengeStart = start,
                    today = today
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgressUi())

    fun setRange(days: Int) { range.value = days }
}
