package com.spiewnik.app

/**
 * Pure crash-log formatting and rolling — shared by Android and desktop.
 * A single rolling log keeps the last [DEFAULT_MAX_ENTRIES] crashes; the oldest
 * drop off. No IO here — the platform reads/writes the file.
 */
object CrashReport {

    const val DEFAULT_MAX_ENTRIES = 20
    private const val MARKER = "===== CRASH"

    /** One log entry: header (time, app version, device) + full stack trace. */
    fun format(throwable: Throwable, appVersion: String, device: String, time: String): String =
        buildString {
            appendLine("$MARKER $time =====")
            appendLine("App: $appVersion")
            appendLine("Device: $device")
            appendLine(throwable.stackTraceToString().trimEnd())
        }

    /** Appends [newEntry] to [existingLog], keeping only the last [maxEntries] crashes. */
    fun roll(existingLog: String, newEntry: String, maxEntries: Int = DEFAULT_MAX_ENTRIES): String {
        val entries = splitEntries(existingLog) + newEntry.trim()
        return entries.takeLast(maxEntries.coerceAtLeast(1)).joinToString("\n\n") + "\n"
    }

    private fun splitEntries(log: String): List<String> =
        if (log.isBlank()) emptyList()
        else log.split(Regex("(?=^$MARKER )", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
