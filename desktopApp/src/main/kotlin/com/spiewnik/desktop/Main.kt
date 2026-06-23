package com.spiewnik.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.SwitchDefaults
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spiewnik.app.NavMode
import com.spiewnik.app.UiState
import com.spiewnik.app.holyrics.AutoFollow
import com.spiewnik.app.holyrics.HolyricsClient
import com.spiewnik.core.LruCache
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
    private val cache = LruCache<Int, ImageBitmap>(MAX_CACHED_PAGES)

    /** [pageIndex] is 0-based (jsonPage - 1). */
    fun renderPage(pageIndex: Int): ImageBitmap = cache.getOrPut(pageIndex) {
        renderer.renderImageWithDPI(pageIndex, RENDER_DPI).toComposeImageBitmap()
    }

    companion object {
        private const val RENDER_DPI = 150f
        private const val MAX_CACHED_PAGES = 16
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
    // Okno jest poziome — wspólna reguła daje tryb Pieśń (spójnie z androidem w landscape).
    var state by mutableStateOf(UiState(navMode = NavMode.defaultFor(isLandscape = true), totalPdfPages = totalPdfPages))
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
    val settings = remember { DesktopSettings() }
    val ctrl = remember { SongbookController(songbook.catalog, songbook.pageCount).also { it.openSong(settings.lastSong) } }
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
    var showHelp by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var holyricsSend by remember { mutableStateOf(settings.holyricsSend) }
    var crashLogEnabled by remember { mutableStateOf(settings.crashLogEnabled) }
    // Songs in the Holyrics playlist (number -> library id); drives the "Wyślij"/"Wyświetl" button.
    var playlistIds by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    fun go() {
        input.toIntOrNull()?.let { ctrl.openSong(it) }
        input = ""
    }

    // Background refresh of the playlist (number->id) for the send/show button — no status banner.
    fun refreshPlaylistSilent() {
        if (!holyricsSend || ip.isBlank() || token.isBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) { client.fetchPlaylistData(ip, token) }
                .onSuccess { pl -> playlistIds = pl.ids; if (pl.numbers.isNotEmpty()) playlist = pl.numbers }
        }
    }

    // "Wyślij do Holyrics": numer otwartej pieśni -> id w bibliotece -> dodanie do playlisty.
    fun sendToHolyrics() {
        val number = ctrl.state.song?.number ?: run { status = "Najpierw otwórz pieśń"; return }
        if (ip.isBlank() || token.isBlank()) { status = "Uzupełnij IP i token w ustawieniach"; return }
        scope.launch {
            val id = withContext(Dispatchers.IO) { client.findSongId(ip, token, number) }
                .getOrElse { status = "Holyrics niedostępny"; return@launch }
            if (id == null) { status = "Nie znaleziono pieśni $number w Holyrics"; return@launch }
            withContext(Dispatchers.IO) { client.addToPlaylist(ip, token, id) }
                .onSuccess {
                    playlistIds = playlistIds + (number to id) // optimistic -> button flips to "Wyświetl"
                    status = "Dodano pieśń $number do playlisty Holyrics"
                    refreshPlaylistSilent()
                }
                .onFailure { status = "Holyrics niedostępny" }
        }
    }

    // "Wyświetl": rzut otwartej pieśni na ekran Holyrics (id z playlisty lub przez SearchLyrics).
    fun showInHolyrics() {
        val number = ctrl.state.song?.number ?: return
        if (ip.isBlank() || token.isBlank()) return
        scope.launch {
            val id = playlistIds[number]
                ?: withContext(Dispatchers.IO) { client.findSongId(ip, token, number) }.getOrNull()
            if (id == null) { status = "Nie znaleziono pieśni $number w Holyrics"; return@launch }
            withContext(Dispatchers.IO) { client.showSong(ip, token, id) }
                .onSuccess { status = "Wyświetlono w Holyrics" }
                .onFailure { status = "Holyrics niedostępny" }
        }
    }

    fun openHolyrics() {
        showHolyrics = true
        if (ip.isBlank() || token.isBlank()) { status = "Uzupełnij IP i token w ustawieniach"; return }
        scope.launch {
            withContext(Dispatchers.IO) { client.fetchPlaylistData(ip, token) }
                .onSuccess { pl ->
                    playlistIds = pl.ids
                    if (pl.numbers.isEmpty()) status = "Playlista Holyrics jest pusta"
                    else { playlist = pl.numbers; status = null }
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
                if (AutoFollow.shouldOpen(number, lastSeen, ctrl.state.song?.number)) {
                    number?.let { ctrl.openSong(it) }
                }
                lastSeen = number
            }
            delay(2000)
        }
    }

    // Refresh the playlist (send/show button state) on start and when the option is enabled.
    LaunchedEffect(holyricsSend, ip, token) {
        if (holyricsSend && ip.isNotBlank() && token.isNotBlank()) refreshPlaylistSilent()
        else if (!holyricsSend) playlistIds = emptyMap()
    }

    // Remember the last opened song between runs.
    LaunchedEffect(ctrl.state.song?.number) {
        ctrl.state.song?.number?.let { settings.lastSong = it }
    }

    LaunchedEffect(status) { if (status != null) { delay(2500); status = null } }

    MaterialTheme(colors = darkColors(primary = accent, background = bgColor, surface = barColor)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(bgColor)) {
                if (barsVisible) TopBar(
                    ctrl,
                    holyricsSend = holyricsSend,
                    queuedNumbers = playlistIds.keys,
                    onSend = { sendToHolyrics() },
                    onShow = { showInHolyrics() },
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
                holyricsSend = holyricsSend,
                onIp = { ip = it; settings.holyricsIp = it },
                onToken = { token = it; settings.holyricsToken = it },
                onAutoFollow = { autoFollow = it; settings.autoFollow = it },
                onHolyricsSend = { holyricsSend = it; settings.holyricsSend = it },
                crashLogEnabled = crashLogEnabled,
                onCrashLog = { crashLogEnabled = it; settings.crashLogEnabled = it },
                onOpenHelp = { showHelp = true },
                onClose = { showSettings = false },
            )
            if (showHelp) HelpOverlay(onClose = { showHelp = false })
            status?.let { StatusBanner(it) }
        }
    }
}

