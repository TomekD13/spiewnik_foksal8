package com.spiewnik.app.holyrics

import com.google.gson.JsonParser

/** Parametry połączenia z Holyrics odczytane z kodu QR. */
data class HolyricsQrConfig(val ip: String, val token: String)

/**
 * Pure parsing of the Holyrics connection QR code — shared logic, no android.*.
 * QR content: {"enabled":true,"ips":["192.168.x.x"],"port":N,"token":"..."}.
 * We use only ips[0] (IP) and token; `port` and `enabled` are ignored.
 * Resilient: malformed JSON or missing IP/token yields null, never throws.
 */
object HolyricsQrParser {

    fun parse(qr: String): HolyricsQrConfig? = try {
        val root = JsonParser.parseString(qr).asJsonObject
        val ip = root.getAsJsonArray("ips")
            ?.takeIf { it.size() > 0 }
            ?.get(0)?.asString?.trim()
        val token = root.get("token")?.takeIf { !it.isJsonNull }?.asString?.trim()
        if (ip.isNullOrBlank() || token.isNullOrBlank()) null
        else HolyricsQrConfig(ip, token)
    } catch (e: Exception) {
        null
    }
}
