package com.spiewnik.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "extra_page"
        const val EXTRA_SONG_NUMBER = "extra_song_number"
        private const val PDF_FILE = "spiewnik.pdf"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        // Pages in android-pdf-viewer are 0-indexed; songs.json uses 1-indexed page numbers.
        val page = intent.getIntExtra(EXTRA_PAGE, 1) - 1
        val songNumber = intent.getIntExtra(EXTRA_SONG_NUMBER, 0)

        supportActionBar?.title = "Pieśń $songNumber"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pdfView = findViewById<PDFView>(R.id.pdfView)
        pdfView.fromAsset(PDF_FILE)
            .defaultPage(page)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .load()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
