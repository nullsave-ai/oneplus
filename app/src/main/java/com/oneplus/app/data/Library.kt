package com.oneplus.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MaxResume = 20
private const val MinResumeMs = 15_000L
private const val DoneFraction = 0.95f

@Stable
class Library(private val store: Store, private val scope: CoroutineScope, legacy: Prefs) {
    var list by mutableStateOf(emptyList<Int>()); private set
    var progress by mutableStateOf(emptyMap<Int, Pair<Long, Long>>()); private set

    init {
        scope.launch {
            val oldList = legacy.string("list")
            val oldProgress = legacy.string("progress")
            if (oldList != null || oldProgress != null) {
                val ids = oldList.orEmpty().split(',').mapNotNull { it.toIntOrNull() }
                ids.forEachIndexed { i, id -> store.save(Saved(id, (ids.size - i).toLong())) }
                val rows = oldProgress.orEmpty().split(';').mapNotNull { s ->
                    val p = s.split(':')
                    val id = p.getOrNull(0)?.toIntOrNull(); val pos = p.getOrNull(1)?.toLongOrNull(); val dur = p.getOrNull(2)?.toLongOrNull()
                    if (id != null && pos != null && dur != null && dur > 0L) Progress(id, pos, dur, 0L) else null
                }
                rows.forEachIndexed { i, r -> store.mark(r.copy(at = (rows.size - i).toLong())) }
                legacy.remove("list", "progress")
            }
            list = store.saved().map { it.id }
            progress = store.progress().associate { it.id to (it.pos to it.dur) }
        }
    }

    fun toggle(id: Int) {
        val add = id !in list
        list = if (add) listOf(id) + list else list - id
        scope.launch { if (add) store.save(Saved(id, System.currentTimeMillis())) else store.unsave(id) }
    }

    fun saveProgress(id: Int, pos: Long, dur: Long) {
        if (dur <= 0L) return
        if (progress[id]?.let { it.first == pos && it.second == dur } == true) return
        val rest = progress - id
        val drop = pos < MinResumeMs || pos >= dur * DoneFraction
        progress = if (drop) rest else (mapOf(id to (pos to dur)) + rest).entries.take(MaxResume).associate { it.toPair() }
        scope.launch {
            if (drop) store.unmark(id) else { store.mark(Progress(id, pos, dur, System.currentTimeMillis())); store.trimProgress() }
        }
    }

    fun resumeMs(id: Int): Long = progress[id]?.first ?: 0L
    fun fraction(id: Int): Float = progress[id]?.let { (p, d) -> (p.toFloat() / d).coerceIn(0f, 1f) } ?: 0f

    fun clearHistory() {
        progress = emptyMap()
        scope.launch { store.clearProgress() }
    }
}
