package com.spiewnik.desktop

import com.spiewnik.app.CrashReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a single rolling log file
 * (~/.spiewnik/crash.txt, last [CrashReport.DEFAULT_MAX_ENTRIES] crashes).
 * Formatting/rolling lives in :core.
 */
object DesktopCrashLogger {

    private val file = File(System.getProperty("user.home"), ".spiewnik/crash.txt")

    fun install() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(throwable) }
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun write(throwable: Throwable) {
        file.parentFile?.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val device = "Desktop · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}"
        val entry = CrashReport.format(throwable, BuildInfo.VERSION, device, time)
        val existing = if (file.exists()) file.readText() else ""
        file.writeText(CrashReport.roll(existing, entry))
    }
}
