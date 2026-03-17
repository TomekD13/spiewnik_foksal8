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
