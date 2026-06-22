package com.spiewnik.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * Thread-safe PDF page renderer with an LruCache.
 * Pages are numbered 0-based (PDF convention).
 * Call [open] once, then [renderPage] as needed, [close] when done.
 */
class PdfPageCache(private val context: Context) {

    companion object {
        private const val TAG = "PdfPageCache"
        private const val PDF_FILE = "Spiewnik.pdf"
    }

    private var renderer: PdfRenderer? = null

    /** LruCache keyed by "pageIndex:widthxheight" to avoid stale bitmaps after resize. */
    private val cache: LruCache<String, Bitmap>

    init {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        cache = object : LruCache<String, Bitmap>(maxKb / 6) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }

    /**
     * Opens the PDF from assets. Returns true on success.
     * Must be called from a background thread.
     *
     * PdfRenderer requires a seekable ParcelFileDescriptor pointing to the
     * start of the PDF. Assets inside an APK have an offset, so we copy the
     * file to cacheDir once and open it from there.
     */
    fun open(): Boolean {
        return try {
            val cacheFile = File(context.cacheDir, PDF_FILE)
            if (!cacheFile.exists()) {
                Log.i(TAG, "Copying PDF to cache…")
                context.assets.open(PDF_FILE).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.i(TAG, "PDF copied (${cacheFile.length() / 1024} KB)")
            }
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            synchronized(this) {
                renderer = PdfRenderer(pfd)
            }
            Log.i(TAG, "PDF opened, pages: ${renderer?.pageCount}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PDF", e)
            false
        }
    }

    val pageCount: Int
        get() = synchronized(this) { renderer?.pageCount ?: 0 }

    /**
     * Renders a single PDF page to a Bitmap.
     * [pageIndex] is 0-based. [width]/[height] are the target view dimensions in pixels.
     * Returns null if the page is out of range or rendering fails.
     */
    fun renderPage(pageIndex: Int, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        synchronized(this) {
            val r = renderer ?: return null
            if (pageIndex < 0 || pageIndex >= r.pageCount) {
                Log.w(TAG, "Page $pageIndex out of range (total: ${r.pageCount})")
                return null
            }
            val key = "$pageIndex:${width}x${height}"
            cache.get(key)?.let { return it }

            return try {
                val page = r.openPage(pageIndex)
                // Scale to fit keeping aspect ratio
                val pageW = page.width.toFloat()
                val pageH = page.height.toFloat()
                // 2x render resolution for quality zoom up to 2x without pixelation
                val scale = minOf(width / pageW, height / pageH) * 2f
                val bmpW = (pageW * scale).toInt().coerceAtLeast(1)
                val bmpH = (pageH * scale).toInt().coerceAtLeast(1)

                // RGB_565 zamiast ARGB_8888: połowa zużycia pamięci. Nuty są czarne na
                // białym tle (bitmapa jest zamalowywana na biało), a render PDF nie używa
                // przezroczystości — kanał alfa jest zbędny. Rozdzielczość renderowania
                // (skala 2×) pozostaje bez zmian, więc jakość obrazu nut się nie pogarsza.
                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.RGB_565)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                cache.put(key, bitmap)
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Render failed for page $pageIndex", e)
                null
            }
        }
    }

    fun close() {
        synchronized(this) {
            cache.evictAll()
            renderer?.close()
            renderer = null
        }
    }
}
