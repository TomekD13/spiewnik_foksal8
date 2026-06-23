package com.spiewnik.desktop

import com.spiewnik.app.CrashReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a single rolling log file
 * (~/.spiewnik/crash.txt, last [CrashReport.DEFAULT_MAX_ENTRIES] crashes).
 * Formatting/rolling lives in :core. Logging can be turned off in settings.
 */
object DesktopCrashLogger {

    private val defaultFile = File(System.getProperty("user.home"), ".spiewnik/crash.txt")

    fun install() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                if (DesktopSettings().crashLogEnabled) {
                    appendCrash(defaultFile, throwable, BuildInfo.VERSION, device(), now())
                }
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    /** Reads [file], appends a formatted+rolled crash entry, writes it back. Testable. */
    fun appendCrash(file: File, throwable: Throwable, version: String, device: String, time: String) {
        file.parentFile?.mkdirs()
        val entry = CrashReport.format(throwable, version, device, time)
        val existing = if (file.exists()) file.readText() else ""
        file.writeText(CrashReport.roll(existing, entry))
    }

    private fun device() = "Desktop · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}"
    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
