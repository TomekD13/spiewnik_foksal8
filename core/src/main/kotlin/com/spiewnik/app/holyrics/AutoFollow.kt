package com.spiewnik.app.holyrics

/** Pure decision for Holyrics auto-follow (shared + testable). */
object AutoFollow {
    /**
     * Should the app switch to [reported] (the number Holyrics shows now)?
     * Yes only when it's a real number, different from what we last acted on
     * ([lastSeen]) and not already open ([currentlyOpen]). Acting once per change
     * avoids re-opening and toast spam for numbers absent from the songbook.
     */
    fun shouldOpen(reported: Int?, lastSeen: Int?, currentlyOpen: Int?): Boolean =
        reported != null && reported != lastSeen && reported != currentlyOpen
}
