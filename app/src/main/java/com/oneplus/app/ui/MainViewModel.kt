package com.oneplus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.HomeData
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.SampleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class UiState(val query: String = "", val data: HomeData = HomeData(emptyList(), emptyList(), emptyList()))

class MainViewModel(repo: HomeRepository = SampleRepository()) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(repo.data, query) { d, q ->
        if (q.isBlank()) UiState(q, d) else UiState(q, HomeData(
            d.matches.filter { q in it.home || q in it.away || q in it.competition },
            d.movies.filter { q in it.title },
            d.channels.filter { q in it.name },
        ))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQuery(q: String) { query.value = q }
}
