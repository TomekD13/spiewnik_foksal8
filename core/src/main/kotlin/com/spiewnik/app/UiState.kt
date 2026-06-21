package com.spiewnik.app

import com.spiewnik.app.data.Song

/**
 * Immutable navigation/UI state. Pure logic — shared by Android and Windows.
 *
 * SONG mode:   shows song pages from strony_pdf (1 or 2). Arrows jump prev/next song.
 * SPREAD mode: shows N and N+1 from all PDF pages. Arrows move by 1 page.
 * PAGE mode:   shows single page N from all PDF pages. Arrows move by 1 page.
 *
 * [songPages] / [songPageIndex] — used in SONG mode only.
 * [currentPdfPage] / [totalPdfPages] — used in SPREAD and PAGE modes.
 */
data class UiState(
    val song: Song? = null,
    val navMode: NavMode = NavMode.SPREAD,
    val hasPrevSong: Boolean = false,
    val hasNextSong: Boolean = false,
    // SONG mode
    val songPages: List<Int> = emptyList(),
    val songPageIndex: Int = 0,
    // SPREAD / PAGE mode
    val currentPdfPage: Int = 1,
    val totalPdfPages: Int = 0,
) {
    val leftPageNumber: Int? get() = when (navMode) {
        NavMode.SONG -> songPages.getOrNull(songPageIndex)
        else         -> currentPdfPage.takeIf { totalPdfPages > 0 && it in 1..totalPdfPages }
    }

    val rightPageNumber: Int? get() = when (navMode) {
        NavMode.SPREAD -> (currentPdfPage + 1).takeIf { it <= totalPdfPages }
        NavMode.SONG   -> songPages.getOrNull(songPageIndex + 1)
        NavMode.PAGE   -> null
    }

    val leftPdfIndex: Int?  get() = leftPageNumber?.minus(1)
    val rightPdfIndex: Int? get() = rightPageNumber?.minus(1)

    val displayPages: String get() {
        val l = leftPageNumber ?: return ""
        val r = rightPageNumber
        return if (r != null) "str. $l–$r" else "str. $l"
    }

    val canGoLeft: Boolean get() = when (navMode) {
        NavMode.SONG -> hasPrevSong
        else         -> totalPdfPages > 0 && currentPdfPage > 1
    }

    val canGoRight: Boolean get() = when (navMode) {
        NavMode.SONG -> hasNextSong
        else         -> totalPdfPages > 0 && currentPdfPage < totalPdfPages
    }
}
