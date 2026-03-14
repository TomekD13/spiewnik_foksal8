package com.spiewnik.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var repository: SongRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = SongRepository(this)

        val etSongNumber = findViewById<TextInputEditText>(R.id.etSongNumber)
        val btnOpen = findViewById<Button>(R.id.btnOpen)
        val tvError = findViewById<TextView>(R.id.tvError)

        fun openSong() {
            val input = etSongNumber.text?.toString()?.trim()
            if (input.isNullOrEmpty()) {
                tvError.text = getString(R.string.error_empty)
                tvError.visibility = TextView.VISIBLE
                return
            }

            val songNumber = input.toIntOrNull() ?: run {
                tvError.text = getString(R.string.error_empty)
                tvError.visibility = TextView.VISIBLE
                return
            }

            val page = repository.getPage(songNumber)
            if (page == null) {
                tvError.text = getString(R.string.error_not_found, songNumber)
                tvError.visibility = TextView.VISIBLE
                return
            }

            tvError.visibility = TextView.GONE
            val intent = Intent(this, PdfViewerActivity::class.java).apply {
                putExtra(PdfViewerActivity.EXTRA_PAGE, page)
                putExtra(PdfViewerActivity.EXTRA_SONG_NUMBER, songNumber)
            }
            startActivity(intent)
        }

        btnOpen.setOnClickListener { openSong() }

        // Allow triggering via keyboard "Go" action
        etSongNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openSong()
                true
            } else false
        }
    }
}
