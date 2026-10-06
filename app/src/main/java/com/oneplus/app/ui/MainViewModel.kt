package com.oneplus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.AppConfig
import com.oneplus.app.data.HomeData
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.RemoteFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

private val EmptyHome = HomeData(emptyList(), emptyList(), emptyList())

/** [data] is filtered by the search query; [all] is the full catalogue (detail pages must not depend on the query). */
data class UiState(val query: String = "", val data: HomeData = EmptyHome, val all: HomeData = EmptyHome)

private const val MaxQuery = 64

class MainViewModel(private val repo: HomeRepository = RemoteFeedRepository()) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(repo.data, query) { d, q ->
        if (q.isBlank()) UiState(q, d, d) else UiState(q, HomeData(
            d.matches.filter { q in it.home || q in it.away || q in it.competition },
            d.movies.filter { q in it.title },
            d.channels.filter { q in it.name },
        ), d)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    val config: StateFlow<AppConfig> = repo.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    fun onQuery(q: String) { query.value = q.take(MaxQuery) }
}
