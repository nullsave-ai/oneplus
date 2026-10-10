package com.oneplus.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.Catalog
import com.oneplus.app.data.CinemaApi
import com.oneplus.app.data.Episode
import com.oneplus.app.data.HomeData
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val EmptyHome = HomeData(emptyList(), emptyList(), emptyList())
private val EmptyCatalog = Catalog(EmptyHome)

@Immutable
class UiState(val query: String = "", val view: Catalog = EmptyCatalog, val base: Catalog = EmptyCatalog) {
    val data: HomeData get() = view.data
    val all: HomeData get() = base.data
}

private const val MaxQuery = 64

class MainViewModel(private val repo: HomeRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val extraMovies = MutableStateFlow<Map<Kind, List<Movie>>>(emptyMap())
    private val currentPages = ConcurrentHashMap<Kind, Int>()
    @Volatile private var loadingMore = false

    val liveSearchResults = MutableStateFlow<List<Movie>>(emptyList())
    val isSearchingLive = MutableStateFlow(false)
    private val dynamicEpisodes = ConcurrentHashMap<Int, List<Episode>>()

    val state: StateFlow<UiState> = combine(repo.data.map { Catalog(it) }, extraMovies, query) { base, extra, q ->
        val extraList = extra.values.flatten()
        val mergedMovies = if (extraList.isEmpty()) {
            base.data.movies
        } else {
            (base.data.movies + extraList).distinctBy { it.id }
        }
        val fullData = HomeData(base.data.matches, mergedMovies, base.data.channels)
        val fullCatalog = Catalog(fullData)
        val d = fullCatalog.data

        if (q.isBlank()) {
            UiState(q, fullCatalog, fullCatalog)
        } else {
            UiState(q, Catalog(HomeData(
                d.matches.filter { q in it.home || q in it.away || q in it.competition },
                d.movies.filter { q in it.title },
                d.channels.filter { q in it.name },
            )), fullCatalog)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQuery(q: String) { query.value = q.take(MaxQuery) }

    fun loadMore(kind: Kind) {
        if (loadingMore) return
        val nextPage = (currentPages[kind] ?: 1) + 1
        loadingMore = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newItems = CinemaApi.fetchList(kind, nextPage)
                if (newItems.isNotEmpty()) {
                    currentPages[kind] = nextPage
                    extraMovies.update { cur ->
                        val existing = cur[kind].orEmpty()
                        val combined = (existing + newItems).distinctBy { it.id }
                        cur + (kind to combined)
                    }
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun searchLive(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) {
            liveSearchResults.value = emptyList()
            isSearchingLive.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isSearchingLive.value = true
            try {
                val results = CinemaApi.search(trimmed)
                liveSearchResults.value = results
                if (results.isNotEmpty()) {
                    extraMovies.update { cur ->
                        val byKind = results.groupBy { it.kind }
                        val next = cur.toMutableMap()
                        for ((k, list) in byKind) {
                            val existing = next[k].orEmpty()
                            next[k] = (existing + list).distinctBy { it.id }
                        }
                        next
                    }
                }
            } finally {
                isSearchingLive.value = false
            }
        }
    }

    fun registerEpisodes(movieId: Int, episodes: List<Episode>) {
        if (episodes.isNotEmpty()) {
            dynamicEpisodes[movieId] = episodes
        }
    }

    fun getEpisode(movieId: Int, epIndex: Int): Episode? {
        return dynamicEpisodes[movieId]?.getOrNull(epIndex)
    }
}
