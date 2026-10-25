package com.habitgame.domain.focus

/**
 * A focus session plan: [rounds] focus blocks of [focusMinutes], with a [breakMinutes] break
 * between them (Pomodoro). One round means a single block with no break.
 */
data class FocusPlan(val focusMinutes: Int = 25, val breakMinutes: Int = 5, val rounds: Int = 1) {
    val totalFocusMinutes: Int get() = focusMinutes * rounds

    fun valid(): FocusPlan = copy(
        focusMinutes = focusMinutes.coerceIn(1, MAX_FOCUS),
        breakMinutes = breakMinutes.coerceIn(1, MAX_BREAK),
        rounds = rounds.coerceIn(1, MAX_ROUNDS)
    )

    companion object {
        const val MAX_FOCUS = 240
        const val MAX_BREAK = 60
        const val MAX_ROUNDS = 12
    }
}

enum class FocusPhase { FOCUS, BREAK }

/** Something that happened while the clock ran: what the timer rewards and announces. */
sealed interface FocusEvent {
    /** Focus block [round] finished: [minutes] of focus to reward. */
    data class FocusDone(val round: Int, val minutes: Int, val lastRound: Boolean) : FocusEvent
    /** The break after [round] is over; the next focus block has started. */
    data class BreakDone(val round: Int) : FocusEvent
}

/**
 * A running (or paused) session. [endsAt] is when the current phase ends, or null while paused,
 * when [pausedLeft] holds the time that was left. Wall-clock based, so it stays right while the
 * app is closed or the phone is asleep.
 */
data class FocusRun(
    val plan: FocusPlan,
    val round: Int = 1,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val phaseMillis: Long = plan.focusMinutes * 60_000L,
    val endsAt: Long? = null,
    val pausedLeft: Long = phaseMillis,
    val taskId: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val xpEarned: Int = 0,
    val coinsEarned: Long = 0,
    val focusedMinutes: Int = 0
) {
    val paused: Boolean get() = endsAt == null

    fun left(now: Long): Long = endsAt?.let { (it - now).coerceAtLeast(0L) } ?: pausedLeft

    /** 1 at the start of the phase, 0 at its end. */
    fun fractionLeft(now: Long): Float = if (phaseMillis <= 0) 0f else (left(now).toFloat() / phaseMillis).coerceIn(0f, 1f)

    fun pause(now: Long): FocusRun = if (paused) this else copy(endsAt = null, pausedLeft = left(now))

    fun resume(now: Long): FocusRun = if (!paused) this else copy(endsAt = now + pausedLeft)

    companion object {
        fun start(plan: FocusPlan, taskId: Long?, now: Long): FocusRun {
            val p = plan.valid()
            val length = p.focusMinutes * 60_000L
            return FocusRun(plan = p, phaseMillis = length, endsAt = now + length, pausedLeft = length, taskId = taskId, startedAt = now)
        }

        /**
         * Moves the run past every phase that has ended by [now] (several, if the phone slept
         * through them). Returns the run afterwards (null when the session is over) and what happened.
         */
        fun advance(run: FocusRun, now: Long): Pair<FocusRun?, List<FocusEvent>> {
            var current = run
            val events = mutableListOf<FocusEvent>()
            while (true) {
                val end = current.endsAt ?: break          // paused: nothing moves
                if (now < end) break
                if (current.phase == FocusPhase.FOCUS) {
                    val last = current.round >= current.plan.rounds
                    events += FocusEvent.FocusDone(current.round, current.plan.focusMinutes, last)
                    if (last) return null to events
                    val length = current.plan.breakMinutes * 60_000L
                    current = current.copy(phase = FocusPhase.BREAK, phaseMillis = length, endsAt = end + length, pausedLeft = length)
                } else {
                    events += FocusEvent.BreakDone(current.round)
                    current = nextFocus(current, end)
                }
            }
            return current to events
        }

        /** Ends the break early and starts the next focus block now. */
        fun skipBreak(run: FocusRun, now: Long): FocusRun =
            if (run.phase != FocusPhase.BREAK) run else nextFocus(run, now)

        private fun nextFocus(run: FocusRun, from: Long): FocusRun {
            val length = run.plan.focusMinutes * 60_000L
            return run.copy(round = run.round + 1, phase = FocusPhase.FOCUS, phaseMillis = length, endsAt = from + length, pausedLeft = length)
        }

        /** "24:59", or "1:04:59" for an hour or more. */
        fun clock(millis: Long): String {
            val total = (millis + 999) / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

        /** "18m", or "1h 5m" — rounded up, so it never says 0m while time is left. */
        fun shortLeft(millis: Long): String {
            val minutes = ((millis + 59_999) / 60_000).toInt()
            return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        }
    }
}
