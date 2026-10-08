package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*

private val MineBg = Color(0xFF0B0B0D)
private val MineCardBg = Color(0xFF18181B)
private val MineDim = Color(0xFF9A9AA3)
private val MineAccent = Color(0xFFF2A33A)

@Composable
fun MineHost(open: Boolean, movies: List<Movie>, onMovie: (Int) -> Unit, onClose: () -> Unit) {
    val p = remember { Animatable(if (open) 1f else 0f) }
    var visible by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) visible = true
        p.animateTo(if (open) 1f else 0f, tween(420, easing = FastOutSlowInEasing))
        if (!open) visible = false
    }
    BackHandler(open, onClose)
    if (!visible) return
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = p.value; val s = 1.06f - 0.06f * p.value; scaleX = s; scaleY = s }
            .background(MineBg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) { MineHome(movies, onMovie, onClose) }
}

@Composable
private fun MineHome(movies: List<Movie>, onMovie: (Int) -> Unit, onExit: () -> Unit) {
    val tv = LocalTvMode.current
    var kind by rememberSaveable { mutableIntStateOf(-1) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val pool = remember(movies, kind) { movies.filter { kind < 0 || it.kind.ordinal == kind } }
    val shown = remember(pool, genre) { pool.filter { genre == null || genre in it.genres } }
    val top = remember(movies) { movies.sortedByDescending { it.rating } }
    val genres = remember(pool) { pool.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topInset() + 8.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "bar") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OneText("ONE+", OneType.Display, Color.White)
                    OneText("MINE", OneType.Display, MineAccent)
                }
                Box(Modifier.size(44.dp).press(onExit).clip(CircleShape).background(MineCardBg), Alignment.Center) {
                    OneIconView(OneIcon.Close) { Color.White }
                }
            }
        }
        item(key = "kinds") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MinePill(stringResource(R.string.filter_all), kind < 0, Modifier.tvAutoFocus()) { kind = -1; genre = null } }
                items(Kind.entries) { k -> MinePill(stringResource(k.title), kind == k.ordinal) { kind = k.ordinal; genre = null } }
            }
        }
        if (kind >= 0) {
            item(key = "genres") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { MinePill(stringResource(R.string.filter_all), genre == null, Modifier, true) { genre = null } }
                    items(genres, key = { it }) { g -> MinePill(g, genre == g, Modifier, true) { genre = g } }
                }
            }
            items(shown.chunked(if (tv) 4 else 2), key = { row -> row.first().id }) { row ->
                Row(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp)) {
                    row.forEach { m -> MineCard(m, Modifier.weight(1f), onMovie) }
                    repeat((if (tv) 4 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            if (top.isNotEmpty()) item(key = "hero") { MineFeatured(top.first(), onMovie) }
            item(key = "top") { MineShelf(stringResource(R.string.mine_rated), top.take(10), null, onMovie) }
            Kind.entries.forEach { k ->
                val shelf = movies.filter { it.kind == k }
                if (shelf.isNotEmpty()) item(key = k.name) { MineShelf(stringResource(k.title), shelf.take(10), { kind = k.ordinal }, onMovie) }
            }
        }
    }
}

@Composable
private fun MinePill(text: String, selected: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(if (small) 32.dp else 38.dp).press(onClick).clip(RoundedCornerShape(50))
            .background(if (selected) MineAccent else MineCardBg).padding(horizontal = 16.dp),
        Alignment.Center,
    ) { OneText(text, OneType.Body, if (selected) Color.Black else Color.White, maxLines = 1) }
}

@Composable
private fun MineShelf(title: String, list: List<Movie>, onAll: (() -> Unit)?, onMovie: (Int) -> Unit) {
    if (list.isEmpty()) return
    val wide = screenWidth() >= 600.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OneText(title, OneType.Section, Color.White)
            if (onAll != null) Box(Modifier.press(onAll).padding(vertical = 6.dp, horizontal = 4.dp)) {
                OneText(stringResource(R.string.sec_all), OneType.Body, MineAccent)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { m -> MineCard(m, Modifier.width(if (wide) 260.dp else 200.dp), onMovie) }
        }
    }
}

@Composable
private fun MineCard(m: Movie, modifier: Modifier, onMovie: (Int) -> Unit) {
    Column(modifier.press { onMovie(m.id) }.clip(RoundedCornerShape(16.dp)).background(MineCardBg)) {
        MineImage(m, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            OneText(m.title, OneType.Body, Color.White, maxLines = 1)
            OneText(listOfNotNull(m.year.takeIf { it > 0 }?.toString(), m.genres.firstOrNull()).joinToString(" · "), OneType.Caption, MineDim, maxLines = 1)
        }
    }
}

@Composable
private fun MineFeatured(m: Movie, onMovie: (Int) -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().widthIn(max = 720.dp).press { onMovie(m.id) }.clip(RoundedCornerShape(20.dp)).background(MineCardBg),
    ) {
        MineImage(m, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(4.dp)) {
            OneText(m.title, OneType.Title, Color.White, maxLines = 1)
            OneText(listOfNotNull(m.year.takeIf { it > 0 }?.toString(), m.genres.firstOrNull(), "★ ${m.rating}").joinToString(" · "), OneType.Caption, MineAccent, maxLines = 1)
            if (m.synopsis.isNotBlank()) OneText(m.synopsis, OneType.Caption, MineDim, maxLines = 2)
        }
    }
}

@Composable
private fun MineImage(m: Movie, modifier: Modifier) {
    Box(modifier.background(Color(0xFF222226)), Alignment.Center) {
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.fillMaxSize())
        else OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 48.sp), MineDim)
    }
}
