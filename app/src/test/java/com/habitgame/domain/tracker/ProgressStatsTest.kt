package com.habitgame.domain.tracker

import com.habitgame.core.database.entity.DayProgressEntity
import com.habitgame.core.database.entity.SavingsEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressStatsTest {
    private val start = LocalDate.of(2026, 10, 1)

    private fun saved(day: Int, amount: Double) = SavingsEntryEntity(amount = amount, date = start.plusDays(day - 1L).toString())

    @Test
    fun targetGrowsByTheDailyAmountAndStopsAtTheGoal() {
        val days = ProgressStats.savings(start, 15_000.0, emptyList(), start.plusDays(200))
        assertEquals(100, days.size)
        assertEquals(150.0, days[0].target, 0.001)
        assertEquals(1_500.0, days[9].target, 0.001)
        assertEquals(15_000.0, days[99].target, 0.001)
    }

    @Test
    fun actualAddsUpDepositsAndFlattensOnMissedDays() {
        val entries = listOf(saved(1, 150.0), saved(2, 100.0), saved(2, 100.0), saved(4, 500.0))  // two deposits on day 2, none on day 3
        val days = ProgressStats.savings(start, 15_000.0, entries, start.plusDays(4))              // today = day 5
        assertEquals(150.0, days[0].actual!!, 0.001)
        assertEquals(350.0, days[1].actual!!, 0.001)
        assertEquals(350.0, days[2].actual!!, 0.001)    // missed day: flat
        assertEquals(850.0, days[3].actual!!, 0.001)
        assertEquals(850.0 - 750.0, days[4].difference!!, 0.001)  // day 5: ahead by 100
        assertNull(days[5].actual)                       // still to come
    }

    @Test
    fun extraFundsAreMoneyAddedByHandNotByTasks() {
        val entries = listOf(
            SavingsEntryEntity(amount = 150.0, date = start.toString(), taskId = 7),  // from a savings task
            saved(1, 300.0),
            saved(2, -50.0)                                                       // a withdrawal isn't extra
        )
        val days = ProgressStats.savings(start, 15_000.0, entries, start.plusDays(2))
        assertEquals(300.0, days[0].extra, 0.001)
        assertEquals(0.0, days[1].extra, 0.001)
        assertEquals(400.0, days[1].actual!!, 0.001)
    }

    @Test
    fun withdrawalsLowerItAndEarlierMoneyCountsFromDayOne() {
        val entries = listOf(
            SavingsEntryEntity(amount = 300.0, date = start.minusDays(3).toString()),
            saved(2, -100.0)
        )
        val days = ProgressStats.savings(start, 15_000.0, entries, start.plusDays(1))
        assertEquals(300.0, days[0].actual!!, 0.001)
        assertEquals(200.0, days[1].actual!!, 0.001)
    }

    @Test
    fun completionRatesSkipDaysWithNothingPlanned() {
        val today = LocalDate.of(2026, 10, 28)
        val dates = ProgressStats.range(7, today, start)
        assertEquals(today.minusDays(6), dates.first())
        assertEquals(today, dates.last())
        val rates = ProgressStats.completionRates(
            dates,
            listOf(DayProgressEntity(today.toString(), 3, 4), DayProgressEntity(today.minusDays(1).toString(), 0, 0)),
            today
        )
        assertEquals(75f, rates.last()!!, 0.001f)
        assertNull(rates[5])
        assertNull(rates[0])
    }

    @Test
    fun hundredDaysIsTheWholeChallenge() {
        val dates = ProgressStats.range(100, LocalDate.of(2026, 10, 28), start)
        assertEquals(100, dates.size)
        assertEquals(start, dates.first())
    }
}
