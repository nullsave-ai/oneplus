package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.oneplus.app.R
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.data.Library
import com.oneplus.app.ui.system.*

/**
 * Home of the phone's Feed style: one editorial hero, then numbered sections that all slide sideways (nothing stacks into a
 * long column of big cards). Cards follow the user's size / roundness (see [FeedLook]). TV Mode keeps [FeedHome].
 */
@Composable
fun LuxHome(
    state: UiState, list: LazyListState, portrait: Boolean, lib: Library, onMovie: (Int) -> Unit, onChannel: (Int) -> Unit,
    onAllMovies: () -> Unit, onAllChannels: () -> Unit, onMatches: (Int) -> Unit,
) {
    val d = state.data
    val k = LocalFeedLook.current.size
    val seen = remember { mutableSetOf<Int>() }
    val byId = d.movies.associateBy { it.id }
    val resume = lib.progress.keys.mapNotNull { byId[it] }
    val hero = resume.firstOrNull() ?: d.movies.firstOrNull()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), list, PaddingValues(top = toolbarInset(), bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        if (hero != null) item(key = "hero") { Hero(hero, list, portrait, lib.fraction(hero.id).takeIf { hero in resume }, Modifier.reveal(0, seen)) { onMovie(hero.id) } }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Section(R.string.sec_matches, 1, null, Modifier.reveal(1, seen)) {
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(236.dp * k)) { onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Section(R.string.sec_resume, 2, null, Modifier.reveal(2, seen)) {
                items(resume, key = { it.id }) { m -> ResumeCard(m, lib.fraction(m.id), Modifier.width(176.dp * k)) { onMovie(m.id) } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            Section(R.string.sec_movies, 3, onAllMovies, Modifier.reveal(3, seen)) {
                items(d.movies.take(10), key = { it.id }) { m -> LuxPoster(m, Modifier.width(138.dp * k)) { onMovie(m.id) } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Section(R.string.sec_channels, 4, onAllChannels, Modifier) {
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { onChannel(ch.id) } }
            }
        }
    }
}

/** "01  Title ——" and, optionally, a round "all" button on the opposite edge; the content is one sideways row. */
@Composable
private fun Section(
    @StringRes title: Int, n: Int, onAll: (() -> Unit)?, modifier: Modifier,
    row: LazyListScope.() -> Unit,
) {
    val c = LocalColors.current
    Column(modifier, Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OneText("0$n", OneType.Caption, c.accent)
                OneText(stringResource(title), OneType.Lux, c.text)
                Box(Modifier.width(28.dp).height(1.dp).background(Brush.horizontalGradient(listOf(c.accent, c.accent.copy(alpha = 0f)))))
            }
            if (onAll != null) Box(Modifier.size(34.dp).press(onAll).border(0.5.dp, c.border, CircleShape), Alignment.Center) {
                OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = row)
    }
}

/** The featured movie: a big rounded card, its artwork drifting slower than the page (parallax), title in serif over a dark fade. */
@Composable
private fun Hero(m: Movie, list: LazyListState, portrait: Boolean, progress: Float?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(30.dp * LocalFeedLook.current.round)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.glass)) }
    val shade = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.92f))) }
    Box(modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(if (portrait) 0.82f else 2.6f).press(onClick).clip(shape).background(fill).border(0.5.dp, c.border, shape)) {
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer {
            val off = if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset * 0.25f else 0f
            translationY = off.coerceAtMost(28.dp.toPx()); scaleX = 1.12f; scaleY = 1.12f
        })
        Box(Modifier.matchParentSize().background(shade))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(22.dp), Arrangement.spacedBy(8.dp)) {
            OneText(stringResource(R.string.lux_featured), OneType.Caption, c.accent)
            OneText(m.title, OneType.LuxHero, Color.White, maxLines = 2)
            OneText("${m.year}  ·  ${m.genres.joinToString(" · ")}", OneType.Caption, Color.White.copy(alpha = 0.72f), maxLines = 1)
            Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(c.accent).padding(horizontal = 18.dp, vertical = 10.dp),
                    Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                ) {
                    OneIconView(OneIcon.Play, Modifier.size(18.dp)) { c.onAccent }
                    OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body, c.onAccent, maxLines = 1)
                }
                if (progress != null) Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
    }
}

/** A poster with its title and year UNDER it (the artwork stays clean); rating as a small badge. */
@Composable
private fun LuxPoster(m: Movie, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(20.dp * LocalFeedLook.current.round)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.42f), c.dim.copy(alpha = 0.16f))) }
    Column(modifier.press(onClick), Arrangement.spacedBy(9.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(fill).border(0.5.dp, c.border, shape), Alignment.Center) {
            OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 56.sp), c.text.copy(alpha = 0.22f))
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
private fun MatchCard(m: Match, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(24.dp * LocalFeedLook.current.round)
    Column(
        modifier.press(onClick).glass(2, 24.dp).then(if (m.live) Modifier.border(1.dp, c.accent.copy(alpha = 0.7f), shape) else Modifier).padding(16.dp),
        Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OneText(m.time, OneType.Lux, c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (m.live) OneDot(c.accent)
                OneText(m.status, OneType.Caption, if (m.live) c.accent else c.dim)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Team(m.home); Team(m.away) }
        OneText(m.competition, OneType.Caption, c.dim, maxLines = 1)
    }
}
