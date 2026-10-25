package com.habitgame.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRunTest {
    private val min = 60_000L

    @Test
    fun singleBlockEndsAndIsRewarded() {
        val run = FocusRun.start(FocusPlan(focusMinutes = 25), taskId = null, now = 0)
        assertEquals(emptyList<FocusEvent>(), FocusRun.advance(run, 24 * min).second)
        val (after, events) = FocusRun.advance(run, 25 * min)
        assertNull(after)
        assertEquals(listOf(FocusEvent.FocusDone(1, 25, lastRound = true)), events)
    }

    @Test
    fun pomodoroGoesFocusBreakFocus() {
        val run = FocusRun.start(FocusPlan(focusMinutes = 25, breakMinutes = 5, rounds = 2), null, 0)
        val (onBreak, first) = FocusRun.advance(run, 25 * min)
        assertEquals(listOf(FocusEvent.FocusDone(1, 25, lastRound = false)), first)
        assertEquals(FocusPhase.BREAK, onBreak!!.phase)
        assertEquals(5 * min, onBreak.left(25 * min))

        val (round2, second) = FocusRun.advance(onBreak, 30 * min)
        assertEquals(listOf(FocusEvent.BreakDone(1)), second)
        assertEquals(2, round2!!.round)
        assertEquals(FocusPhase.FOCUS, round2.phase)

        val (done, third) = FocusRun.advance(round2, 55 * min)
        assertNull(done)
        assertEquals(listOf(FocusEvent.FocusDone(2, 25, lastRound = true)), third)
    }

    @Test
    fun catchesUpAfterThePhoneSleptThroughSeveralPhases() {
        val run = FocusRun.start(FocusPlan(focusMinutes = 10, breakMinutes = 5, rounds = 3), null, 0)
        val (after, events) = FocusRun.advance(run, 31 * min)   // 10 focus + 5 break + 10 focus + 5 break, then 1 min in
        assertEquals(2, events.count { it is FocusEvent.FocusDone })
        assertEquals(3, after!!.round)
        assertEquals(9 * min, after.left(31 * min))
    }

    @Test
    fun pausingStopsTheClock() {
        val run = FocusRun.start(FocusPlan(focusMinutes = 25), null, 0)
        val paused = run.pause(10 * min)
        assertTrue(paused.paused)
        assertEquals(emptyList<FocusEvent>(), FocusRun.advance(paused, 999 * min).second)
        assertEquals(15 * min, paused.left(500 * min))
        val resumed = paused.resume(100 * min)
        assertEquals(115 * min, resumed.endsAt)
    }

    @Test
    fun skippingABreakStartsTheNextRound() {
        val run = FocusRun.start(FocusPlan(focusMinutes = 25, breakMinutes = 5, rounds = 2), null, 0)
        val onBreak = FocusRun.advance(run, 25 * min).first!!
        val next = FocusRun.skipBreak(onBreak, 26 * min)
        assertEquals(2, next.round)
        assertEquals(FocusPhase.FOCUS, next.phase)
        assertEquals(51 * min, next.endsAt)
    }

    @Test
    fun customLengthsAreKeptInRange() {
        assertEquals(FocusPlan(240, 60, 12), FocusPlan(999, 999, 99).valid())
        assertEquals(FocusPlan(1, 1, 1), FocusPlan(0, 0, 0).valid())
        assertEquals("1:05:00", FocusRun.clock(65 * min))
        assertEquals("24:59", FocusRun.clock(24 * min + 59_000))
        assertEquals("1h 5m", FocusRun.shortLeft(65 * min))
    }
}
