package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.AllGenres
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*

private val RatingSteps = listOf(0, 5, 6, 7, 8)
private val SortLabels = listOf(R.string.sort_new, R.string.sort_rating, R.string.sort_name)

/**
 * Full-screen "all movies" page. Fades/rises in with a spring, closes with back or the back button.
 * Filters (genre, year, minimum rating, sort) are local to one visit and reset the next time it opens.
 */
@Composable
fun MoviesHost(open: Boolean, movies: List<Movie>, portrait: Boolean, onMovie: (Int) -> Unit, onClose: () -> Unit) {
    val p = remember { Animatable(if (open) 1f else 0f) }
    var visible by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) visible = true
        p.animateTo(if (open) 1f else 0f, spring(1f, 380f))
        if (!open) visible = false
    }
    BackHandler(open, onClose)
    if (!visible) return

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 36.dp.toPx() }
            .ambient()
            .pointerInput(Unit) { detectTapGestures { } } // the page underneath must not receive touches
    ) { MoviesScreen(movies, portrait, onMovie, onClose) }
}

@Composable
private fun MoviesScreen(movies: List<Movie>, portrait: Boolean, onMovie: (Int) -> Unit, onClose: () -> Unit) {
    val c = LocalColors.current
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var year by rememberSaveable { mutableIntStateOf(0) }       // 0 = all
    var minRating by rememberSaveable { mutableIntStateOf(0) }  // 0 = all
    var sort by rememberSaveable { mutableIntStateOf(0) }       // index in SortLabels
    var panel by rememberSaveable { mutableStateOf(false) }

    val genres = remember(movies) { AllGenres.filter { g -> movies.any { g in it.genres } } }
    val years = remember(movies) { movies.map { it.year }.distinct().sortedDescending() }
    val shown = remember(movies, genre, year, minRating, sort) {
        movies
            .filter { m -> (genre == null || genre in m.genres) && (year == 0 || m.year == year) && (minRating == 0 || m.rating >= minRating) }
            .let { list ->
                when (sort) {
                    0 -> list.sortedByDescending { it.year }
                    1 -> list.sortedByDescending { it.rating }
                    else -> list.sortedBy { it.title }
                }
            }
    }
    val extraFilters = (if (year != 0) 1 else 0) + (if (minRating != 0) 1 else 0) + (if (sort != 0) 1 else 0)
    val reset = { genre = null; year = 0; minRating = 0; sort = 0 }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // header: back · title + count · filter
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).press(onClose).glass(3, 22.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
            Box(Modifier.weight(1f).height(44.dp).glass(3, 22.dp), Alignment.Center) {
                Row(Modifier.padding(horizontal = 16.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    OneText(stringResource(R.string.movies_title), OneType.Section, c.text, maxLines = 1)
                    OneDot(c.dim)
                    OneText("${shown.size}", OneType.Section, c.dim)
                }
            }
            Box(Modifier.size(44.dp).press { panel = !panel }.glass(3, 22.dp), Alignment.Center) {
                OneIconView(OneIcon.Filter) { if (panel || extraFilters > 0) c.accent else c.text }
                if (extraFilters > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).background(c.accent, CircleShape))
            }
        }

        // genre chips (always visible: the primary filter)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") { OneChip(stringResource(R.string.filter_all), genre == null, { genre = null }) }
            items(genres, key = { it }) { g -> OneChip(g, genre == g, { genre = g }) }
        }

        // year / rating / sort
        AnimatedVisibility(panel, enter = expandVertically(tween(220)) + fadeIn(tween(220)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(2, 22.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterRow(R.string.filter_year) {
                    item(key = "y0") { OneChip(stringResource(R.string.filter_all), year == 0, { year = 0 }) }
                    items(years, key = { "y$it" }) { y -> OneChip(y.toString(), year == y, { year = y }) }
                }
                FilterRow(R.string.filter_rating) {
                    items(RatingSteps, key = { "r$it" }) { r ->
                        OneChip(if (r == 0) stringResource(R.string.filter_all) else "$r+", minRating == r, { minRating = r })
                    }
                }
                FilterRow(R.string.filter_sort) {
                    items(SortLabels.indices.toList(), key = { "s$it" }) { i -> OneChip(stringResource(SortLabels[i]), sort == i, { sort = i }) }
                }
            }
        }

        if (shown.isEmpty()) Column(
            Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally,
        ) {
            OneText(stringResource(R.string.movies_empty), OneType.Body, c.dim)
            OneButton(stringResource(R.string.movies_reset), null, reset, Modifier.width(200.dp), primary = false)
        } else LazyVerticalGrid(
            if (portrait) GridCells.Fixed(3) else GridCells.Adaptive(130.dp), Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown, key = { it.id }) { m -> Poster(m, Modifier.fillMaxWidth()) { onMovie(m.id) } }
        }
    }
}

@Composable
private fun FilterRow(label: Int, chips: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OneText(stringResource(label), OneType.Caption, LocalColors.current.dim, Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = chips)
    }
}
