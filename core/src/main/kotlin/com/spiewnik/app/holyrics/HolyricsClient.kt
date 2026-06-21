package com.spiewnik.app.holyrics

/** Sends a POST to the Holyrics API and returns the response body. Throws on error. */
interface HolyricsTransport {
    fun post(url: String): String
}

/**
 * Platform-neutral Holyrics client: builds the API URLs, calls the injected
 * [transport], and parses the result with [HolyricsParser]. Network failures
 * (timeout, no host, bad HTTP) surface as [Result.failure] — never throws.
 *
 * Shared logic; the transport (HTTP) is provided per platform.
 */
class HolyricsClient(private val transport: HolyricsTransport) {

    fun fetchPlaylist(ip: String, token: String): Result<List<Int>> = runCatching {
        val body = transport.post("http://$ip:$PORT/api/GetLyricsPlaylist?token=$token")
        HolyricsParser.parsePlaylist(body)
    }

    fun fetchCurrentSong(ip: String, token: String): Result<Int?> = runCatching {
        val body = transport.post("http://$ip:$PORT/api/GetCurrentPresentation?token=$token")
        HolyricsParser.parseCurrentSong(body)
    }

    companion object {
        const val PORT = 8091
    }
}
