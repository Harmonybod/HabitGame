package com.habitgame.widget

import android.content.Context
import com.habitgame.core.database.entity.UserEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import com.habitgame.core.database.repository.TrackerRepository
import com.habitgame.core.database.repository.UserRepository
import com.habitgame.domain.engine.HabitManager
import com.habitgame.domain.engine.TaskCompletionManager
import com.habitgame.domain.engine.UserProgressEngine
import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerManager
import com.habitgame.domain.tracker.TrackerPlan
import com.habitgame.ui.theme.WidgetPalette
import com.habitgame.ui.theme.widgetPalette
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/** Widgets aren't Hilt screens, so they reach the app's repositories through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun userRepo(): UserRepository
    fun taskRepo(): TaskRepository
    fun habitRepo(): HabitRepository
    fun trackerRepo(): TrackerRepository
    fun habitManager(): HabitManager
    fun taskCompletionManager(): TaskCompletionManager
}

fun widgetDeps(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** One line on the checklist widget: a task or a habit due today. */
data class ChecklistItem(val id: Long, val isHabit: Boolean, val name: String, val time: LocalTime?, val done: Boolean)

/** What every widget needs to draw itself in the user's look. */
data class WidgetLook(val palette: WidgetPalette, val iconPack: String)

object WidgetData {

    suspend fun user(context: Context): UserEntity = widgetDeps(context).userRepo().getUser() ?: UserEntity()

    fun look(user: UserEntity) = WidgetLook(widgetPalette(user.selectedTheme, user.accentColorHex, user.glassEnabled), user.selectedIconPack)

    /** Today's tasks and habits, the same list as Home: undone first, by time. */
    suspend fun checklist(context: Context): List<ChecklistItem> {
        val deps = widgetDeps(context)
        val today = LocalDate.now()
        val tasks = deps.taskRepo().getAllTasks()
            .filter { TaskTiming.countsToward(it, today) || (!it.isCompleted && !it.isRecurring && TaskTiming.scheduledDate(it) == null) }
            .map { ChecklistItem(it.id, false, it.name, TaskTiming.timeOfDay(it), it.isCompleted) }
        val habits = deps.habitManager().filterTodaysHabits(deps.habitRepo().observeActiveHabits().first())
            .map { ChecklistItem(it.id, true, it.name, TaskTiming.timeOfDay(it), deps.habitRepo().isHabitCompletedToday(it.id)) }
        return (tasks + habits).sortedWith(compareBy<ChecklistItem>({ it.done }, { it.time ?: LocalTime.MAX }))
    }

    /** Ticks a task or habit from the widget; a habit can also be unticked, like in the app. */
    suspend fun toggle(context: Context, id: Long, isHabit: Boolean) {
        val deps = widgetDeps(context)
        if (isHabit) {
            val habit = deps.habitRepo().getAllHabits().firstOrNull { it.id == id } ?: return
            if (deps.habitRepo().isHabitCompletedToday(id)) deps.habitManager().uncompleteHabit(id)
            else deps.habitManager().completeHabit(habit)
        } else {
            deps.taskCompletionManager().complete(id)
        }
    }

    /** The 100-day board: how much of each day was done, and which day it is. */
    data class Board(val scores: List<Float>, val currentDay: Int, val phaseName: String)

    suspend fun board(context: Context): Board {
        val tracker = widgetDeps(context).trackerRepo()
        val today = LocalDate.now()
        val start = tracker.getChallenge()?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val byDate = tracker.observeDayProgress().first().associateBy { it.date }
        val day = TrackerManager.dayNumber(start.toString(), today)
        return Board(
            scores = List(TrackerPlan.TOTAL_DAYS) { i -> byDate[start.plusDays(i.toLong()).toString()]?.fraction ?: 0f },
            currentDay = day,
            phaseName = TrackerPlan.phaseFor(day.coerceIn(1, TrackerPlan.TOTAL_DAYS)).name
        )
    }

    /** Money saved against the goal. */
    data class Savings(val saved: Double, val goal: Double, val currency: String, val daysLeft: Int) {
        val fraction: Float get() = if (goal <= 0) 0f else (saved / goal).toFloat().coerceIn(0f, 1f)
        val perDay: Double get() = (goal - saved).coerceAtLeast(0.0) / daysLeft.coerceAtLeast(1)
    }

    suspend fun savings(context: Context): Savings {
        val tracker = widgetDeps(context).trackerRepo()
        val challenge = tracker.getChallenge()
        val day = challenge?.let { TrackerManager.dayNumber(it.startDate, LocalDate.now()) } ?: 1
        return Savings(
            saved = tracker.observeSavings().first().sumOf { it.amount },
            goal = challenge?.savingsGoal ?: 0.0,
            currency = challenge?.currency.orEmpty(),
            daysLeft = TrackerPlan.TOTAL_DAYS - day + 1
        )
    }

    fun xpFraction(user: UserEntity): Float {
        val needed = UserProgressEngine.xpRequired(user.level)
        return if (needed <= 0) 0f else (user.currentXp.toFloat() / needed).coerceIn(0f, 1f)
    }
}
