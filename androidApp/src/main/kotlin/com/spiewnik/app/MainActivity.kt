package com.spiewnik.app

import android.animation.ValueAnimator
import android.content.Context
import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.GestureDetector
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.spiewnik.app.databinding.ActivityMainBinding
import com.spiewnik.app.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SongViewModel by viewModels()

    private var holyricsPopup: PopupWindow? = null
    private var holyricsPopupButtons: LinearLayout? = null

    // ── Zoom / pan state ──────────────────────────────────────────────────────
    private var zoomScale = 1f
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    // Track song changes to reset pan (not zoom) when song switches
    private var lastSongNumber: Int? = null

    // Active render job — canceled before starting a new one to prevent stale bitmap flicker
    private var renderJob: kotlinx.coroutines.Job? = null

    private var inputRowHeight = 0

    // ── Bars (top bar + input row) visibility, toggled by tapping the page ──────
    private var topBarHeight = 0
    private var barsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep screen on + fullscreen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInput()
        measureBarHeights()
        setupNavigation()
        setupZoom()
        observeState()
        observeLoadState()
        observeToasts()
        observeHolyricsPlaylist()
        observeHolyricsCurrentSong()
        observeHolyricsSend()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Obrót ekranu przywraca domyślny tryb dla nowej orientacji
        // (poziom → Pieśń, pion → Strona). Re-render po przeliczeniu layoutu.
        val isLandscape =
            newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        viewModel.applyOrientationDefault(isLandscape)
        binding.pdfContainer.post { viewModel.state.value?.let { renderPages(it) } }
    }

    override fun onResume() {
        super.onResume()
        // Poll Holyrics only while in the foreground (no-op if the setting is off)
        viewModel.startHolyricsAutoFollow()
        // Refresh the send/show button state from the current Holyrics playlist (no-op if off)
        viewModel.refreshHolyricsPlaylistSilent()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopHolyricsAutoFollow()
    }

    // ── Input & search ────────────────────────────────────────────────────────

    private fun setupInput() {
        binding.btnGo.setOnClickListener {
            openSongFromInput()
            hideKeyboard(binding.etNumber)
        }

        binding.etNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                openSongFromInput()
                hideKeyboard(binding.etNumber)
                true
            } else false
        }

        // Title field acts as a button that opens the searchable table of contents
        binding.actvTitle.setOnClickListener { showSongListDialog() }

        setupKeyboardFocus()

        binding.btnNavMode.setOnClickListener { viewModel.cycleNavMode() }

        binding.btnSettings.setOnClickListener {
            SettingsFragment.show(supportFragmentManager)
        }

        binding.btnHolyrics.setOnClickListener {
            if (holyricsPopup?.isShowing == true) {
                holyricsPopup?.dismiss()
            } else {
                showHolyricsPopup()
                viewModel.fetchHolyricsPlaylist()
                viewModel.fetchHolyricsCurrentSong()
            }
        }

        binding.btnHolyricsSend.setOnClickListener {
            val songNumber = viewModel.state.value?.song?.number
            val inPlaylist = songNumber != null &&
                viewModel.holyricsPlaylistIds.value?.containsKey(songNumber) == true
            if (inPlaylist) viewModel.showCurrentSongInHolyrics()
            else viewModel.sendCurrentSongToHolyrics()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardFocus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        listOf(binding.etNumber).forEach { field ->
            field.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    exitImmersiveMode()
                    window.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    )
                    v.requestFocus()
                    v.postDelayed({ imm.showSoftInput(v, InputMethodManager.SHOW_FORCED) }, 200)
                }
                false
            }
            field.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    v.postDelayed({ enterImmersiveMode() }, 500)
                }
            }
        }
    }

    /**
     * Searchable table of contents: full scrollable list of songs ("nr. tytuł"),
     * filtered live as you type. Tapping a row opens the song.
     */
    private fun showSongListDialog() {
        val songs = viewModel.allSongs.value ?: return
        val labels = songs.map { "${it.number}. ${it.title}" }
        val view = layoutInflater.inflate(R.layout.dialog_song_list, null)
        val search = view.findViewById<EditText>(R.id.etSongSearch)
        val list = view.findViewById<ListView>(R.id.lvSongs)
        val adapter = ArrayAdapter(this, R.layout.item_song, ArrayList(labels))
        list.adapter = adapter

        val dialog = AlertDialog.Builder(this).setView(view).create()
        search.doAfterTextChanged { adapter.filter.filter(it?.toString() ?: "") }
        list.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position) ?: return@setOnItemClickListener
            selected.substringBefore(".").trim().toIntOrNull()?.let { number ->
                binding.etNumber.setText(number.toString())
                viewModel.openSong(number)
            }
            dialog.dismiss()
        }
        dialog.setOnDismissListener { enterImmersiveMode() }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.7f).toInt(),
            (resources.displayMetrics.heightPixels * 0.9f).toInt()
        )
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    @Suppress("DEPRECATION")
    private fun exitImmersiveMode() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    /** Measures the bar heights so [toggleBars] can animate them back to full size. */
    private fun measureBarHeights() {
        binding.inputRow.post {
            inputRowHeight = binding.inputRow.measuredHeight
        }
        binding.topBar.post {
            topBarHeight = binding.topBar.measuredHeight
        }
    }

    /**
     * Toggles both the top info bar and the input row so notes can use the full
     * screen. Triggered by a single tap on the page (the side arrows are separate
     * views, so tapping them navigates instead of toggling).
     */
    private fun toggleBars() {
        if (topBarHeight == 0) topBarHeight = binding.topBar.measuredHeight
        if (barsVisible) {
            animateBarHeight(binding.topBar, binding.topBar.height, 0, endVisible = false)
            if (binding.inputRow.visibility == View.VISIBLE) {
                animateBarHeight(binding.inputRow, binding.inputRow.height, 0, endVisible = false)
            }
            barsVisible = false
        } else {
            animateBarHeight(binding.topBar, 0, topBarHeight, endVisible = true)
            animateBarHeight(binding.inputRow, 0, inputRowHeight, endVisible = true)
            barsVisible = true
        }
    }

    private fun animateBarHeight(view: View, from: Int, to: Int, endVisible: Boolean) {
        if (to > 0) view.visibility = View.VISIBLE
        ValueAnimator.ofInt(from, to).apply {
            duration = 250
            addUpdateListener {
                view.layoutParams.height = it.animatedValue as Int
                view.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (endVisible) {
                        view.layoutParams.height = to
                        view.requestLayout()
                    } else {
                        view.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    private fun openSongFromInput() {
        val number = binding.etNumber.text?.toString()?.trim()?.toIntOrNull()
        if (number != null && number > 0) viewModel.openSong(number)
    }

    // ── Navigation buttons ────────────────────────────────────────────────────

    private fun setupNavigation() {
        binding.btnPrev.setOnClickListener { viewModel.navigateLeft() }
        binding.btnNext.setOnClickListener { viewModel.navigateRight() }
    }

    // ── Zoom / pan ────────────────────────────────────────────────────────────

    // LinearLayout cannot override performClick() — suppress is the only option here
    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoom() {
        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 5f)
                    applyZoom()
                    return true
                }
            }
        )

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(
                    e1: MotionEvent?, e2: MotionEvent,
                    distanceX: Float, distanceY: Float
                ): Boolean {
                    if (zoomScale <= 1f) return false
                    binding.pdfContainer.translationX -= distanceX
                    binding.pdfContainer.translationY -= distanceY
                    clampTranslation()
                    return true
                }

                // Tap on the page (not the side arrows) toggles both bars for fullscreen notes
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleBars()
                    return true
                }
            }
        )

        binding.pdfContainer.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
    }

    private fun applyZoom() {
        binding.pdfContainer.scaleX = zoomScale
        binding.pdfContainer.scaleY = zoomScale
        clampTranslation()
    }

    private fun clampTranslation() {
        val c = binding.pdfContainer
        val maxTx = c.width * (zoomScale - 1f) / 2f
        val maxTy = c.height * (zoomScale - 1f) / 2f
        c.translationX = c.translationX.coerceIn(-maxTx, maxTx)
        c.translationY = c.translationY.coerceIn(-maxTy, maxTy)
    }

    private fun resetPan() {
        binding.pdfContainer.translationX = 0f
        binding.pdfContainer.translationY = 0f
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            // Reset pan when song changes (zoom persists)
            val currentNumber = state.song?.number
            if (currentNumber != lastSongNumber) {
                lastSongNumber = currentNumber
                resetPan()
            }

            updateTopBar(state)
            updateNavMode(state)
            updateNavButtons(state)
            updateHolyricsSendButton()
            renderPages(state)
        }
    }

    private fun observeHolyricsSend() {
        viewModel.holyricsSendEnabled.observe(this) { updateHolyricsSendButton() }
        viewModel.holyricsPlaylistIds.observe(this) { updateHolyricsSendButton() }
    }

    /** Shows the top-bar Holyrics button only when enabled and a song is open;
     *  morphs label/colour between "Wyślij do Holyrics" (blue) and "Wyświetl" (orange)
     *  depending on whether the open song is already in the Holyrics playlist. */
    private fun updateHolyricsSendButton() {
        val enabled = viewModel.holyricsSendEnabled.value ?: false
        val songNumber = viewModel.state.value?.song?.number
        val btn = binding.btnHolyricsSend
        if (!enabled || songNumber == null) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        val showMode = viewModel.holyricsPlaylistIds.value?.containsKey(songNumber) == true
        btn.text = getString(if (showMode) R.string.holyrics_show_button else R.string.holyrics_send_button)
        val colorRes = if (showMode) R.color.holyrics_show else R.color.holyrics_send
        btn.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun observeLoadState() {
        viewModel.loadState.observe(this) { loadState ->
            when (loadState) {
                is LoadState.Error -> {
                    binding.tvError.text = loadState.message
                    binding.tvError.visibility = View.VISIBLE
                }
                LoadState.Ready -> binding.tvError.visibility = View.GONE
                LoadState.Loading -> binding.tvError.visibility = View.GONE
            }
        }
    }

    private fun observeToasts() {
        viewModel.toastEvent.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }
    }

    // ── Holyrics popup ────────────────────────────────────────────────────────

    private fun showHolyricsPopup() {
        val popView = layoutInflater.inflate(R.layout.popup_holyrics, null)
        holyricsPopupButtons = popView.findViewById(R.id.llSongButtons)
        popView.findViewById<Button>(R.id.btnHolyricsClose).setOnClickListener {
            holyricsPopup?.dismiss()
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val popupWidth = screenWidth / 3
        val maxHeight = (resources.displayMetrics.heightPixels * 0.65f).toInt()

        holyricsPopup = PopupWindow(popView, popupWidth, maxHeight, true).apply {
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this@MainActivity, R.color.bar_background)))
            elevation = 12f
            isOutsideTouchable = true
        }

        // Populate immediately with cached data if available
        viewModel.holyricsPlaylist.value
            ?.takeIf { it.isNotEmpty() }
            ?.let { populateHolyricsButtons(it) }

        // Right-align popup with the button
        val xOffset = binding.btnHolyrics.width - popupWidth
        holyricsPopup?.showAsDropDown(binding.btnHolyrics, xOffset, 0)
    }

    private fun observeHolyricsPlaylist() {
        viewModel.holyricsPlaylist.observe(this) { numbers ->
            if (numbers.isNotEmpty() && holyricsPopup?.isShowing == true) {
                populateHolyricsButtons(numbers)
            }
        }
    }

    private fun observeHolyricsCurrentSong() {
        viewModel.currentHolyricsSong.observe(this) {
            if (holyricsPopup?.isShowing == true) {
                viewModel.holyricsPlaylist.value
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { numbers -> populateHolyricsButtons(numbers) }
            }
        }
    }

    private fun populateHolyricsButtons(numbers: List<Int>) {
        val container = holyricsPopupButtons ?: return
        container.removeAllViews()
        val allSongs = viewModel.allSongs.value ?: emptyList()
        val current = viewModel.currentHolyricsSong.value
        var highlighted: View? = null
        numbers.forEach { number ->
            val song = allSongs.find { it.number == number }
            val label = if (song != null) "$number. ${song.title}" else "$number"
            val isCurrent = number == current
            val btn = Button(this).apply {
                text = if (isCurrent) "▶ $label" else label
                textSize = 14f
                if (isCurrent) {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.accent)
                    )
                }
                setOnClickListener {
                    viewModel.openSong(number)
                    holyricsPopup?.dismiss()
                }
            }
            container.addView(
                btn,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            if (isCurrent) highlighted = btn
        }
        // Auto-scroll so the currently-played song is visible
        highlighted?.let { view ->
            (container.parent as? ScrollView)?.post {
                (container.parent as? ScrollView)?.smoothScrollTo(0, view.top)
            }
        }
    }

    // ── UI update helpers ─────────────────────────────────────────────────────

    private fun updateTopBar(state: UiState) {
        val song = state.song
        if (song != null) {
            binding.tvSongInfo.text = getString(R.string.song_info_format, song.number, song.title)
            binding.tvPageInfo.text = state.displayPages
        } else {
            binding.tvSongInfo.text = getString(R.string.app_name)
            binding.tvPageInfo.text = ""
        }
    }

    private fun updateNavMode(state: UiState) {
        binding.btnNavMode.text = state.navMode.label()
    }

    private fun updateNavButtons(state: UiState) {
        binding.btnPrev.isEnabled = state.canGoLeft
        binding.btnNext.isEnabled = state.canGoRight
    }

    // ── PDF rendering ─────────────────────────────────────────────────────────

    private fun renderPages(state: UiState) {
        val leftIdx = state.leftPdfIndex
        if (leftIdx == null) {
            binding.ivLeft.setImageBitmap(null)
            binding.ivRight.setImageBitmap(null)
            binding.ivRight.visibility = View.GONE
            return
        }

        if (binding.ivLeft.width == 0) {
            binding.ivLeft.post { renderPages(viewModel.state.value ?: return@post) }
            return
        }

        val rightIdx = state.rightPdfIndex

        // If right page will be shown but ivRight has no size yet (was GONE), trigger re-render after layout
        if (rightIdx != null && binding.ivRight.width == 0) {
            binding.ivRight.visibility = View.VISIBLE
            binding.ivRight.post { renderPages(viewModel.state.value ?: return@post) }
            return
        }

        val leftW = binding.ivLeft.width
        val leftH = binding.ivLeft.height
        val rightW = binding.ivRight.width
        val rightH = binding.ivRight.height

        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val leftBmp = withContext(Dispatchers.IO) {
                viewModel.pdfCache.renderPage(leftIdx, leftW, leftH)
            }
            if (!isActive) return@launch
            binding.ivLeft.setImageBitmap(leftBmp)

            if (rightIdx != null) {
                binding.ivRight.visibility = View.VISIBLE
                val rightBmp = withContext(Dispatchers.IO) {
                    viewModel.pdfCache.renderPage(rightIdx, rightW, rightH)
                }
                if (!isActive) return@launch
                binding.ivRight.setImageBitmap(rightBmp)
            } else {
                binding.ivRight.setImageBitmap(null)
                binding.ivRight.visibility = View.GONE
            }

            prefetchNeighbours(state)
        }
    }

    private suspend fun prefetchNeighbours(state: UiState) {
        val w = binding.ivLeft.width.takeIf { it > 0 } ?: return
        val h = binding.ivLeft.height.takeIf { it > 0 } ?: return
        val pagesToPrefetch: List<Int> = when (state.navMode) {
            NavMode.SONG -> listOfNotNull(
                state.songPages.getOrNull(state.songPageIndex - 1),
                state.songPages.getOrNull(state.songPageIndex + 2)
            )
            else -> {
                val p = state.currentPdfPage
                listOf(p - 1, p + 2).filter { it in 1..state.totalPdfPages }
            }
        }
        for (pageNum in pagesToPrefetch) {
            withContext(Dispatchers.IO) {
                viewModel.pdfCache.renderPage(pageNum - 1, w, h)
            }
        }
    }
}
