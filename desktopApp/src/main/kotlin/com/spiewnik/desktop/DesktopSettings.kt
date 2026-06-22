package com.spiewnik.desktop

import com.google.gson.Gson
import java.io.File

/**
 * Persisted desktop settings: a small JSON file in the user's home directory
 * (~/.spiewnik/settings.json). The desktop counterpart of Android's AppSettings.
 */
class DesktopSettings {

    private data class Data(
        var holyricsIp: String = "",
        var holyricsToken: String = "",
        var autoFollow: Boolean = false,
        var holyricsSend: Boolean = false,
        var lastSong: Int = 1,
    )

    private val file = File(System.getProperty("user.home"), ".spiewnik/settings.json")
    private val data: Data = runCatching {
        if (file.exists()) Gson().fromJson(file.readText(), Data::class.java) else Data()
    }.getOrDefault(Data())

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(Gson().toJson(data))
        }
    }

    var holyricsIp: String
        get() = data.holyricsIp
        set(value) { data.holyricsIp = value; save() }

    var holyricsToken: String
        get() = data.holyricsToken
        set(value) { data.holyricsToken = value; save() }

    var autoFollow: Boolean
        get() = data.autoFollow
        set(value) { data.autoFollow = value; save() }

    var holyricsSend: Boolean
        get() = data.holyricsSend
        set(value) { data.holyricsSend = value; save() }

    var lastSong: Int
        get() = data.lastSong
        set(value) { data.lastSong = value; save() }
}
