package com.habitgame.domain.engine

import com.habitgame.core.database.entity.ChecklistItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    private val today = "2026-10-31"
    private fun item(id: Long, text: String, checked: String? = null) =
        ChecklistItemEntity(id = id, ownerKind = "task", ownerId = 5, text = text, sortOrder = id.toInt(), checkedDate = checked)

    @Test
    fun habitsAndRepeatingTasksStartEachDayUnticked() {
        val yesterday = item(1, "Cleanse", "2026-10-30")
        assertFalse(Checklist.isChecked(yesterday, repeats = true, today))
        assertTrue(Checklist.isChecked(yesterday, repeats = false, today))   // a one-time task keeps it
        assertTrue(Checklist.isChecked(item(2, "Tone", today), repeats = true, today))
    }

    @Test
    fun editingKeepsTicksOnStepsThatDidNotChange() {
        val existing = listOf(item(1, "Buy eggs", today), item(2, "Buy milk"), item(3, "Buy bread", today))
        val (kept, removed) = Checklist.merge(existing, listOf("Buy bread", " ", "Buy rice", "Buy eggs"), "task", 5)
        assertEquals(listOf("Buy bread", "Buy rice", "Buy eggs"), kept.map { it.text })
        assertEquals(listOf(3L, 0L, 1L), kept.map { it.id })               // new step gets a new id
        assertEquals(listOf(today, null, today), kept.map { it.checkedDate })
        assertEquals(listOf(0, 1, 2), kept.map { it.sortOrder })
        assertEquals(listOf(2L), removed)
    }

    @Test
    fun progressCountsTodaysTicks() {
        val items = listOf(item(1, "a", today), item(2, "b"), item(3, "c", today))
        assertEquals(2, Checklist.progress(items, repeats = true, today))
    }
}
