package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*

@Composable
fun HomeScreen(state: UiState, onQuery: (String) -> Unit, wide: Boolean) {
    val c = LocalColors.current
    val list = rememberLazyListState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 24 } }
    val headerA by animateFloatAsState(if (scrolled) 1f else 0f, tween(200), label = "header")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    val d = state.data

    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 880.dp).fillMaxSize(), list,
            PaddingValues(top = top + (if (searching) 124.dp else 72.dp), bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            if (d.matches.isNotEmpty()) item(key = "matches") { Block(R.string.sec_matches) { MatchSchedule(d.matches, wide) } }
            if (d.movies.isNotEmpty()) item(key = "movies") {
                Block(R.string.sec_movies) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(d.movies, key = { it.id }) { Poster(it, if (wide) 156.dp else 124.dp) }
                    }
                }
            }
            if (d.channels.isNotEmpty()) item(key = "channels") { Block(R.string.sec_channels) { ChannelRail(d.channels, wide) } }
        }
        // Floating header: integrates with the page, gains opacity on scroll.
        Column(
            Modifier.fillMaxWidth().drawBehind { drawRect(c.bg.copy(alpha = 0.88f * headerA)) }
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                OneText(stringResource(R.string.app_name), OneType.Display, c.text)
                Box(Modifier.size(44.dp).glass(2, 16.dp).press { searching = !searching; if (!searching) onQuery("") }, Alignment.Center) {
                    OneIconView(OneIcon.Search) { if (searching) c.accent else c.text }
                }
            }
            AnimatedVisibility(searching) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).glass(3, 18.dp).padding(horizontal = 16.dp), Alignment.CenterStart) {
                    BasicTextField(
                        state.query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = OneType.Body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                        decorationBox = { inner ->
                            if (state.query.isEmpty()) OneText(stringResource(R.string.search_hint), OneType.Body, c.dim)
                            inner()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Block(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OneText(stringResource(title), OneType.Section, LocalColors.current.text, Modifier.padding(horizontal = 20.dp))
        content()
    }
}

@Composable
private fun MatchSchedule(matches: List<Match>, wide: Boolean) {
    var open by rememberSaveable { mutableIntStateOf(-1) }
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(2, 22.dp).animateContentSize().padding(vertical = 8.dp)) {
        if (wide) matches.chunked(2).forEach { pair ->
            Row {
                pair.forEach { m -> MatchRow(m, open == m.id, { open = if (open == m.id) -1 else m.id }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        } else matches.forEach { m -> MatchRow(m, open == m.id, { open = if (open == m.id) -1 else m.id }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun MatchRow(m: Match, expanded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    Column(modifier.press(onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(52.dp)) {
                OneText(m.time, OneType.Section, c.text)
                OneText(m.status, OneType.Caption, if (m.live) c.accent else c.dim)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Team(m.home); Team(m.away) }
        }
        if (expanded) OneText("${m.competition}  ·  ${m.channel}", OneType.Caption, c.dim, Modifier.padding(top = 12.dp, start = 68.dp))
    }
}

@Composable
private fun Team(name: String) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(c.accent.copy(alpha = 0.14f), CircleShape), Alignment.Center) {
            OneText(name.take(1), OneType.Caption, c.accent)
        }
        OneText(name, OneType.Body, c.text, maxLines = 1)
    }
}

@Composable
private fun Poster(movie: Movie, width: Dp) {
    val c = LocalColors.current
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.40f), c.dim.copy(alpha = 0.22f))) }
    Box(Modifier.width(width).aspectRatio(2f / 3f).press { }.clip(RoundedCornerShape(16.dp)).background(fill)) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))).padding(12.dp)
        ) {
            OneText(movie.title, OneType.Body, Color.White, maxLines = 1)
            OneText(movie.year, OneType.Caption, Color.White.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelRail(channels: List<Channel>, wide: Boolean) {
    if (wide) FlowRow(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp), Arrangement.spacedBy(12.dp)) {
        channels.forEach { ChannelTile(it) }
    } else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(channels, key = { it.id }) { ChannelTile(it) }
    }
}

@Composable
private fun ChannelTile(ch: Channel) {
    val c = LocalColors.current
    Column(Modifier.width(72.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).press { }.glass(2, 18.dp), Alignment.Center) { OneText(ch.name.take(1), OneType.Section, c.accent) }
        OneText(ch.name, OneType.Caption, c.dim, maxLines = 1)
    }
}
