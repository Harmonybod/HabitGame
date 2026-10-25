package com.habitgame.domain.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ShoppingTextTest {

    @Test
    fun priceRanges() {
        assertNull(ShoppingText.priceRange(0.0, 0.0, "$"))
        assertEquals("$20–40", ShoppingText.priceRange(20.0, 40.0, "$"))
        assertEquals("$25", ShoppingText.priceRange(25.0, 0.0, "$"))
        assertEquals("$25", ShoppingText.priceRange(25.0, 25.0, "$"))
        assertEquals("up to $40", ShoppingText.priceRange(0.0, 40.0, "$"))
        assertEquals("$20–40", ShoppingText.priceRange(40.0, 20.0, "$"))   // typed the wrong way round
        assertEquals("19.99–24.50", ShoppingText.priceRange(19.99, 24.5, ""))
    }

    @Test
    fun deadlines() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals("by 6:00 PM today", ShoppingText.deadline(LocalDateTime.of(2026, 10, 25, 18, 0), today).replace(' ', ' '))
        assertEquals("by 9:30 AM tomorrow", ShoppingText.deadline(LocalDateTime.of(2026, 10, 26, 9, 30), today).replace(' ', ' '))
    }

    @Test
    fun amounts() {
        assertEquals(12.5, ShoppingText.parseAmount("12,5"), 0.0)
        assertEquals(0.0, ShoppingText.parseAmount(""), 0.0)
    }
}
