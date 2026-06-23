package com.spiewnik.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

    private fun entry(tag: String) =
        CrashReport.format(RuntimeException("boom-$tag"), "1.0.$tag", "TestDevice", "2026-01-0$tag 10:00:00")

    @Test fun `format contains header and stack trace`() {
        val s = CrashReport.format(IllegalStateException("oops"), "1.0.5", "TCL · Android 14", "2026-06-23 10:00:00")
        assertTrue(s.contains("===== CRASH 2026-06-23 10:00:00"))
        assertTrue(s.contains("App: 1.0.5"))
        assertTrue(s.contains("Device: TCL · Android 14"))
        assertTrue(s.contains("java.lang.IllegalStateException: oops"))
    }

    @Test fun `roll keeps only the last N entries`() {
        var log = ""
        for (i in 1..5) log = CrashReport.roll(log, entry(i.toString()), maxEntries = 3)
        val count = Regex("===== CRASH").findAll(log).count()
        assertEquals(3, count)
        assertTrue(log.contains("boom-5"))   // newest kept
        assertTrue(!log.contains("boom-1"))  // oldest dropped
    }

    @Test fun `roll on empty log yields a single entry`() {
        val log = CrashReport.roll("", entry("1"))
        assertEquals(1, Regex("===== CRASH").findAll(log).count())
    }
}
