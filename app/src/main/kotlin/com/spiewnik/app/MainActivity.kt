package com.spiewnik.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.GestureDetector
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spiewnik.app.databinding.ActivityMainBinding
import com.spiewnik.app.ui.HolyricsBottomSheet
import com.spiewnik.app.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SongViewModel by viewModels()

    // ── Zoom / pan state ──────────────────────────────────────────────────────
    private var zoomScale = 1f
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    // Track song changes to reset pan (not zoom) when song switches
    private var lastSongNumber: Int? = null

    // Active render job — canceled before starting a new one to prevent stale bitmap flicker
    private var renderJob: kotlinx.coroutines.Job? = null

    private var inputRowHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep screen on + fullscreen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInput()
        setupInputRowToggle()
        setupNavigation()
        setupZoom()
        observeState()
        observeLoadState()
        observeToasts()
    }

    // ── Input & search ────────────────────────────────────────────────────────

    private fun setupInput() {
        binding.btnGo.setOnClickListener { openSongFromInput() }

        binding.etNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                openSongFromInput(); true
            } else false
        }

        viewModel.allSongs.observe(this) { songs ->
            val items = songs.map { "${it.number}. ${it.title}" }
            binding.actvTitle.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
            )
        }

        binding.actvTitle.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as? String ?: return@setOnItemClickListener
            val number = selected.substringBefore(".").trim().toIntOrNull() ?: return@setOnItemClickListener
            binding.etNumber.setText(number.toString())
            viewModel.openSong(number)
            binding.actvTitle.text.clear()
        }

        binding.btnNavMode.setOnClickListener { viewModel.cycleNavMode() }

        binding.btnSettings.setOnClickListener {
            SettingsFragment.show(supportFragmentManager)
        }

        binding.btnHolyrics.setOnClickListener {
            HolyricsBottomSheet.show(supportFragmentManager)
            viewModel.fetchHolyricsPlaylist()
        }
    }

    private fun setupInputRowToggle() {
        binding.inputRow.post {
            inputRowHeight = binding.inputRow.measuredHeight
        }
        binding.btnSelect.setOnClickListener {
            if (binding.inputRow.visibility == View.VISIBLE) hideInputRow()
            else showInputRow()
        }
        // btnHolyrics: action not wired yet
    }

    private fun showInputRow() {
        binding.inputRow.visibility = View.VISIBLE
        binding.inputRow.layoutParams.height = 0
        binding.inputRow.requestLayout()
        binding.btnSelect.text = getString(R.string.btn_hide_bar)
        ValueAnimator.ofInt(0, inputRowHeight).apply {
            duration = 250
            addUpdateListener {
                binding.inputRow.layoutParams.height = it.animatedValue as Int
                binding.inputRow.requestLayout()
            }
            start()
        }
    }

    private fun hideInputRow() {
        ValueAnimator.ofInt(inputRowHeight, 0).apply {
            duration = 250
            addUpdateListener {
                binding.inputRow.layoutParams.height = it.animatedValue as Int
                binding.inputRow.requestLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.inputRow.visibility = View.GONE
                    binding.btnSelect.text = getString(R.string.btn_select)
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
            renderPages(state)
        }
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

    // ── UI update helpers ─────────────────────────────────────────────────────

    private fun updateTopBar(state: UiState) {
        if (state.song != null) {
            binding.tvSongInfo.text = getString(R.string.song_info_format, state.song.number, state.song.title)
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
        val pages = state.allPageNumbers
        val w = binding.ivLeft.width.takeIf { it > 0 } ?: return
        val h = binding.ivLeft.height.takeIf { it > 0 } ?: return
        val neighbours = listOf(state.spreadStart - 1, state.spreadStart + 2)
        for (idx in neighbours) {
            val pageNum = pages.getOrNull(idx) ?: continue
            withContext(Dispatchers.IO) {
                viewModel.pdfCache.renderPage(pageNum - 1, w, h)
            }
        }
    }
}
