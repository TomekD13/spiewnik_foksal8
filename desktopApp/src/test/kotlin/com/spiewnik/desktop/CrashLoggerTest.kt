package com.spiewnik.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** End-to-end test of crash logging at the file level (read -> roll -> write). */
class CrashLoggerTest {

    @Test fun `appendCrash writes a formatted entry to a fresh file`() {
        val file = File.createTempFile("crash", ".txt").also { it.delete() }
        try {
            DesktopCrashLogger.appendCrash(file, IllegalStateException("boom"), "1.0.7", "dev", "2026-06-23 10:00:00")
            val text = file.readText()
            assertTrue(file.exists())
            assertTrue(text.contains("===== CRASH 2026-06-23 10:00:00"))
            assertTrue(text.contains("App: 1.0.7"))
            assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        } finally {
            file.delete()
        }
    }

    @Test fun `appendCrash rolls to the last 20 crashes`() {
        val file = File.createTempFile("crash", ".txt").also { it.delete() }
        try {
            for (i in 1..25) {
                DesktopCrashLogger.appendCrash(file, RuntimeException("e$i"), "1.0.$i", "dev", "t$i")
            }
            val text = file.readText()
            assertEquals(20, Regex("===== CRASH").findAll(text).count())
            assertTrue(text.contains("e25"))   // newest kept
            assertFalse(text.contains("e5"))    // dropped (only last 20: e6..e25)
        } finally {
            file.delete()
        }
    }
}
