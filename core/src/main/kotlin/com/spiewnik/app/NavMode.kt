package com.spiewnik.app

enum class NavMode {
    SPREAD, PAGE, SONG;

    fun next(): NavMode = when (this) {
        SPREAD -> PAGE
        PAGE   -> SONG
        SONG   -> SPREAD
    }

    fun label(): String = when (this) {
        SPREAD -> "Rozkładówka"
        PAGE   -> "Strona"
        SONG   -> "Pieśń"
    }

    companion object {
        /**
         * Domyślny tryb dla orientacji ekranu: poziom → Pieśń, pion → Strona.
         * Czysta reguła współdzielona przez obie platformy (android: orientacja tabletu,
         * desktop: okno traktowane jak poziome).
         */
        fun defaultFor(isLandscape: Boolean): NavMode = if (isLandscape) SONG else PAGE
    }
}
