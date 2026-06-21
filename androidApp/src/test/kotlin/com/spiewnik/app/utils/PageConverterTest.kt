package com.spiewnik.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PageConverterTest {

    @Test
    fun `jsonPage 1 maps to pdfIndex 0`() {
        assertEquals(0, PageConverter.jsonPageToPdfIndex(1))
    }

    @Test
    fun `jsonPage 143 maps to pdfIndex 142`() {
        assertEquals(142, PageConverter.jsonPageToPdfIndex(143))
    }

    @Test
    fun `jsonPage 700 maps to pdfIndex 699`() {
        assertEquals(699, PageConverter.jsonPageToPdfIndex(700))
    }

    @Test
    fun `pdfIndex 0 maps to jsonPage 1`() {
        assertEquals(1, PageConverter.pdfIndexToJsonPage(0))
    }

    @Test
    fun `pdfIndex 699 maps to jsonPage 700`() {
        assertEquals(700, PageConverter.pdfIndexToJsonPage(699))
    }

    @Test
    fun `round trip jsonPage to pdfIndex and back`() {
        for (page in 1..700) {
            assertEquals(page, PageConverter.pdfIndexToJsonPage(PageConverter.jsonPageToPdfIndex(page)))
        }
    }
}
