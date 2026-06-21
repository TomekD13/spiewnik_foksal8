package com.spiewnik.desktop

import com.spiewnik.app.holyrics.HolyricsTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Desktop HTTP transport for Holyrics (HttpURLConnection, POST {} with timeouts). */
class HttpUrlTransport(private val timeoutMs: Int = 3000) : HolyricsTransport {
    override fun post(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.write("{}".toByteArray())
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }
}
