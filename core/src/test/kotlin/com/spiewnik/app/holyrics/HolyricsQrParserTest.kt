package com.spiewnik.app.holyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HolyricsQrParserTest {

    @Test fun `parses ip and token, ignores port and enabled`() {
        val qr = """{"enabled":true,"ips":["192.168.1.50"],"port":8091,"token":"abc123"}"""
        val config = HolyricsQrParser.parse(qr)
        assertEquals(HolyricsQrConfig("192.168.1.50", "abc123"), config)
    }

    @Test fun `takes the first ip when several are present`() {
        val qr = """{"ips":["10.0.0.2","192.168.1.50"],"token":"t"}"""
        assertEquals("10.0.0.2", HolyricsQrParser.parse(qr)?.ip)
    }

    @Test fun `trims whitespace around ip and token`() {
        val qr = """{"ips":[" 192.168.1.50 "],"token":" abc "}"""
        assertEquals(HolyricsQrConfig("192.168.1.50", "abc"), HolyricsQrParser.parse(qr))
    }

    @Test fun `null when token missing`() {
        assertNull(HolyricsQrParser.parse("""{"ips":["192.168.1.50"]}"""))
    }

    @Test fun `null when ips missing or empty`() {
        assertNull(HolyricsQrParser.parse("""{"token":"abc"}"""))
        assertNull(HolyricsQrParser.parse("""{"ips":[],"token":"abc"}"""))
    }

    @Test fun `null when ip or token is blank`() {
        assertNull(HolyricsQrParser.parse("""{"ips":[""],"token":"abc"}"""))
        assertNull(HolyricsQrParser.parse("""{"ips":["192.168.1.50"],"token":""}"""))
    }

    @Test fun `null on malformed or empty json`() {
        assertNull(HolyricsQrParser.parse("not a qr at all"))
        assertNull(HolyricsQrParser.parse(""))
    }
}
