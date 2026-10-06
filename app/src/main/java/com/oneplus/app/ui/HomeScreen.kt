package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Channel
import com.oneplus.app.data.Library
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: UiState, list: LazyListState, wide: Boolean,
    lib: Library, onMovie: (Int) -> Unit, onChannel: (Int) -> Unit, onAllMovies: () -> Unit, onAllChannels: () -> Unit,
    onMatches: (Int) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    val d = state.data
    // "continue watching" = movies stopped part-way (newest first), limited to what the search matches
    val byId = d.movies.associateBy { it.id }
    val resume = lib.progress.keys.mapNotNull { byId[it] }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 880.dp).fillMaxSize(), list,
            PaddingValues(top = toolbarInset(), bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // a teaser: a tap on any row opens the full matches page (the details are there)
            if (d.matches.isNotEmpty()) item(key = "matches") { Block(R.string.sec_matches, null) { MatchSchedule(d.matches.take(4), wide, onOpen = onMatches) } }
            if (resume.isNotEmpty()) item(key = "resume") { Block(R.string.sec_resume, null) { ResumeRow(resume, wide, lib, onMovie) } }
            if (d.movies.isNotEmpty()) item(key = "movies") {
                Block(R.string.sec_movies, onAllMovies) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // the row is a teaser; the full catalogue lives behind "الكل"
                        items(d.movies.take(10), key = { it.id }) { m -> Poster(m, Modifier.width(if (wide) 156.dp else 124.dp)) { onMovie(m.id) } }
                    }
                }
            }
            if (d.channels.isNotEmpty()) item(key = "channels") { Block(R.string.sec_channels, onAllChannels) { ChannelShelf(d.channels, onChannel) } }
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

/**
 * "Continue watching": the poster turned on its side. A card is the poster's own box flipped (width = the poster's height) and
 * then 10 % smaller, so it reads as a landscape still rather than a cover, and about two of them fit on a phone with the third
 * peeking in. It shows the movie's second (landscape) artwork.
 */
@Composable
private fun ResumeRow(movies: List<Movie>, wide: Boolean, lib: Library, onMovie: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(movies, key = { it.id }) { m -> ResumeCard(m, lib.fraction(m.id), Modifier.width(if (wide) 210.dp else 168.dp)) { onMovie(m.id) } }
    }
}

@Composable
internal fun ResumeCard(movie: Movie, progress: Float, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    // stand-in until the artwork arrives (a bit stronger than the poster's, so the wide card still has presence)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.50f), c.dim.copy(alpha = 0.18f))) }
    val shade = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.78f))) }
    val left = (movie.durationMin * (1f - progress)).roundToInt().coerceAtLeast(1)
    Box(modifier.aspectRatio(3f / 2f).press(onClick).clip(RoundedCornerShape(18.dp)).background(fill)) {
        if (movie.backdrop.isNotBlank()) RemoteImage(movie.backdrop, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(shade))
        Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(30.dp).vGlass(15.dp), Alignment.Center) {
            OneIconView(OneIcon.Play, Modifier.size(16.dp)) { Color.White }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            OneText(movie.title, OneType.Body, Color.White, maxLines = 1)
            OneText(stringResource(R.string.resume_left, left), OneType.Caption, Color.White.copy(alpha = 0.75f), maxLines = 1)
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
            }
        }
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
internal fun Team(name: String) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(c.accentSoft, CircleShape), Alignment.Center) {
            OneText(name.take(1), OneType.Caption, c.accent)
        }
        OneText(name, OneType.Body, c.text, maxLines = 1)
    }
}

@Composable
internal fun Poster(movie: Movie, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.40f), c.dim.copy(alpha = 0.22f))) }
    Box(modifier.aspectRatio(2f / 3f).press(onClick).clip(RoundedCornerShape(16.dp)).background(fill)) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))).padding(12.dp)
        ) {
            OneText(movie.title, OneType.Body, Color.White, maxLines = 1)
            OneText("${movie.year}", OneType.Caption, Color.White.copy(alpha = 0.7f))
        }
    }
}

/** Channels on Home: small square tiles (logo, or the first letter) with the name under them, in one row that slides sideways. */
@Composable
internal fun ChannelShelf(channels: List<Channel>, onChannel: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { onChannel(ch.id) } }
    }
}

@Composable
internal fun ChannelTile(ch: Channel, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.width(72.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).press(onClick).glass(2, 18.dp), Alignment.Center) {
            if (ch.logo.isNotBlank()) RemoteImage(ch.logo, Modifier.matchParentSize()) else OneText(ch.name.take(1), OneType.Section, c.accent)
        }
        OneText(ch.name, OneType.Caption, c.dim, maxLines = 1)
    }
}

/** A poster with its title and year UNDER it (the artwork stays clean); rating as a small badge. */
@Composable
internal fun TitledPoster(m: Movie, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(20.dp)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.42f), c.dim.copy(alpha = 0.16f))) }
    Column(modifier.press(onClick), Arrangement.spacedBy(9.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(fill).border(0.5.dp, c.border, shape), Alignment.Center) {
            OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 56.sp), c.text.copy(alpha = 0.22f))
            OneText(
                "${(m.rating * 10).roundToInt() / 10f}", OneType.Caption, Color.White,
                Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            OneText(m.title, OneType.Body, c.text, maxLines = 1)
            OneText("${m.year}", OneType.Caption, c.dim, maxLines = 1)
        }
    }
}

/** One match as a card: time, the two teams, the competition. A live match is outlined in the accent. */
@Composable
internal fun MatchCard(m: Match, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.press(onClick).glass(2, 24.dp).then(if (m.live) Modifier.border(1.dp, c.accent.copy(alpha = 0.7f), shape) else Modifier).padding(16.dp),
        Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OneText(m.time, OneType.Serif, c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.live) OneDot(c.accent)
                OneText(m.status, OneType.Caption, if (m.live) c.accent else c.dim)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Team(m.home); Team(m.away) }
        OneText(m.competition, OneType.Caption, c.dim, maxLines = 1)
    }
}