@Composable
private fun TopBar(
    ctrl: SongbookController,
    holyricsSend: Boolean,
    queuedNumbers: Set<Int>,
    onSend: () -> Unit,
    onShow: () -> Unit,
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
        if (holyricsSend && song != null) {
            val showMode = song.number in queuedNumbers
            BarButton(
                label = if (showMode) "Wyświetl" else "Wyślij do Holyrics",
                color = if (showMode) Color(0xFFC2640A) else Color(0xFF1565C0),
                onClick = if (showMode) onShow else onSend,
            )
        }
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

/** Suwaki Holyrics: ON = pomarańczowy, OFF = niebieski. */
@Composable
private fun holyricsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color(0xFFC2640A),
    checkedTrackColor = Color(0xFFC2640A),
    uncheckedThumbColor = Color(0xFF0E639C),
    uncheckedTrackColor = Color(0xFF0E639C),
)

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
        var shift by remember { mutableStateOf(false) }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c ->
                        val shown = if (shift) c.uppercaseChar() else c
                        MiniKey(shown.toString()) { onKey(shown) }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { shift = !shift },
                    modifier = Modifier.size(width = 64.dp, height = 48.dp).focusProperties { canFocus = false },
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = if (shift) accent else Color(0xFF3A3D41))
                ) { Text("⇧ Shift", color = Color.White, fontSize = 13.sp) }
                Button(
                    onClick = onSpace,
                    modifier = Modifier.height(48.dp).width(160.dp).focusProperties { canFocus = false },
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3D41))
                ) { Text("spacja", color = Color.White) }
                MiniKey(".") { onKey('.') }
                // Drugi backspace — bliżej liter (pierwszy jest przy klawiaturze numerycznej)
                MiniKey("⌫") { onBackspace() }
            }
        }
    }
}

