package com.spiewnik.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spiewnik.app.model.SongMapping

class SongRepository(context: Context) {

    private val mappings: Map<Int, SongMapping> by lazy {
        loadMappings(context)
    }

    fun getPage(songNumber: Int): Int? = mappings[songNumber]?.page

    private fun loadMappings(context: Context): Map<Int, SongMapping> {
        val json = context.assets.open("songs.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<SongMapping>>() {}.type
        val list: List<SongMapping> = Gson().fromJson(json, type)
        return list.associateBy { it.songNumber }
    }
}
