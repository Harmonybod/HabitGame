package com.habitgame.domain.habits

import com.habitgame.domain.tracker.TaskTiming
import com.habitgame.domain.tracker.TrackerPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTemplatesTest {
    @Test
    fun foldersAreUniqueAndNotEmpty() {
        val names = HabitTemplates.FOLDERS.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
        HabitTemplates.FOLDERS.forEach { folder ->
            assertTrue(folder.name, folder.templates.isNotEmpty())
            val habits = folder.templates.map { it.name.lowercase() }
            assertEquals(folder.name, habits.size, habits.toSet().size)
        }
    }

    @Test
    fun repeatDaysAreRealWeekdays() {
        HabitTemplates.FOLDERS.flatMap { it.templates }.forEach { t ->
            assertTrue(t.name, TaskTiming.WEEK.containsAll(t.days))
        }
    }

    @Test
    fun weightGainFolderMatchesTheExistingArea() {
        val folder = HabitTemplates.folder(TrackerPlan.WEIGHT_GAIN_GROUP)!!
        // The 100-day plan's weight gain habits are in it, so adding the folder doesn't duplicate them
        assertTrue(folder.templates.map { it.name }.containsAll(TrackerPlan.SEED_HABITS.map { it.name }))
        assertEquals("🍽️", HabitTemplates.emojiFor("weight gain"))
        assertEquals("#FF9800", HabitTemplates.colorHexFor(TrackerPlan.WEIGHT_GAIN_GROUP))
    }

    @Test
    fun bodyCareHasTheBasics() {
        val names = HabitTemplates.folder("Take care of body")!!.templates.map { it.name }
        assertTrue(names.containsAll(listOf("Brush teeth", "Take a shower")))
    }
}
