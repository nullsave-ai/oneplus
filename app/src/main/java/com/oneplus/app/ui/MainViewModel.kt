package com.oneplus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.HomeData
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.ApiUrl
import com.oneplus.app.data.RemoteRepository
import com.oneplus.app.data.SampleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

private val EmptyHome = HomeData(emptyList(), emptyList(), emptyList())

data class UiState(val query: String = "", val data: HomeData = EmptyHome, val all: HomeData = EmptyHome)

private const val MaxQuery = 64

class MainViewModel(repo: HomeRepository = if (ApiUrl.isBlank()) SampleRepository() else RemoteRepository(ApiUrl)) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(repo.data, query) { d, q ->
        if (q.isBlank()) UiState(q, d, d) else {
            val s = q.trim()
            UiState(q, HomeData(
                d.matches.filter { it.home.contains(s, true) || it.away.contains(s, true) || it.competition.contains(s, true) },
                d.movies.filter { it.title.contains(s, true) || it.genres.any { g -> g.contains(s, true) } },
                d.channels.filter { it.name.contains(s, true) || it.group.contains(s, true) },
                d.update,
            ), d)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQuery(q: String) { query.value = q.take(MaxQuery) }
}
