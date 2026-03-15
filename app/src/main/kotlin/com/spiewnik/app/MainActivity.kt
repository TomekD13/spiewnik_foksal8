package com.spiewnik.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spiewnik.app.databinding.ActivityMainBinding
import com.spiewnik.app.pdf.PdfPageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SongViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
        setupNavigation()
        observeState()
    }

    // ── Input & search ────────────────────────────────────────────────────────

    private fun setupInput() {
        binding.btnGo.setOnClickListener { openSongFromInput() }

        binding.etNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                openSongFromInput(); true
            } else false
        }

        // Title autocomplete – adapter populated when songs list arrives
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

    // ── State observer ────────────────────────────────────────────────────────

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            updateTopBar(state)
            updateNavMode(state)
            updateError(state)
            renderPages(state)
        }
    }

    private fun updateTopBar(state: UiState) {
        if (state.song != null) {
            binding.tvSongInfo.text = "#${state.song.number}  ${state.song.title}"
            binding.tvPageInfo.text = state.displayPages
        } else {
            binding.tvSongInfo.text = getString(R.string.app_name)
            binding.tvPageInfo.text = ""
        }
    }

    private fun updateNavMode(state: UiState) {
        binding.btnNavMode.text = state.navMode.label()
    }

    private fun updateError(state: UiState) {
        val msg = state.error
        if (msg != null) {
            binding.tvError.text = msg
            binding.tvError.visibility = View.VISIBLE
        } else {
            binding.tvError.visibility = View.GONE
        }
    }

    private fun renderPages(state: UiState) {
        val leftIdx = state.leftPdfIndex
        if (leftIdx == null) {
            binding.ivLeft.setImageBitmap(null)
            binding.ivRight.setImageBitmap(null)
            binding.ivRight.visibility = View.INVISIBLE
            return
        }

        // If views haven't been laid out yet, wait for layout pass
        if (binding.ivLeft.width == 0) {
            binding.ivLeft.post { renderPages(viewModel.state.value ?: return@post) }
            return
        }

        val rightIdx = state.rightPdfIndex
        val leftW = binding.ivLeft.width
        val leftH = binding.ivLeft.height
        val rightW = binding.ivRight.width
        val rightH = binding.ivRight.height

        lifecycleScope.launch {
            val leftBmp = withContext(Dispatchers.IO) {
                viewModel.pdfCache.renderPage(leftIdx, leftW, leftH)
            }
            binding.ivLeft.setImageBitmap(leftBmp)

            if (rightIdx != null) {
                binding.ivRight.visibility = View.VISIBLE
                val rightBmp = withContext(Dispatchers.IO) {
                    viewModel.pdfCache.renderPage(rightIdx, rightW, rightH)
                }
                binding.ivRight.setImageBitmap(rightBmp)
            } else {
                binding.ivRight.setImageBitmap(null)
                binding.ivRight.visibility = View.INVISIBLE
            }

            // Prefetch neighbours in the background
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
