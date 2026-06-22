package com.spiewnik.app.holyrics

import com.google.gson.JsonParser

/**
 * Pure parsing of Holyrics API responses — shared by Android and Windows.
 * Network/transport stays platform-specific; this only turns JSON into numbers.
 * All methods are resilient: malformed JSON yields empty/null, never throws.
 */
object HolyricsParser {

    /**
     * GetLyricsPlaylist parsed into both the ordered list of song numbers (from each item's
     * `title`) and a `number -> library id` map (from `id`). The id lets us project an
     * already-queued song directly (ShowLyrics) without an extra SearchLyrics lookup.
     */
    data class Playlist(val numbers: List<Int>, val ids: Map<Int, String>)

    fun parsePlaylistData(json: String): Playlist = try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "ok") {
            Playlist(emptyList(), emptyMap())
        } else {
            val data = root.get("data")
            if (data == null || !data.isJsonArray) {
                Playlist(emptyList(), emptyMap())
            } else {
                val numbers = mutableListOf<Int>()
                val ids = linkedMapOf<Int, String>()
                data.asJsonArray.forEach { el ->
                    runCatching {
                        val o = el.asJsonObject
                        val number = o.get("title")?.asString?.trim()?.toIntOrNull()
                        if (number != null) {
                            numbers.add(number)
                            val id = o.get("id")?.takeIf { !it.isJsonNull }?.asString
                            if (!id.isNullOrBlank()) ids.putIfAbsent(number, id)
                        }
                    }
                }
                Playlist(numbers, ids)
            }
        }
    } catch (e: Exception) {
        Playlist(emptyList(), emptyMap())
    }

    /** GetLyricsPlaylist: song numbers live in each item's `title` field. */
    fun parsePlaylist(json: String): List<Int> = parsePlaylistData(json).numbers

    /**
     * GetCurrentPresentation: the current song number lives in `data.name` when
     * `data.type == "song"`. Returns null when nothing (or a non-song) is presented.
     */
    /**
     * SearchLyrics: returns the `id` of the song whose `title` exactly equals [number].
     * Exact match avoids picking "10"/"100" when searching for "1". Null when none matches.
     */
    fun parseSongId(json: String, number: Int): String? = try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asString != "ok") {
            null
        } else {
            val data = root.get("data")
            if (data == null || !data.isJsonArray) {
                null
            } else {
                val target = number.toString()
                data.asJsonArray.asSequence().mapNotNull { el ->
                    runCatching {
                        val o = el.asJsonObject
                        if (o.get("title")?.asString?.trim() == target) o.get("id")?.asString else null
                    }.getOrNull()
                }.firstOrNull()
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * True when a control method (AddLyricsToPlaylist/ShowLyrics) succeeded.
     * Those methods may return an empty body or `{"status":"ok"}`; only an explicit
     * `"status":"error"` (or unparseable non-empty body) counts as failure.
     */
    fun isOk(json: String): Boolean {
        if (json.isBlank()) return true
        return runCatching {
            val status = JsonParser.parseString(json).asJsonObject.get("status")?.asString
            status == null || status == "ok"
        }.getOrDefault(false)
    }

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
