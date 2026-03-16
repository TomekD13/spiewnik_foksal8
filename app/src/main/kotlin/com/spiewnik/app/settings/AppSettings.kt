package com.spiewnik.app.settings

import android.content.Context

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_FILE = "spiewnik_prefs"
        private const val KEY_LAST_SONG = "last_song_number"
        private const val KEY_LAST_PAGE_INDEX = "last_page_index"
        private const val KEY_NAV_MODE = "nav_mode"
    }

    var lastSongNumber: Int
        get() = prefs.getInt(KEY_LAST_SONG, 1)
        set(value) = prefs.edit().putInt(KEY_LAST_SONG, value).apply()

    var lastPageIndex: Int
        get() = prefs.getInt(KEY_LAST_PAGE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_PAGE_INDEX, value).apply()

    var navMode: String
        get() = prefs.getString(KEY_NAV_MODE, "SPREAD") ?: "SPREAD"
        set(value) = prefs.edit().putString(KEY_NAV_MODE, value).apply()

    fun resetPosition() {
        prefs.edit()
            .putInt(KEY_LAST_SONG, 1)
            .putInt(KEY_LAST_PAGE_INDEX, 0)
            .apply()
    }
}
