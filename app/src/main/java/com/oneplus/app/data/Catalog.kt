package com.oneplus.app.data

import androidx.compose.runtime.Immutable

@Immutable
class Catalog(val data: HomeData) {
    val byId: Map<Int, Movie> = data.movies.associateBy { it.id }
    val shelves: Map<Kind, List<Movie>> = Kind.entries.associateWith { k -> data.movies.filter { it.kind == k } }
    val topRated: List<Movie> = data.movies.sortedByDescending { it.rating }
    val channelsById: Map<Int, Channel> = data.channels.associateBy { it.id }
    val byGroup: Map<String, List<Channel>> = data.channels.groupBy { it.group }
    val groups: List<String> = byGroup.keys.toList()
}
