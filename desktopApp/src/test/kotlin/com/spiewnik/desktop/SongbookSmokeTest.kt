package com.spiewnik.desktop

import com.spiewnik.core.SongCatalog
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless smoke test: the bundled resources load and PDFBox renders a page.
 * No Compose / no GUI / no jpackage needed — runs in plain JUnit.
 */
class SongbookSmokeTest {

    private fun resource(path: String): ByteArray =
        javaClass.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Brak zasobu w paczce: $path")

    @Test
    fun `piesni json loads and contains song 1`() {
        val catalog = SongCatalog.fromJson(resource("/piesni.json").decodeToString())
        assertTrue("katalog powinien mieć pieśni", catalog.songs.isNotEmpty())
        assertNotNull("pieśń nr 1 powinna istnieć", catalog.findByNumber(1))
    }

    @Test
    fun `pdf renders a page to a non-empty image`() {
        PDDocument.load(resource("/Spiewnik.pdf")).use { doc ->
            assertTrue(doc.numberOfPages > 600)
            val image = PDFRenderer(doc).renderImageWithDPI(10, 72f) // page 11 (0-based)
            assertTrue("render powinien mieć szerokość", image.width > 0)
            assertTrue("render powinien mieć wysokość", image.height > 0)
        }
    }
}
