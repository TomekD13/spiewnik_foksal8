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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spiewnik.app.NavMode
import com.spiewnik.app.UiState
import com.spiewnik.core.SongCatalog
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer

// ── Data + rendering ──────────────────────────────────────────────────────────

/** Loaded songbook: catalog + PDF renderer with a small page cache. */
class Songbook private constructor(val catalog: SongCatalog, doc: PDDocument) {
    val pageCount: Int = doc.numberOfPages
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
            val doc = PDDocument.load(readResource("/Spiewnik.pdf"))
            return Songbook(SongCatalog.fromJson(json), doc)
        }
        private fun readResource(path: String): ByteArray =
            Songbook::class.java.getResourceAsStream(path)?.use { it.readBytes() }
                ?: error("Brak zasobu w paczce: $path")
    }
}

// ── Navigation controller (mirrors SongViewModel orchestration, on shared :core) ──

class SongbookController(
    private val catalog: SongCatalog,
    private val totalPdfPages: Int,
) {
    var state by mutableStateOf(UiState(totalPdfPages = totalPdfPages))
        private set

    /** Returns false when the song doesn't exist or has no valid pages. */
    fun openSong(number: Int, pageIndex: Int = 0): Boolean {
        val song = catalog.findByNumber(number) ?: return false
        val validPages = song.pages.filter { it in 1..totalPdfPages }
        if (validPages.isEmpty()) return false
        val idx = pageIndex.coerceIn(0, validPages.size - 1)
        state = UiState(
            song = song,
            navMode = state.navMode,
            hasPrevSong = catalog.previousSong(number) != null,
            hasNextSong = catalog.nextSong(number) != null,
            songPages = validPages,
            songPageIndex = idx,
            currentPdfPage = validPages[idx],
            totalPdfPages = totalPdfPages,
        )
        return true
    }

    fun navigateLeft() {
        val s = state
        when (s.navMode) {
            NavMode.SPREAD, NavMode.PAGE -> {
                val p = (s.currentPdfPage - 1).coerceAtLeast(1)
                if (p != s.currentPdfPage) state = s.copy(currentPdfPage = p)
            }
            NavMode.SONG -> s.song?.let { catalog.previousSong(it.number) }?.let { openSong(it.number) }
        }
    }

    fun navigateRight() {
        val s = state
        when (s.navMode) {
            NavMode.SPREAD, NavMode.PAGE -> {
                val p = (s.currentPdfPage + 1).coerceAtMost(s.totalPdfPages)
                if (p != s.currentPdfPage) state = s.copy(currentPdfPage = p)
            }
            NavMode.SONG -> s.song?.let { catalog.nextSong(it.number) }?.let { openSong(it.number) }
        }
    }

    fun cycleNavMode() {
        state = state.copy(navMode = state.navMode.next())
    }
}

// ── UI ────────────────────────────────────────────────────────────────────────

private val barColor = Color(0xFF252526)
private val bgColor = Color(0xFF1E1E1E)
private val accent = Color(0xFF007ACC)
private val textColor = Color(0xFFD4D4D4)

@Composable
fun App() {
    val songbook = remember { Songbook.load() }
    val ctrl = remember { SongbookController(songbook.catalog, songbook.pageCount).also { it.openSong(1) } }
    var input by remember { mutableStateOf("") }

    fun go() {
        input.toIntOrNull()?.let { ctrl.openSong(it) }
        input = ""
    }

    MaterialTheme(colors = darkColors(primary = accent, background = bgColor, surface = barColor)) {
        Column(Modifier.fillMaxSize().background(bgColor)) {
            TopBar(ctrl)
            SpreadArea(ctrl, songbook, Modifier.weight(1f))
            NumpadBar(
                input = input,
                onDigit = { d -> if (input.length < 4) input += d },
                onBackspace = { input = input.dropLast(1) },
                onGo = { go() },
                onNavMode = { ctrl.cycleNavMode() },
                navModeLabel = ctrl.state.navMode.label(),
            )
        }
    }
}

@Composable
private fun TopBar(ctrl: SongbookController) {
    val s = ctrl.state
    val song = s.song
    Row(
        Modifier.fillMaxWidth().background(barColor).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (song != null) "${song.number}. ${song.title}" else "Śpiewnik",
            color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(s.displayPages, color = textColor, fontSize = 18.sp)
    }
}

@Composable
private fun SpreadArea(ctrl: SongbookController, songbook: Songbook, modifier: Modifier) {
    val s = ctrl.state
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton("◀", enabled = s.canGoLeft) { ctrl.navigateLeft() }
        Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.leftPageNumber?.let { page ->
                    Image(
                        bitmap = songbook.renderPage(page - 1),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                s.rightPageNumber?.let { page ->
                    Image(
                        bitmap = songbook.renderPage(page - 1),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
        ArrowButton("▶", enabled = s.canGoRight) { ctrl.navigateRight() }
    }
}

@Composable
private fun ArrowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxHeight().width(72.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = barColor, disabledBackgroundColor = bgColor)
    ) {
        Text(label, color = if (enabled) Color.White else Color(0xFF555555), fontSize = 26.sp)
    }
}

@Composable
private fun NumpadBar(
    input: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onGo: () -> Unit,
    onNavMode: () -> Unit,
    navModeLabel: String,
) {
    Row(
        Modifier.fillMaxWidth().background(barColor).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Typed-number display
        Box(
            Modifier.width(96.dp).size(width = 96.dp, height = 56.dp).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(if (input.isEmpty()) "—" else input, color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        (1..9).forEach { d -> KeyButton(d.toString()) { onDigit(d.toString()) } }
        KeyButton("0") { onDigit("0") }
        KeyButton("⌫") { onBackspace() }
        Button(
            onClick = onGo,
            modifier = Modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = accent)
        ) { Text("Idź", color = Color.White, fontSize = 18.sp) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNavMode,
            modifier = Modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0E639C))
        ) { Text(navModeLabel, color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 56.dp, height = 56.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3D41))
    ) {
        Text(label, color = Color.White, fontSize = 20.sp)
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
