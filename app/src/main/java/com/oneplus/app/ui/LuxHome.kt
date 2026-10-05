package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HeroMs = 5500 // how long a featured card stays before the carousel moves on

/**
 * Home of the Feed look: a carousel of featured cards, then numbered sections that all slide sideways (nothing stacks into a
 * long column of big cards). Motion follows the finger: the carousel's artwork and text drift at different speeds, the whole
 * hero lets go of the page as it scrolls away, and the cards of every row shrink as they slide off its ends.
 * TV Mode keeps [FeedHome].
 */
@Composable
fun LuxHome(a: HomeArgs) {
    val d = a.state.data
    val k = LocalCardScale.current
    val seen = remember { mutableSetOf<Int>() }
    val byId = d.movies.associateBy { it.id }
    val resume = a.lib.progress.keys.mapNotNull { byId[it] }
    val heroes = remember(d.movies, resume) { (resume.take(2) + d.movies.sortedByDescending { it.rating }).distinct().take(5) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), a.list, PaddingValues(top = toolbarInset(), bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        if (heroes.isNotEmpty()) item(key = "hero") { HeroPager(heroes, a, Modifier.reveal(0, seen)) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Section(R.string.sec_matches, 1, null, Modifier.reveal(1, seen)) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(236.dp * k).edgeFx(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Section(R.string.sec_resume, 2, null, Modifier.reveal(2, seen)) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(176.dp * k).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            Section(R.string.sec_movies, 3, a.onAllMovies, Modifier.reveal(3, seen)) { row ->
                items(d.movies.take(10), key = { it.id }) { m -> LuxPoster(m, Modifier.width(138.dp * k).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Section(R.string.sec_channels, 4, a.onAllChannels, Modifier) { _ ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
    }
}

/** "01  Title ——" and, optionally, something on the opposite edge (a round "all" button). Shared by every Feed page. */
@Composable
internal fun SectionHead(@StringRes title: Int, n: Int, trailing: @Composable () -> Unit = {}) {
    val c = LocalColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OneText("0$n", OneType.Caption, c.accent)
            OneText(stringResource(title), OneType.Lux, c.text)
            Box(Modifier.width(28.dp).height(1.dp).background(Brush.horizontalGradient(listOf(c.accent, c.accent.copy(alpha = 0f)))))
        }
        trailing()
    }
}

/** A titled section whose content is one sideways row; [row] gets that row's state (for [edgeFx]). */
@Composable
private fun Section(
    @StringRes title: Int, n: Int, onAll: (() -> Unit)?, modifier: Modifier,
    row: LazyListScope.(LazyListState) -> Unit,
) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    Column(modifier, Arrangement.spacedBy(14.dp)) {
        SectionHead(title, n) {
            if (onAll != null) Box(Modifier.size(34.dp).press(onAll).border(0.5.dp, c.border, CircleShape), Alignment.Center) {
                OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text }
            }
        }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { this.row(state) }
    }
}

/**
 * The featured cards: a carousel with the neighbours peeking in. The artwork drifts slower than its card and the text faster,
 * so the layers part as you swipe; the neighbours sit smaller and dimmer. It advances by itself (story-style progress under it,
 * which you can tap), waits while a finger is on it, and as the page scrolls the whole block lets go and fades.
 */
@Composable
private fun HeroPager(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val n = movies.size
    val pager = rememberPagerState { n }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val timer = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.settledPage, dragged, n) {
        if (dragged || n < 2) return@LaunchedEffect
        timer.snapTo(0f)
        timer.animateTo(1f, tween(HeroMs, easing = LinearEasing))
        pager.animateScrollToPage((pager.settledPage + 1) % n)
    }
    Column(
        modifier.graphicsLayer {
            val l = a.list
            val f = if (l.firstVisibleItemIndex == 0) (l.firstVisibleItemScrollOffset / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
            translationY = f * size.height * 0.12f // drifts away slower than the page
            val k = 1f - 0.05f * f
            scaleX = k; scaleY = k; alpha = 1f - 0.6f * f
        },
        Arrangement.spacedBy(14.dp),
    ) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 28.dp), pageSpacing = 12.dp,
            key = { movies[it].id },
        ) { i ->
            val m = movies[i]
            HeroCard(m, { (i - pager.currentPage) - pager.currentPageOffsetFraction }, a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }, a.portrait) { a.onMovie(m.id) }
        }
        if (n > 1) HeroProgress(n, pager.currentPage, timer) { j -> scope.launch { pager.animateScrollToPage(j) } }
    }
}

/** [off]: where this page is relative to the one in focus, in pages (0 = centred, +1 = the next one, -1 = the previous). */
@Composable
private fun HeroCard(m: Movie, off: () -> Float, progress: Float?, portrait: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f // a page moves with the finger: physical left in RTL
    val shape = RoundedCornerShape(30.dp)
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.glass)) }
    val shade = remember { Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.92f))) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (portrait) 0.82f else 2.2f)
            .graphicsLayer { val o = abs(off()).coerceAtMost(1f); val k = 1f - 0.07f * o; scaleX = k; scaleY = k; alpha = 1f - 0.5f * o }
            .press(onClick).clip(shape).background(fill).border(0.5.dp, c.border, shape)
    ) {
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer {
            translationX = -off() * size.width * 0.16f * dir; scaleX = 1.34f; scaleY = 1.34f // the scale keeps the edges covered while it drifts
        }) else OneText(
            m.title.take(1), OneType.LuxHero.copy(fontSize = 200.sp, lineHeight = 220.sp), Color.White.copy(alpha = 0.10f),
            Modifier.align(Alignment.Center).graphicsLayer { translationX = -off() * 120.dp.toPx() * dir },
        )
        Box(Modifier.matchParentSize().background(shade))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(22.dp)
                .graphicsLayer { val o = off(); translationX = o * size.width * 0.3f * dir; alpha = (1f - abs(o) * 1.6f).coerceIn(0f, 1f) },
            Arrangement.spacedBy(8.dp),
        ) {
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

/** One thin segment per card: the ones before the current are full, the current fills with the time left on it. A tap jumps there. */
@Composable
private fun HeroProgress(n: Int, current: Int, timer: Animatable<Float, AnimationVector1D>, onPick: (Int) -> Unit) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.padding(horizontal = 36.dp).fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
        repeat(n) { j ->
            Box(Modifier.weight(1f).height(22.dp).press { onPick(j) }, Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(c.dim.copy(alpha = 0.28f)).drawBehind {
                        val f = if (j < current) 1f else if (j == current) timer.value else 0f
                        drawRect(c.text, Offset(if (rtl) size.width * (1f - f) else 0f, 0f), Size(size.width * f, size.height))
                    }
                )
            }
        }
    }
}

/** A poster with its title and year UNDER it (the artwork stays clean); rating as a small badge. */
@Composable
internal fun LuxPoster(m: Movie, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(20.dp)
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
    val shape = RoundedCornerShape(24.dp)
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
