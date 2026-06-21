package com.spiewnik.app.holyrics

import com.google.gson.JsonParser

/**
 * Pure parsing of Holyrics API responses — shared by Android and Windows.
 * Network/transport stays platform-specific; this only turns JSON into numbers.
 * All methods are resilient: malformed JSON yields empty/null, never throws.
 */
object HolyricsParser {

    /** GetLyricsPlaylist: song numbers live in each item's `title` field. */
    fun parsePlaylist(json: String): List<Int> = try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "ok") {
            emptyList()
        } else {
            val data = root.get("data")
            if (data == null || !data.isJsonArray) {
                emptyList()
            } else {
                data.asJsonArray.mapNotNull { el ->
                    runCatching { el.asJsonObject.get("title")?.asString?.trim()?.toIntOrNull() }.getOrNull()
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * GetCurrentPresentation: the current song number lives in `data.name` when
     * `data.type == "song"`. Returns null when nothing (or a non-song) is presented.
     */
    fun parseCurrentSong(json: String): Int? = try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "ok") {
            null
        } else {
            val data = root.get("data")
            if (data == null || data.isJsonNull || !data.isJsonObject) {
                null
            } else {
                val obj = data.asJsonObject
                if (obj.get("type")?.asString != "song") null
                else obj.get("name")?.asString?.trim()?.toIntOrNull()
            }
        }
    } catch (e: Exception) {
        null
    }
}
