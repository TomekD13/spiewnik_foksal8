package com.spiewnik.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NavModeTest {

    @Test fun `landscape defaults to SONG`() {
        assertEquals(NavMode.SONG, NavMode.defaultFor(isLandscape = true))
    }

    @Test fun `portrait defaults to PAGE`() {
        assertEquals(NavMode.PAGE, NavMode.defaultFor(isLandscape = false))
    }
}
