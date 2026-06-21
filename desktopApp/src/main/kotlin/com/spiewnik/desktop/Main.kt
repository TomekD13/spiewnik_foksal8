package com.spiewnik.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.spiewnik.app.holyrics.HolyricsClient
import com.spiewnik.core.SongCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val settings = remember { DesktopSettings() }
    val client = remember { HolyricsClient(HttpUrlTransport()) }
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var showToc by remember { mutableStateOf(false) }
    var tocQuery by remember { mutableStateOf("") }
    var barsVisible by remember { mutableStateOf(true) }

    var ip by remember { mutableStateOf(settings.holyricsIp) }
    var token by remember { mutableStateOf(settings.holyricsToken) }
    var autoFollow by remember { mutableStateOf(settings.autoFollow) }
    var showHolyrics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun go() {
        input.toIntOrNull()?.let { ctrl.openSong(it) }
        input = ""
    }

    fun openHolyrics() {
        showHolyrics = true
        if (ip.isBlank() || token.isBlank()) { status = "Uzupełnij IP i token w ustawieniach"; return }
        scope.launch {
            withContext(Dispatchers.IO) { client.fetchPlaylist(ip, token) }
                .onSuccess { nums ->
                    if (nums.isEmpty()) status = "Playlista Holyrics jest pusta"
                    else { playlist = nums; status = null }
                }
                .onFailure { status = "Holyrics niedostępny" }
            withContext(Dispatchers.IO) { client.fetchCurrentSong(ip, token) }.onSuccess { currentSong = it }
        }
    }

    // Auto-follow: poll the current presentation; Holyrics always wins (act once per change).
    LaunchedEffect(autoFollow, ip, token) {
        if (!autoFollow || ip.isBlank() || token.isBlank()) return@LaunchedEffect
        var lastSeen: Int? = null
        while (isActive) {
            withContext(Dispatchers.IO) { client.fetchCurrentSong(ip, token) }.onSuccess { number ->
                currentSong = number
                if (number != null && number != lastSeen && number != ctrl.state.song?.number) ctrl.openSong(number)
                lastSeen = number
            }
            delay(2000)
        }
    }

    LaunchedEffect(status) { if (status != null) { delay(2500); status = null } }

    MaterialTheme(colors = darkColors(primary = accent, background = bgColor, surface = barColor)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(bgColor)) {
                if (barsVisible) TopBar(
                    ctrl,
                    onOpenToc = { tocQuery = ""; showToc = true },
                    onOpenHolyrics = { openHolyrics() },
                    onOpenSettings = { showSettings = true },
                )
                SpreadArea(ctrl, songbook, Modifier.weight(1f), onToggleBars = { barsVisible = !barsVisible })
                if (barsVisible) NumpadBar(
                    input = input,
                    onDigit = { d -> if (input.length < 4) input += d },
                    onBackspace = { input = input.dropLast(1) },
                    onGo = { go() },
                    onNavMode = { ctrl.cycleNavMode() },
                    navModeLabel = ctrl.state.navMode.label(),
                )
            }
            if (showToc) TocOverlay(
                catalog = songbook.catalog,
                query = tocQuery,
                onQuery = { tocQuery = it },
                onPick = { number -> ctrl.openSong(number); showToc = false },
                onClose = { showToc = false },
            )
            if (showHolyrics) HolyricsOverlay(
                playlist = playlist,
                current = currentSong,
                catalog = songbook.catalog,
                onPick = { number -> ctrl.openSong(number); showHolyrics = false },
                onClose = { showHolyrics = false },
            )
            if (showSettings) SettingsOverlay(
                ip = ip,
                token = token,
                autoFollow = autoFollow,
                onIp = { ip = it; settings.holyricsIp = it },
                onToken = { token = it; settings.holyricsToken = it },
                onAutoFollow = { autoFollow = it; settings.autoFollow = it },
                onClose = { showSettings = false },
            )
            status?.let { StatusBanner(it) }
        }
    }
}

@Composable
private fun TopBar(
    ctrl: SongbookController,
    onOpenToc: () -> Unit,
    onOpenHolyrics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val s = ctrl.state
    val song = s.song
    Row(
        Modifier.fillMaxWidth().background(barColor).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (song != null) "${song.number}. ${song.title}" else "Śpiewnik",
            color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(s.displayPages, color = textColor, fontSize = 18.sp)
        BarButton("Spis treści", Color(0xFF0E639C), onOpenToc)
        BarButton("Holyrics", Color(0xFF6A0DAD), onOpenHolyrics)
        BarButton("⚙", Color(0xFF3A3D41), onOpenSettings)
    }
}

