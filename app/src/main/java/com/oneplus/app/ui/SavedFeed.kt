package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import java.util.Locale

/**
 * "قائمتي" as a feed: one card per saved movie, newest saved first (artwork, title, details, a short synopsis).
 * A tap opens the movie; the close button takes it off the list.
 */
@Composable
internal fun SavedFeed(movies: List<Movie>, onOpen: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            OneText(stringResource(R.string.sec_list), OneType.Section, c.text)
            if (movies.isNotEmpty()) {
                OneDot(c.dim)
                OneText("${movies.size}", OneType.Section, c.dim)
            }
        }
        if (movies.isEmpty()) Box(Modifier.fillMaxWidth().glass(2, 22.dp).padding(24.dp), Alignment.Center) {
            OneText(stringResource(R.string.list_empty), OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
        } else movies.forEach { m -> key(m.id) { FeedCard(m, { onOpen(m.id) }, { onRemove(m.id) }) } }
    }
}

@Composable
private fun FeedCard(m: Movie, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = LocalColors.current
    // stand-in until the artwork arrives (the same one the "continue watching" card uses)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.50f), c.dim.copy(alpha = 0.18f))) }
    val shade = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.45f))) }
    val duration = "${m.durationMin / 60} ${stringResource(R.string.unit_hour)} ${m.durationMin % 60} ${stringResource(R.string.unit_min)}"
    Column(Modifier.fillMaxWidth().press(onOpen).glass(2, 22.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(fill), Alignment.Center) {
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(shade))
            Box(Modifier.size(48.dp).vGlass(24.dp), Alignment.Center) { OneIconView(OneIcon.Play) { Color.White } }
        }
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp), Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OneText(m.title, OneType.Section, c.text, Modifier.weight(1f), maxLines = 1)
                Box(Modifier.size(40.dp).press(onRemove), Alignment.Center) { OneIconView(OneIcon.Close) { c.dim } }
            }
            Row(Modifier.padding(end = 8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                OneText("${m.year}", OneType.Caption, c.dim)
                OneDot(c.dim)
                OneText(String.format(Locale.US, "%.1f", m.rating), OneType.Caption, c.accent)
                OneDot(c.dim)
                OneText(duration, OneType.Caption, c.dim, maxLines = 1)
            }
            OneText(m.genres.joinToString(" · "), OneType.Caption, c.dim, Modifier.padding(end = 8.dp), maxLines = 1)
            OneText(m.synopsis, OneType.Body, c.dim, Modifier.padding(top = 2.dp, end = 8.dp), maxLines = 2)
        }
    }
}
