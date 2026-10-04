package com.oneplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*

/**
 * "قائمتي": a horizontal shelf of the same poster cards the Home page uses, newest saved first.
 * A tap opens the movie; it is taken off the list from the movie page. Shown only when the list is not empty.
 */
@Composable
internal fun SavedShelf(movies: List<Movie>, wide: Boolean, onOpen: (Int) -> Unit) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 24.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OneText(stringResource(R.string.sec_list), OneType.Section, c.text)
            OneDot(c.dim)
            OneText("${movies.size}", OneType.Section, c.dim)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies, key = { it.id }) { m -> Poster(m, Modifier.width(if (wide) 156.dp else 124.dp)) { onOpen(m.id) } }
        }
    }
}