@Composable
private fun BarButton(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = color)) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun TocOverlay(
    catalog: SongCatalog,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val filtered = remember(query) {
        val q = query.foldPl()
        if (q.isBlank()) catalog.songs
        else catalog.songs.filter {
            it.number.toString().contains(q) || it.title.foldPl().contains(q)
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize(0.9f).background(barColor).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    label = { Text("Szukaj numeru lub tytułu", color = textColor) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        cursorColor = accent,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0xFF555555),
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = accent)
                ) { Text("Zamknij", color = Color.White) }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(filtered) { song ->
                    Text(
                        "${song.number}. ${song.title}",
                        color = textColor, fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(song.number) }.padding(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // On-screen keyboard for touch use (Compose Desktop doesn't pop the OS keyboard)
            MiniKeyboard(
                onKey = { c -> onQuery(query + c) },
                onBackspace = { onQuery(query.dropLast(1)) },
                onSpace = { onQuery("$query ") },
                onClear = { onQuery("") },
            )
        }
    }
}

@Composable
private fun MiniKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── Digit pad (left) ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c -> MiniKey(c.toString()) { onKey(c) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniKey("⌫") { onBackspace() }
                MiniKey("0") { onKey('0') }
                MiniKey("C") { onClear() }
            }
        }

        // ── Letters (right, fills the rest of the bar) ──
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c -> MiniKey(c.toString()) { onKey(c) } }
                }
            }
            Button(
                onClick = onSpace,
                modifier = Modifier.height(48.dp).fillMaxWidth(0.6f),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3D41))
            ) { Text("spacja", color = Color.White) }
        }
    }
}

@Composable
private fun MiniKey(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3D41))
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

/** Lowercase + strip Polish diacritics so "blogoslaw" matches "Błogosław". */
private fun String.foldPl(): String {
    val stripped = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
    return stripped.replace('ł', 'l').replace('Ł', 'L').lowercase()
}

@Composable
private fun SpreadArea(ctrl: SongbookController, songbook: Songbook, modifier: Modifier, onToggleBars: () -> Unit) {
    val s = ctrl.state
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArrowButton("◀", enabled = s.canGoLeft) { ctrl.navigateLeft() }
        // Tapping the page area toggles the bars (fullscreen notes), like on Android.
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(8.dp).clickable { onToggleBars() },
            contentAlignment = Alignment.Center
        ) {
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

@Composable
private fun HolyricsOverlay(
    playlist: List<Int>,
    current: Int?,
    catalog: SongCatalog,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize(0.6f).background(barColor).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Playlista Holyrics", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(backgroundColor = accent)) {
                    Text("Zamknij", color = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (playlist.isEmpty()) {
                Text("Brak playlisty — sprawdź ustawienia Holyrics.", color = textColor, modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(playlist) { number ->
                        val title = catalog.findByNumber(number)?.title
                        val isCurrent = number == current
                        Text(
                            text = (if (isCurrent) "▶ " else "") + (if (title != null) "$number. $title" else "$number"),
                            color = if (isCurrent) Color.White else textColor,
                            fontSize = 18.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (isCurrent) accent else Color.Transparent)
                                .clickable { onPick(number) }
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOverlay(
    ip: String,
    token: String,
    autoFollow: Boolean,
    onIp: (String) -> Unit,
    onToken: (String) -> Unit,
    onAutoFollow: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val tfColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = Color.White, cursorColor = accent,
        focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF555555),
    )
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.6f).background(barColor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ustawienia Holyrics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = ip, onValueChange = onIp, singleLine = true,
                label = { Text("Adres IP komputera z Holyrics", color = textColor) },
                colors = tfColors, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token, onValueChange = onToken, singleLine = true,
                label = { Text("Token API", color = textColor) },
                colors = tfColors, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Automatycznie zmieniaj pieśń razem z Holyrics", color = textColor, modifier = Modifier.weight(1f))
                Switch(checked = autoFollow, onCheckedChange = onAutoFollow)
            }
            Text(
                "W Holyrics włącz API: GetLyricsPlaylist + GetCurrentPresentation (Narzędzia → API). Ta sama sieć WiFi.",
                color = Color(0xFF9CDCFE), fontSize = 13.sp
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Zamknij", color = Color.White) }
        }
    }
}

@Composable
private fun StatusBanner(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            message, color = Color.White, fontSize = 16.sp,
            modifier = Modifier.padding(24.dp).background(Color(0xEE333333)).padding(horizontal = 20.dp, vertical = 12.dp)
        )
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
