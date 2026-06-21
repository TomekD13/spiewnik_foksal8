package com.spiewnik.app.utils

/**
 * Converts between 1-based page numbers (as stored in piesni.json)
 * and 0-based PDF page indices (as used by PdfRenderer).
 */
object PageConverter {
    fun jsonPageToPdfIndex(jsonPage: Int): Int = jsonPage - 1
    fun pdfIndexToJsonPage(pdfIndex: Int): Int = pdfIndex + 1
}
