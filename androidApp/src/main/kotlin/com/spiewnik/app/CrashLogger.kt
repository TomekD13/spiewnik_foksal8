package com.spiewnik.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.spiewnik.app.settings.AppSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a single rolling log file (last
 * [CrashReport.DEFAULT_MAX_ENTRIES] crashes) in app-specific external storage,
 * so it can be shared from the settings dialog. Formatting/rolling lives in :core.
 */
object CrashLogger {

    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "crash.txt"

    /** Installs a global handler that logs the crash, then delegates to the default one. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, throwable) }
            default?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, throwable: Throwable) {
        if (!AppSettings(context).crashLogEnabled) return
        val file = logFile(context)
        file.parentFile?.mkdirs()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val entry = CrashReport.format(throwable, BuildConfig.VERSION_NAME, device, time)
        val existing = if (file.exists()) file.readText() else ""
        file.writeText(CrashReport.roll(existing, entry))
    }

    fun logFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "$LOG_DIR/$LOG_FILE")

    /** Share intent (ACTION_SEND with the log attached), or null when no log exists yet. */
    fun shareIntent(context: Context): Intent? {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return null
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Log błędów Śpiewnik ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
