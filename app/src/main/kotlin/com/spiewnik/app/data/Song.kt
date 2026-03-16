package com.spiewnik.app.data

import com.google.gson.annotations.SerializedName

data class Song(
    @SerializedName("nr_piesni")  val number: Int,
    @SerializedName("tytul")      val title: String,
    @SerializedName("strony_pdf") val pages: List<Int>,
    @SerializedName("interpolated") val interpolated: Boolean = false
)
