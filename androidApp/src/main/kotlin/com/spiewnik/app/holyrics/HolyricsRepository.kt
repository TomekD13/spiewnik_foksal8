package com.spiewnik.app.holyrics

import android.util.Log
import org.json.JSONObject
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

            val numbers = parsePlaylist(body)
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

            val number = parseCurrentSong(body)
            Log.i(TAG, "Holyrics current song: $number")
            Result.success(number)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Holyrics current presentation", e)
            Result.failure(e)
        }
    }

    private fun parseCurrentSong(json: String): Int? {
        val root = JSONObject(json)
        if (root.optString("status") != "ok") return null
        val data = root.optJSONObject("data") ?: return null
        if (data.optString("type") != "song") return null
        return data.optString("name").trim().toIntOrNull()
    }

    private fun parsePlaylist(json: String): List<Int> {
        val root = JSONObject(json)
        if (root.optString("status") != "ok") return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        val numbers = mutableListOf<Int>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val number = item.optString("title").trim().toIntOrNull() ?: continue
            numbers.add(number)
        }
        return numbers
    }
}
