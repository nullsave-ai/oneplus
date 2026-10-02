package com.oneplus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneplus.app.data.Channel
import com.oneplus.app.data.ChannelRepository
import com.oneplus.app.data.SampleChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class UiState(val query: String = "", val channels: List<Channel> = emptyList())

class MainViewModel(repo: ChannelRepository = SampleChannelRepository()) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState> = combine(repo.channels, query) { list, q ->
        UiState(q, if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) })
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onQuery(q: String) { query.value = q }
}
