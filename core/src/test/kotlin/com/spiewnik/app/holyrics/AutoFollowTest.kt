package com.spiewnik.app.holyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFollowTest {

    @Test fun `opens a new reported number`() {
        assertTrue(AutoFollow.shouldOpen(reported = 264, lastSeen = 5, currentlyOpen = 5))
    }

    @Test fun `does not reopen the same reported number`() {
        assertFalse(AutoFollow.shouldOpen(reported = 264, lastSeen = 264, currentlyOpen = 1))
    }

    @Test fun `does not open when already showing it`() {
        assertFalse(AutoFollow.shouldOpen(reported = 264, lastSeen = 5, currentlyOpen = 264))
    }

    @Test fun `ignores null (nothing presented)`() {
        assertFalse(AutoFollow.shouldOpen(reported = null, lastSeen = 5, currentlyOpen = 5))
    }
}
