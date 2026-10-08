package com.oneplus.app.data

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val MaxResume = 20
private const val MinResumeMs = 15_000L
private const val DoneFraction = 0.95f

@Stable
class Library(private val sp: SharedPreferences) {
    var list by mutableStateOf(ids("list")); private set
    var progress by mutableStateOf(readProgress()); private set

    fun toggle(id: Int) {
        list = if (id in list) list - id else listOf(id) + list
        sp.edit().putString("list", list.joinToString(",")).apply()
    }

    fun saveProgress(id: Int, pos: Long, dur: Long) {
        if (dur <= 0L) return
        if (progress[id]?.let { it.first == pos && it.second == dur } == true) return
        val rest = progress - id
        progress = if (pos < MinResumeMs || pos >= dur * DoneFraction) rest
        else (mapOf(id to (pos to dur)) + rest).entries.take(MaxResume).associate { it.toPair() }
        sp.edit().putString("progress", progress.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }).apply()
    }

    fun resumeMs(id: Int): Long = progress[id]?.first ?: 0L
    fun fraction(id: Int): Float = progress[id]?.let { (p, d) -> (p.toFloat() / d).coerceIn(0f, 1f) } ?: 0f

    fun clearHistory() {
        progress = emptyMap()
        sp.edit().remove("progress").apply()
    }

    private fun ids(key: String) = sp.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun readProgress(): Map<Int, Pair<Long, Long>> = sp.getString("progress", "").orEmpty().split(';').mapNotNull { s ->
        val p = s.split(':')
        val id = p.getOrNull(0)?.toIntOrNull(); val pos = p.getOrNull(1)?.toLongOrNull(); val dur = p.getOrNull(2)?.toLongOrNull()
        if (id != null && pos != null && dur != null && dur > 0L) id to (pos to dur) else null
    }.toMap()
}