@Composable
private fun MiniKey(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        // Nie odbieraj fokusu polu edycyjnemu — dzięki temu klawiatura ekranowa nie znika
        // po naciśnięciu klawisza (pole pozostaje aktywne).
        modifier = Modifier.size(48.dp).focusProperties { canFocus = false },
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
    Box(Modifier.fillMaxWidth().background(barColor).padding(6.dp)) {
        // Number field + numpad — centered in the bar (zmniejszone ~20%, by mieściło się w oknie)
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier.size(width = 77.dp, height = 45.dp).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(if (input.isEmpty()) "—" else input, color = textColor, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            (1..9).forEach { d -> KeyButton(d.toString()) { onDigit(d.toString()) } }
            KeyButton("0") { onDigit("0") }
            KeyButton("⌫") { onBackspace() }
            Button(
                onClick = onGo,
                modifier = Modifier.height(45.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = accent)
            ) { Text("Idź", color = Color.White, fontSize = 15.sp) }
        }
        // Nav-mode stays pinned to the right edge
        Button(
            onClick = onNavMode,
            modifier = Modifier.align(Alignment.CenterEnd).height(45.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0E639C))
        ) { Text(navModeLabel, color = Color.White, fontSize = 13.sp) }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 45.dp, height = 45.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3D41))
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
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
    holyricsSend: Boolean,
    crashLogEnabled: Boolean,
    onIp: (String) -> Unit,
    onToken: (String) -> Unit,
    onAutoFollow: (Boolean) -> Unit,
    onHolyricsSend: (Boolean) -> Unit,
    onCrashLog: (Boolean) -> Unit,
    onOpenHelp: () -> Unit,
    onClose: () -> Unit,
) {
    val tfColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = Color.White, cursorColor = accent,
        focusedBorderColor = accent, unfocusedBorderColor = Color(0xFF555555),
    )
    var active by remember { mutableStateOf(0) } // 0 = IP, 1 = token (target of the on-screen keyboard)
    // Klawiatura ekranowa pojawia się dopiero po kliknięciu w pole IP lub Token.
    var ipFocused by remember { mutableStateOf(false) }
    var tokenFocused by remember { mutableStateOf(false) }
    val showKeyboard = ipFocused || tokenFocused
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(0.7f).background(barColor).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ustawienia Holyrics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = ip, onValueChange = onIp, singleLine = true,
                label = { Text("Adres IP komputera z Holyrics", color = textColor) },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    ipFocused = it.isFocused
                    if (it.isFocused) active = 0
                }
            )
            OutlinedTextField(
                value = token, onValueChange = onToken, singleLine = true,
                label = { Text("Token API", color = textColor) },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    tokenFocused = it.isFocused
                    if (it.isFocused) active = 1
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Odbieraj i zmieniaj pieśni razem z Holyrics", color = textColor, modifier = Modifier.weight(1f))
                Switch(checked = autoFollow, onCheckedChange = onAutoFollow, colors = holyricsSwitchColors())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wysyłaj pieśni do Holyrics (przycisk w pasku)", color = textColor, modifier = Modifier.weight(1f))
                Switch(checked = holyricsSend, onCheckedChange = onHolyricsSend, colors = holyricsSwitchColors())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zapisuj log błędów (~/.spiewnik/crash.txt)", color = textColor, modifier = Modifier.weight(1f))
                Switch(checked = crashLogEnabled, onCheckedChange = onCrashLog, colors = holyricsSwitchColors())
            }
            if (showKeyboard) {
                Text(
                    "Klawiatura wpisuje do zaznaczonego pola (IP lub Token). W Holyrics włącz API: " +
                        "GetLyricsPlaylist + GetCurrentPresentation (Narzędzia → API). Ta sama sieć WiFi.",
                    color = Color(0xFF9CDCFE), fontSize = 13.sp
                )
                MiniKeyboard(
                    onKey = { c -> if (active == 0) onIp(ip + c) else onToken(token + c) },
                    onBackspace = { if (active == 0) onIp(ip.dropLast(1)) else onToken(token.dropLast(1)) },
                    onSpace = { if (active == 0) onIp("$ip ") else onToken("$token ") },
                    onClear = { if (active == 0) onIp("") else onToken("") },
                )
            } else {
                Text(
                    "Kliknij w pole IP lub Token, aby wpisać dane — pojawi się klawiatura ekranowa. " +
                        "W Holyrics włącz API: GetLyricsPlaylist + GetCurrentPresentation (Narzędzia → API). Ta sama sieć WiFi.",
                    color = Color(0xFF9CDCFE), fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenHelp,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF0E639C)),
                    modifier = Modifier.weight(1f)
                ) { Text("Instrukcja obsługi", color = Color.White) }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                    modifier = Modifier.weight(1f)
                ) { Text("Zamknij", color = Color.White) }
            }
            Text(
                "Wersja ${BuildInfo.VERSION}",
                color = Color(0xFF808080), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun HelpOverlay(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize(0.8f).background(barColor).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Instrukcja obsługi", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(backgroundColor = accent)) {
                    Text("Zamknij", color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                HELP_TEXT,
                color = textColor, fontSize = 15.sp,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            )
        }
    }
}

