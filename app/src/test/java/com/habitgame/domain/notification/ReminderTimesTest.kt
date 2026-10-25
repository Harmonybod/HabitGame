package com.habitgame.domain.notification

import com.habitgame.core.database.entity.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimesTest {

    @Test
    fun parsesSortsAndDropsBadValues() {
        assertEquals(listOf(10, 5, 1), ReminderTimes.parse("5, 1,x,10,5,-3"))
        assertEquals(emptyList<Int>(), ReminderTimes.parse(""))
        assertEquals(6, ReminderTimes.parse("1,2,3,4,5,6,7,8").size)
    }

    @Test
    fun togglesOnAndOffUpToTheLimit() {
        assertEquals("10,5", ReminderTimes.toggle("5", 10))
        assertEquals("5", ReminderTimes.toggle("10,5", 10))
        assertEquals("", ReminderTimes.toggle("5", 5))
        assertEquals("6,5,4,3,2,1", ReminderTimes.toggle("1,2,3,4,5,6", 30))   // full: ignored
    }

    @Test
    fun labelsAndDescriptions() {
        assertEquals("At the time", ReminderTimes.label(0))
        assertEquals("5 min", ReminderTimes.label(5))
        assertEquals("1 h", ReminderTimes.label(60))
        assertEquals("1 h 30 min", ReminderTimes.label(90))
        assertEquals("1 day", ReminderTimes.label(1440))
        assertEquals("1 h before, 15 min before and 5 min before", ReminderTimes.describe(listOf(60, 15, 5)))
        assertEquals("at the time", ReminderTimes.describe(listOf(0)))
    }

    @Test
    fun defaultsMatchTheRequest() {
        assertEquals(listOf(60, 15, 5), UserEntity().deadlineReminderLeads)
        assertEquals(listOf(5), UserEntity().taskReminderLeads)
    }
}
