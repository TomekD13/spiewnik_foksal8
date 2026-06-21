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
}
