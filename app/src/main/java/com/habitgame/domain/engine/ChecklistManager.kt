package com.habitgame.domain.engine

import com.habitgame.core.database.dao.ChecklistDao
import com.habitgame.core.database.entity.ChecklistItemEntity
import com.habitgame.core.database.entity.DaySkipEntity
import com.habitgame.core.database.repository.HabitRepository
import com.habitgame.core.database.repository.TaskRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The rules for checklists, kept apart from the database so they can be tested. */
object Checklist {
    /**
     * A step counts as ticked today. Habits and repeating tasks start each day unticked
     * ([repeats]); a one-time task keeps its ticks until it's done.
     */
    fun isChecked(item: ChecklistItemEntity, repeats: Boolean, today: String): Boolean =
        if (repeats) item.checkedDate == today else item.checkedDate != null

    /** How many of [items] are ticked today. */
    fun progress(items: List<ChecklistItemEntity>, repeats: Boolean, today: String): Int =
        items.count { isChecked(it, repeats, today) }

    /**
     * The checklist after editing it to [texts]: steps whose text didn't change keep their
     * ids and ticks. Returns what to save and the ids of removed steps.
     */
    fun merge(
        existing: List<ChecklistItemEntity>,
        texts: List<String>,
        kind: String,
        ownerId: Long
    ): Pair<List<ChecklistItemEntity>, List<Long>> {
        val unused = existing.toMutableList()
        val kept = texts.map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, text ->
            val match = unused.firstOrNull { it.text == text }
            if (match != null) {
                unused.remove(match)
                match.copy(sortOrder = index, ownerKind = kind, ownerId = ownerId)
            } else {
                ChecklistItemEntity(ownerKind = kind, ownerId = ownerId, text = text, sortOrder = index)
            }
        }
        return kept to unused.map { it.id }
    }
}

/**
 * Checklists on tasks and habits. Ticking the last step completes the task or habit (XP, coins
 * and all); unticking a step of a habit done today un-does the habit.
 */
@Singleton
class ChecklistManager @Inject constructor(
    private val dao: ChecklistDao,
    private val habitRepo: HabitRepository,
    private val taskRepo: TaskRepository,
    private val habitManager: HabitManager,
    private val taskCompletionManager: TaskCompletionManager
) {
    /** Replaces the checklist of a task or habit with [texts], keeping ticks on unchanged steps. */
    suspend fun save(kind: String, ownerId: Long, texts: List<String>) {
        val (items, removed) = Checklist.merge(dao.forOwner(kind, ownerId), texts, kind, ownerId)
        if (removed.isNotEmpty()) dao.deleteIds(removed)
        items.forEach { dao.upsert(it) }
    }

    suspend fun delete(kind: String, ownerId: Long) = dao.deleteForOwner(kind, ownerId)

    /** Ticks or unticks one step, then completes (or un-does) its task or habit to match. */
    suspend fun toggle(itemId: Long) {
        val item = dao.get(itemId) ?: return
        val today = LocalDate.now().toString()
        when (item.ownerKind) {
            DaySkipEntity.KIND_HABIT -> {
                val habit = habitRepo.getHabitById(item.ownerId) ?: return
                if (Checklist.isChecked(item, repeats = true, today)) {
                    dao.setChecked(item.id, null)
                    if (habitRepo.isHabitCompletedToday(habit.id)) habitManager.uncompleteHabit(habit.id, keepChecklist = true)
                } else {
                    dao.setChecked(item.id, today)
                    val all = dao.forOwner(item.ownerKind, item.ownerId)
                    if (Checklist.progress(all, true, today) == all.size && !habitRepo.isHabitCompletedToday(habit.id)) {
                        habitManager.completeHabit(habit)
                    }
                }
            }
            DaySkipEntity.KIND_TASK -> {
                val task = taskRepo.getTaskById(item.ownerId) ?: return
                if (task.isCompleted) return   // done tasks keep their ticks
                val repeats = task.isRecurring
                dao.setChecked(item.id, if (Checklist.isChecked(item, repeats, today)) null else today)
                val all = dao.forOwner(item.ownerKind, item.ownerId)
                if (Checklist.progress(all, repeats, today) == all.size) taskCompletionManager.complete(task.id)
            }
        }
    }
}
