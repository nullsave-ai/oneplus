package com.oneplus.app.data

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val MaxRecent = 20
private const val MinResumeMs = 15_000L   // a few seconds in is not "watching"
private const val DoneFraction = 0.95f    // past this the movie counts as finished

/**
 * The viewer's own shelves, kept on the device: "قائمتي", the watch history and where each unfinished movie stopped.
 * Only movie ids and positions are stored (never URLs); all three are newest-first / small, so a plain string per key is enough.
 */
@Stable
class Library(private val sp: SharedPreferences) {
    var list by mutableStateOf(ids("list")); private set
    var recent by mutableStateOf(ids("recent")); private set
    /** Movie id -> (position, duration) in ms, for movies stopped part-way. */
    var progress by mutableStateOf(readProgress()); private set

    fun toggle(id: Int) {
        list = if (id in list) list - id else listOf(id) + list
        sp.edit().putString("list", list.joinToString(",")).apply()
    }

    fun watched(id: Int) {
        recent = (listOf(id) + recent.filter { it != id }).take(MaxRecent)
        sp.edit().putString("recent", recent.joinToString(",")).apply()
    }

    fun saveProgress(id: Int, pos: Long, dur: Long) {
        if (dur <= 0L) return
        progress = if (pos < MinResumeMs || pos >= dur * DoneFraction) progress - id else progress + (id to (pos to dur))
        sp.edit().putString("progress", progress.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }).apply()
    }

    fun resumeMs(id: Int): Long = progress[id]?.first ?: 0L
    fun fraction(id: Int): Float = progress[id]?.let { (p, d) -> (p.toFloat() / d).coerceIn(0f, 1f) } ?: 0f

    fun clearHistory() {
        recent = emptyList(); progress = emptyMap()
        sp.edit().remove("recent").remove("progress").apply()
    }

    private fun ids(key: String) = sp.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun readProgress(): Map<Int, Pair<Long, Long>> = sp.getString("progress", "").orEmpty().split(';').mapNotNull { s ->
        val p = s.split(':')
        val id = p.getOrNull(0)?.toIntOrNull(); val pos = p.getOrNull(1)?.toLongOrNull(); val dur = p.getOrNull(2)?.toLongOrNull()
        if (id != null && pos != null && dur != null && dur > 0L) id to (pos to dur) else null
    }.toMap()
}
