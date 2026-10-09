package com.oneplus.app.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.Catalog
import com.oneplus.app.data.HomeData
import com.oneplus.app.data.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

private val EmptyHome = HomeData(emptyList(), emptyList(), emptyList())
private val EmptyCatalog = Catalog(EmptyHome)

@Immutable
class UiState(val query: String = "", val view: Catalog = EmptyCatalog, val base: Catalog = EmptyCatalog) {
    val data: HomeData get() = view.data
    val all: HomeData get() = base.data
}

private const val MaxQuery = 64

class MainViewModel(repo: HomeRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(repo.data.map { Catalog(it) }, query) { base, q ->
        val d = base.data
        if (q.isBlank()) UiState(q, base, base) else UiState(q, Catalog(HomeData(
            d.matches.filter { q in it.home || q in it.away || q in it.competition },
            d.movies.filter { q in it.title },
            d.channels.filter { q in it.name },
        )), base)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQuery(q: String) { query.value = q.take(MaxQuery) }
}
