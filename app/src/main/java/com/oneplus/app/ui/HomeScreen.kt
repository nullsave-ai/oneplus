package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Channel
import com.oneplus.app.data.Library
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*

@Composable
fun HomeScreen(
    state: UiState, list: LazyListState, wide: Boolean, portrait: Boolean,
    lib: Library, onMovie: (Int) -> Unit, onChannel: (Int) -> Unit, onAllMovies: () -> Unit, onAllChannels: () -> Unit,
    onMatches: (Int) -> Unit, onAllList: () -> Unit, onAllRecent: () -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    val d = state.data
    // the viewer's shelves, limited to what the search matches; "continue" = stopped part-way, "recent" = the rest of the history
    val byId = d.movies.associateBy { it.id }
    val resume = lib.recent.filter { it in lib.progress }.mapNotNull { byId[it] }
    val saved = lib.list.mapNotNull { byId[it] }
    val recent = lib.recent.filter { it !in lib.progress }.mapNotNull { byId[it] }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 880.dp).fillMaxSize(), list,
            PaddingValues(top = toolbarInset(), bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // a teaser: a tap on any row opens the full matches page (the details are there)
            if (d.matches.isNotEmpty()) item(key = "matches") { Block(R.string.sec_matches, null) { MatchSchedule(d.matches.take(4), wide, onOpen = onMatches) } }
            if (resume.isNotEmpty()) item(key = "resume") { Block(R.string.sec_resume, null) { MovieRow(resume, wide, lib, onMovie) } }
            if (d.movies.isNotEmpty()) item(key = "movies") {
                Block(R.string.sec_movies, onAllMovies) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // the row is a teaser; the full catalogue lives behind "الكل"
                        items(d.movies.take(10), key = { it.id }) { m -> Poster(m, Modifier.width(if (wide) 156.dp else 124.dp)) { onMovie(m.id) } }
                    }
                }
            }
            if (saved.isNotEmpty()) item(key = "list") { Block(R.string.sec_list, onAllList) { MovieRow(saved.take(10), wide, lib, onMovie) } }
            if (recent.isNotEmpty()) item(key = "recent") { Block(R.string.sec_recent, onAllRecent) { MovieRow(recent.take(10), wide, lib, onMovie) } }
            if (d.channels.isNotEmpty()) item(key = "channels") { Block(R.string.sec_channels, onAllChannels) { ChannelSection(d.channels, portrait, onChannel) } }
        }
    }
}

/** Section with a title and, optionally, an "الكل" action on the opposite edge. */
@Composable
private fun Block(@StringRes title: Int, onAll: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OneText(stringResource(title), OneType.Section, c.text)
            if (onAll != null) Row(
                Modifier.press(onAll).padding(vertical = 6.dp, horizontal = 4.dp),
                Arrangement.spacedBy(2.dp), Alignment.CenterVertically,
            ) {
                OneText(stringResource(R.string.sec_all), OneType.Body, c.accent)
                OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.accent }
            }
        }
        content()
    }
}

/** A shelf of posters; those stopped part-way carry a progress bar. */
@Composable
private fun MovieRow(movies: List<Movie>, wide: Boolean, lib: Library, onMovie: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(movies, key = { it.id }) { m -> Poster(m, Modifier.width(if (wide) 156.dp else 124.dp), lib.fraction(m.id)) { onMovie(m.id) } }
    }
}

/**
 * The schedule table. With [onOpen] (Home) a tap on any row opens the full page instead of expanding it;
 * without it (the matches page) a tap expands the row, and [initialOpen] is the row expanded from the start.
 */
@Composable
internal fun MatchSchedule(matches: List<Match>, wide: Boolean, initialOpen: Int = -1, onOpen: ((Int) -> Unit)? = null) {
    var open by rememberSaveable { mutableIntStateOf(initialOpen) }
    val click = { m: Match -> if (onOpen != null) onOpen(m.id) else open = if (open == m.id) -1 else m.id }
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(2, 22.dp).animateContentSize()) {
        if (wide) matches.chunked(2).forEach { pair ->
            Row {
                pair.forEach { m -> MatchRow(m, open == m.id, { click(m) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        } else matches.forEach { m -> MatchRow(m, open == m.id, { click(m) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun MatchRow(m: Match, expanded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    Column(modifier.press(onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(52.dp)) {
                OneText(m.time, OneType.Section, c.text)
                OneText(m.status, OneType.Caption, if (m.live) c.accent else c.dim)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Team(m.home); Team(m.away) }
        }
        if (expanded) Row(Modifier.padding(top = 12.dp, start = 68.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OneText(m.competition, OneType.Caption, c.dim)
            OneDot(c.dim)
            OneText(m.channel, OneType.Caption, c.dim)
        }
    }
}

@Composable
private fun Team(name: String) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(c.accentSoft, CircleShape), Alignment.Center) {
            OneText(name.take(1), OneType.Caption, c.accent)
        }
        OneText(name, OneType.Body, c.text, maxLines = 1)
    }
}

@Composable
internal fun Poster(movie: Movie, modifier: Modifier, progress: Float = 0f, onClick: () -> Unit) {
    val c = LocalColors.current
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.40f), c.dim.copy(alpha = 0.22f))) }
    Box(modifier.aspectRatio(2f / 3f).press(onClick).clip(RoundedCornerShape(16.dp)).background(fill)) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))).padding(12.dp)
        ) {
            OneText(movie.title, OneType.Body, Color.White, maxLines = 1)
            OneText("${movie.year}", OneType.Caption, Color.White.copy(alpha = 0.7f))
            if (progress > 0f) Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.accent))
            }
        }
    }
}

/** Portrait: a two-column grid of cards. Landscape / tablets: the compact tile wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelSection(channels: List<Channel>, portrait: Boolean, onChannel: (Int) -> Unit) {
    if (portrait) Column(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp)) {
        channels.take(8).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                pair.forEach { ch -> ChannelCard(ch, Modifier.weight(1f)) { onChannel(ch.id) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else FlowRow(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp), Arrangement.spacedBy(12.dp)) {
        channels.take(8).forEach { ch -> ChannelTile(ch) { onChannel(ch.id) } }
    }
}

@Composable
private fun ChannelTile(ch: Channel, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.width(72.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).press(onClick).glass(2, 18.dp), Alignment.Center) { OneText(ch.name.take(1), OneType.Section, c.accent) }
        OneText(ch.name, OneType.Caption, c.dim, maxLines = 1)
    }
}