private const val HELP_TEXT = """NAWIGACJA
• Wpisz numer pieśni na klawiaturze numerycznej u dołu i naciśnij „Idź".
• „Spis treści" otwiera pełną listę pieśni — przewijasz i klikasz, albo filtrujesz
  wpisując numer lub tytuł (ekranowa klawiatura jest pod listą).
• Strzałki ◀ ▶ przewijają wg trybu nawigacji; na granicach są wyszarzone.
• Kliknięcie w obszar nut chowa/pokazuje paski (pełny ekran nut).

TRYBY NAWIGACJI (przycisk po prawej w dolnym pasku)
• Rozkładówka — strzałki o 1 stronę (pokazane 2 strony obok siebie).
• Strona — pojedyncza strona.
• Pieśń — strzałki skaczą do poprzedniej/następnej pieśni.

HOLYRICS
• Ten sam WiFi co komputer z Holyrics; numery pieśni muszą być zgodne.
• ⚙ → wpisz adres IP komputera z Holyrics i token API (Holyrics: Narzędzia → API).
• W uprawnieniach API Holyrics włącz: GetLyricsPlaylist oraz GetCurrentPresentation;
  do wysyłania także SearchLyrics, AddLyricsToPlaylist, ShowLyrics.
• Przycisk „Holyrics" pokazuje playlistę; aktualnie wyświetlana pieśń jest oznaczona ▶.
• „Odbieraj i zmieniaj pieśni razem z Holyrics" — apka sama otwiera pieśń z rzutnika.
• „Wysyłaj pieśni do Holyrics" — w górnym pasku pojawia się przycisk: „Wyślij do Holyrics"
  dodaje otwartą pieśń do playlisty, potem zmienia się w „Wyświetl" (rzuca na ekran). Gdy
  pieśń jest już w playliście, od razu widać „Wyświetl".

LOG BŁĘDÓW
• Gdy aplikacja się zawiesi, błąd zapisuje się do pliku: ~/.spiewnik/crash.txt
  (ostatnie 20 crashy). Można wyłączyć w ustawieniach („Zapisuj log błędów")."""

@Composable
private fun StatusBanner(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            message, color = Color.White, fontSize = 16.sp,
            modifier = Modifier.padding(24.dp).background(Color(0xEE333333)).padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

fun main() {
    DesktopCrashLogger.install()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
            title = "Śpiewnik KADS Foksal 8",
            icon = painterResource("logo.png")
        ) {
            App()
        }
    }
}
