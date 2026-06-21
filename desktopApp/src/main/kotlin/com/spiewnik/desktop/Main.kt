package com.spiewnik.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spiewnik.app.data.Song
import com.spiewnik.core.SongCatalog
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer

/**
 * Loaded songbook: parsed catalog + the PDF renderer, with a small page cache.
 * Pages are rendered on demand (CropBox is respected by PDFBox).
 */
class Songbook private constructor(val catalog: SongCatalog, doc: PDDocument) {
    private val renderer = PDFRenderer(doc)
    private val cache = HashMap<Int, ImageBitmap>()

    /** [pageIndex] is 0-based (jsonPage - 1). */
    fun renderPage(pageIndex: Int): ImageBitmap = cache.getOrPut(pageIndex) {
        renderer.renderImageWithDPI(pageIndex, RENDER_DPI).toComposeImageBitmap()
    }

    companion object {
        private const val RENDER_DPI = 150f

        fun load(): Songbook {
            val json = readResource("/piesni.json").decodeToString()
            val catalog = SongCatalog.fromJson(json)
            val doc = PDDocument.load(readResource("/Spiewnik.pdf"))
            return Songbook(catalog, doc)
        }

        private fun readResource(path: String): ByteArray =
            Songbook::class.java.getResourceAsStream(path)?.use { it.readBytes() }
                ?: error("Brak zasobu w paczce: $path")
    }
}

private val barColor = Color(0xFF252526)
private val bgColor = Color(0xFF1E1E1E)
private val accent = Color(0xFF007ACC)

@Composable
fun App() {
    val songbook = remember { Songbook.load() }
    var numberInput by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Song?>(songbook.catalog.findByNumber(1)) }

    fun open(number: Int) {
        songbook.catalog.findByNumber(number)?.let { current = it }
    }
    fun go() { numberInput.toIntOrNull()?.let { open(it) }; numberInput = "" }

    MaterialTheme(colors = darkColors(primary = accent, background = bgColor, surface = barColor)) {
        Column(Modifier.fillMaxSize().background(bgColor)) {
            // ── Top bar ──
            Row(
                Modifier.fillMaxWidth().background(barColor).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = numberInput,
                    onValueChange = { numberInput = it.filter(Char::isDigit).take(4) },
                    label = { Text("Nr pieśni") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { go() }),
                    modifier = Modifier.width(140.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { go() }) { Text("Idź") }
                Spacer(Modifier.width(16.dp))
                Text(
                    current?.let { "${it.number}. ${it.title}" } ?: "—",
                    color = Color(0xFFD4D4D4)
                )
            }

            // ── Spread ──
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                NavButton("◀") { current?.let { songbook.catalog.previousSong(it.number) }?.let { current = it } }
                Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp), contentAlignment = Alignment.Center) {
                    val song = current
                    if (song != null) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            song.pages.take(2).forEach { page ->
                                Image(
                                    bitmap = songbook.renderPage(page - 1),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
                NavButton("▶") { current?.let { songbook.catalog.nextSong(it.number) }?.let { current = it } }
            }
        }
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxHeight().width(64.dp)) {
        Text(label, color = Color.White)
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
        title = "Śpiewnik KADS Foksal 8"
    ) {
        App()
    }
}
