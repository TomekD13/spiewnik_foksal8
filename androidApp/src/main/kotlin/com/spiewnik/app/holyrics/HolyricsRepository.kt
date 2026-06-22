package com.spiewnik.app.holyrics

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

class HolyricsRepository {

    companion object {
        private const val TAG = "HolyricsRepository"
        private const val PORT = 8091
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 3000
    }

    /**
     * Fetches the current Lyrics playlist from Holyrics.
     * Returns a list of song numbers (Int) on success.
     * Items whose name is not a valid integer are silently skipped.
     */
    fun fetchPlaylist(ip: String, token: String): Result<List<Int>> {
        return try {
            val url = URL("http://$ip:$PORT/api/GetLyricsPlaylist?token=$token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.write("{}".toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Holyrics returned HTTP $responseCode")
                return Result.failure(Exception("HTTP $responseCode"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val numbers = HolyricsParser.parsePlaylist(body)
            Log.i(TAG, "Holyrics playlist: $numbers")
            Result.success(numbers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Holyrics playlist", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches the song currently shown on screen in Holyrics (GetCurrentPresentation).
     * Returns the song number (Int) on success, or null when nothing — or a non-song —
     * is being presented. The number lives in data.name (e.g. {"data":{"type":"song","name":"1"}}).
     */
    fun fetchCurrentSong(ip: String, token: String): Result<Int?> {
        return try {
            val url = URL("http://$ip:$PORT/api/GetCurrentPresentation?token=$token")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.write("{}".toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Holyrics GetCurrentPresentation returned HTTP $responseCode")
                return Result.failure(Exception("HTTP $responseCode"))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val number = HolyricsParser.parseCurrentSong(body)
            Log.i(TAG, "Holyrics current song: $number")
            Result.success(number)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Holyrics current presentation", e)
            Result.failure(e)
        }
    }

    /** GetLyricsPlaylist with both the ordered numbers and the number→id map. */
    fun fetchPlaylistData(ip: String, token: String): Result<HolyricsParser.Playlist> = try {
        Result.success(HolyricsParser.parsePlaylistData(postApi(ip, token, "GetLyricsPlaylist", "{}")))
    } catch (e: Exception) {
        Log.e(TAG, "Holyrics GetLyricsPlaylist (data) failed", e)
        Result.failure(e)
    }

    // ── Sending a song to Holyrics ────────────────────────────────────────────────

    /** Finds the Holyrics library id of the song whose title exactly equals [number]. */
    fun findSongId(ip: String, token: String, number: Int): Result<String?> = try {
        val body = postApi(ip, token, "SearchLyrics",
            """{"text":"$number","title":true,"artist":false,"note":false}""")
        Result.success(HolyricsParser.parseSongId(body, number))
    } catch (e: Exception) {
        Log.e(TAG, "Holyrics SearchLyrics failed", e)
        Result.failure(e)
    }

    /** Adds the song with the given library [id] to the current Holyrics lyrics playlist. */
    fun addToPlaylist(ip: String, token: String, id: String): Result<Unit> = try {
        val body = postApi(ip, token, "AddLyricsToPlaylist", """{"id":"$id"}""")
        if (HolyricsParser.isOk(body)) Result.success(Unit)
        else Result.failure(Exception("Holyrics: $body"))
    } catch (e: Exception) {
        Log.e(TAG, "Holyrics AddLyricsToPlaylist failed", e)
        Result.failure(e)
    }

    /** Projects the song with the given library [id] on the Holyrics output. */
    fun showSong(ip: String, token: String, id: String): Result<Unit> = try {
        val body = postApi(ip, token, "ShowLyrics", """{"id":"$id"}""")
        if (HolyricsParser.isOk(body)) Result.success(Unit)
        else Result.failure(Exception("Holyrics: $body"))
    } catch (e: Exception) {
        Log.e(TAG, "Holyrics ShowLyrics failed", e)
        Result.failure(e)
    }

    /** POSTs [body] (JSON) to the given Holyrics API [method]; returns the response, throws on non-200. */
    private fun postApi(ip: String, token: String, method: String, body: String): String {
        val url = URL("http://$ip:$PORT/api/$method?token=$token")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.write(body.toByteArray())
        }
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw Exception("HTTP $responseCode")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }
}
