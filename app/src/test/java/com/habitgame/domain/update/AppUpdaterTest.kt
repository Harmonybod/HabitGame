package com.habitgame.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun comparesVersionsByNumber() {
        assertTrue(AppUpdater.isNewer("2.4", "2.3"))
        assertTrue(AppUpdater.isNewer("2.10", "2.9"))
        assertTrue(AppUpdater.isNewer("3.0", "2.99"))
        assertTrue(AppUpdater.isNewer("2.4.1", "2.4"))
        assertFalse(AppUpdater.isNewer("2.4", "2.4"))
        assertFalse(AppUpdater.isNewer("2.3", "2.4"))
        assertFalse(AppUpdater.isNewer("2.4", "2.4.0"))
    }
}
