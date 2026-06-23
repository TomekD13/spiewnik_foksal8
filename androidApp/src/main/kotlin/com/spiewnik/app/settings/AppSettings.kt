package com.spiewnik.app.settings

import android.content.Context

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_FILE = "spiewnik_prefs"
        private const val KEY_LAST_SONG = "last_song_number"
        private const val KEY_LAST_PAGE_INDEX = "last_page_index"
        private const val KEY_LAST_PDF_PAGE = "last_pdf_page"
        private const val KEY_NAV_MODE = "nav_mode"
        private const val KEY_HOLYRICS_IP = "holyrics_ip"
        private const val KEY_HOLYRICS_TOKEN = "holyrics_token"
        private const val KEY_HOLYRICS_AUTO_FOLLOW = "holyrics_auto_follow"
        private const val KEY_HOLYRICS_SEND = "holyrics_send"
        private const val KEY_CRASH_LOG = "crash_log_enabled"
    }

    var lastSongNumber: Int
        get() = prefs.getInt(KEY_LAST_SONG, 1)
        set(value) = prefs.edit().putInt(KEY_LAST_SONG, value).apply()

    var lastPageIndex: Int
        get() = prefs.getInt(KEY_LAST_PAGE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_PAGE_INDEX, value).apply()

    var lastPdfPage: Int
        get() = prefs.getInt(KEY_LAST_PDF_PAGE, 1)
        set(value) = prefs.edit().putInt(KEY_LAST_PDF_PAGE, value).apply()

    var navMode: String
        get() = prefs.getString(KEY_NAV_MODE, "SPREAD") ?: "SPREAD"
        set(value) = prefs.edit().putString(KEY_NAV_MODE, value).apply()

    var holyricsIp: String
        get() = prefs.getString(KEY_HOLYRICS_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOLYRICS_IP, value).apply()

    var holyricsToken: String
        get() = prefs.getString(KEY_HOLYRICS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOLYRICS_TOKEN, value).apply()

    var holyricsAutoFollow: Boolean
        get() = prefs.getBoolean(KEY_HOLYRICS_AUTO_FOLLOW, false)
        set(value) = prefs.edit().putBoolean(KEY_HOLYRICS_AUTO_FOLLOW, value).apply()

    var holyricsSend: Boolean
        get() = prefs.getBoolean(KEY_HOLYRICS_SEND, false)
        set(value) = prefs.edit().putBoolean(KEY_HOLYRICS_SEND, value).apply()

    var crashLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_CRASH_LOG, true)
        set(value) = prefs.edit().putBoolean(KEY_CRASH_LOG, value).apply()

    fun resetPosition() {
        prefs.edit()
            .putInt(KEY_LAST_SONG, 1)
            .putInt(KEY_LAST_PAGE_INDEX, 0)
            .apply()
    }
}
