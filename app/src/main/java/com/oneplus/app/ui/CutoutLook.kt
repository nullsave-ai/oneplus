package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * CUTOUT (قصاصات): the fourth look, and Orbit's opposite on every axis.
 *   Orbit is dark, round, glowing, deep and floating   ->  Cutout is flat, square, inked, shallow and grounded.
 *   · Surfaces: paper slabs. A 2dp ink outline and a HARD shadow in the app colour (see slab() / glass() in OneKit.kt); a press pushes the
 *     slab down into its own shadow. The page is a dotted cutting mat.
 *   · Header: the title is a tilted sticker; it straightens and shrinks as the page moves, then the bar slides away (back on scroll up).
 *   · Nav: three folder tabs. The chosen one is taller than its neighbours and filled with the app colour; every label stays visible.
 *   · Home: the featured titles are a fanned DECK of cards (swipe: the cards pivot from their bottom edge). Every section hangs on a WIRE:
 *     cards are pegged to it and swing like a fan the further they are from the middle. Sections slip behind the next one as they leave.
 *   · Pages arrive as sheets: the new page slides up over the old one.
 */

// ---- Header ----------------------------------------------------------------------------------------------------------------

@Composable
fun CutoutHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val s = rememberSearch(a.hasSearch, false, a.onQuery) {}
    val morph = s.morph
    val inset = topInset()
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.padding(top = inset).fillMaxWidth().height(64.dp)
                .graphicsLayer { translationY = -(1f - max(a.reveal(), morph)) * (size.height + inset.toPx()) }
                .drawBehind { // paper from the first scroll on, with a ruled edge
                    val e = max(a.collapse(), morph)
                    drawRect(c.bg.copy(alpha = e))
                    drawRect(c.border.copy(alpha = e), Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
                },
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).graphicsLayer { alpha = 1f - morph },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.onBack?.let { back ->
                        Box(Modifier.size(42.dp).press { if (a.reveal() > 0.6f) back() }.glass(3, 6.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                    }
                    Crossfade(a.context ?: a.title, animationSpec = tween(220), label = "title") { t ->
                        OneText(
                            t, OneType.Title, c.onAccent,
                            Modifier.graphicsLayer { rotationZ = -2f * (1f - a.collapse()); val k = lerp(1f, 0.86f, a.collapse()); scaleX = k; scaleY = k }
                                .slab(c.accent, c.border, 4.dp, 3.dp).padding(horizontal = 12.dp, vertical = 4.dp), 1,
                        )
                    }
                }
                if (a.hasSearch) Box(Modifier.size(42.dp).press { if (a.reveal() > 0.6f) s.show() }.glass(3, 6.dp), Alignment.Center) { OneIconView(OneIcon.Search) { c.text } }
            }
            if (s.open || morph > 0f) Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).graphicsLayer { alpha = morph }.glass(3, 6.dp)) {
                SearchRow(a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(inset).graphicsLayer { alpha = max(a.collapse(), morph) }.background(c.bg)) // behind the clock
    }
}

// ---- Nav -------------------------------------------------------------------------------------------------------------------

private val CutTabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)

/** Folder tabs along the bottom edge: the chosen one stands taller than the others and takes the app colour. */
@Composable
fun CutoutNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp), Arrangement.spacedBy((-2).dp), Alignment.Bottom) {
            CutTabs.forEachIndexed { i, (icon, label) ->
                val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.6f, 420f), label = "cutTab")
                val ink = androidx.compose.ui.graphics.lerp(c.dim, c.onAccent, on)
                Box(
                    Modifier.weight(1f).height(56.dp + 16.dp * on).press { onSelect(i) }
                        .background(androidx.compose.ui.graphics.lerp(c.bg, c.accent, on)).border(2.dp, c.border),
                    Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneIconView(icon, Modifier.size(24.dp)) { ink }
                        OneText(stringResource(label), OneType.Caption.copy(fontWeight = FontWeight.Bold), ink, maxLines = 1)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(c.bg))
    }
}

// ---- Scroll physics --------------------------------------------------------------------------------------------------------

/** A card pegged to a wire: it swings from its peg, leaning outward and sagging the further it is from the middle of its row. Draw phase only. */
@Composable
private fun Modifier.swing(state: LazyListState, key: Any): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
        val span = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
        val f = ((item.offset + item.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f) / span).coerceIn(-1f, 1f) * (if (rtl) -1f else 1f)
        transformOrigin = TransformOrigin(0.5f, 0f)
        rotationZ = -f * 9f
        translationY = f * f * 8.dp.toPx()
    }
}

/** A section leaving through the top slips behind the next one: it shrinks toward its bottom edge and fades. */
private fun Modifier.sink(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val out = (-item.offset).coerceAtLeast(0) / item.size.toFloat().coerceAtLeast(1f)
    val k = 1f - 0.07f * out.coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(0.5f, 1f)
    scaleX = k; scaleY = k; alpha = 1f - 0.5f * out.coerceIn(0f, 1f)
}

// ---- Home ------------------------------------------------------------------------------------------------------------------

@Composable
fun CutoutHome(a: HomeArgs) {
    val d = a.state.data
    val seen = remember { mutableSetOf<Int>() }
    val byId = d.movies.associateBy { it.id }
    val resume = a.lib.progress.keys.mapNotNull { byId[it] }
    val heroes = remember(d.movies, resume) { (resume.take(2) + d.movies.sortedByDescending { it.rating }).distinct().take(5) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), a.list, PaddingValues(top = toolbarInset() + 8.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        if (heroes.isNotEmpty()) item(key = "hero") { Deck(heroes, a, Modifier.reveal(0, seen).sink(a.list, "hero")) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Wire(R.string.sec_matches, null, Modifier.reveal(1, seen).sink(a.list, "matches")) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> Hung(row, m.id, Modifier.width(220.dp)) { MatchCard(m, Modifier.fillMaxWidth()) { a.onMatches(m.id) } } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Wire(R.string.sec_resume, null, Modifier.reveal(2, seen).sink(a.list, "resume")) { row ->
                items(resume, key = { it.id }) { m -> Hung(row, m.id, Modifier.width(230.dp)) { CutCard(m, a.lib.fraction(m.id), true) { a.onMovie(m.id) } } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            Wire(R.string.sec_movies, a.onAllMovies, Modifier.reveal(3, seen).sink(a.list, "movies")) { row ->
                items(d.movies.take(10), key = { it.id }) { m -> Hung(row, m.id, Modifier.width(140.dp)) { CutCard(m, null, false) { a.onMovie(m.id) } } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Wire(R.string.sec_channels, a.onAllChannels, Modifier.reveal(4, seen).sink(a.list, "channels")) { row ->
                items(d.channels.take(12), key = { it.id }) { ch -> Hung(row, ch.id, Modifier) { ChannelTile(ch) { a.onChannel(ch.id) } } }
            }
        }
    }
}

/** A section: its title as a tilted tag on a ruled line, then one row hanging on a wire. */
@Composable
private fun Wire(@StringRes title: Int, onAll: (() -> Unit)?, modifier: Modifier, row: LazyListScope.(LazyListState) -> Unit) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    Column(modifier, Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            OneText(
                stringResource(title), OneType.Section, c.onAccent,
                Modifier.graphicsLayer { rotationZ = -1.5f }.slab(c.accent, c.border, 4.dp, 3.dp).padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Box(Modifier.weight(1f).height(2.dp).background(c.border))
            if (onAll != null) Box(Modifier.size(34.dp).press(onAll).glass(3, 6.dp), Alignment.Center) { OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text } }
        }
        LazyRow(
            Modifier.drawBehind { drawLine(c.border, Offset(0f, 5.dp.toPx()), Offset(size.width, 5.dp.toPx()), 2.dp.toPx()) }, // the wire
            state, contentPadding = PaddingValues(start = 20.dp, end = 28.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { this.row(state) }
    }
}

/** [content] pegged to the wire above it, swinging with the row's scroll. */
@Composable
private fun Hung(row: LazyListState, key: Any, modifier: Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Box(modifier.swing(row, key)) {
        Box(Modifier.padding(top = 9.dp)) { content() }
        Box(Modifier.align(Alignment.TopCenter).size(12.dp, 20.dp).slab(c.accent, c.border, 2.dp, 2.dp))
    }
}

/** A movie as a card (portrait, or wide with a progress bar when [progress] is given), a rating sticker on its corner, the title under it. */
@Composable
private fun CutCard(m: Movie, progress: Float?, wide: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().press(onClick), Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 3f / 2f else 2f / 3f).slab(c.glass, c.accent, 4.dp), Alignment.Center) {
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
            else OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 64.sp), c.text.copy(alpha = 0.25f))
            OneText(
                "★ ${(m.rating * 10).roundToInt() / 10f}", OneType.Caption.copy(fontWeight = FontWeight.Bold), c.onAccent,
                Modifier.align(Alignment.TopStart).padding(6.dp).graphicsLayer { rotationZ = -4f }.slab(c.accent, c.border, 2.dp, 2.dp).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(8.dp).background(c.bg)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(c.accent))
            }
        }
        OneText(m.title, OneType.Body.copy(fontWeight = FontWeight.Bold), c.text, maxLines = 1)
    }
}

/** The featured titles as a fanned deck: swiping pivots the cards from their bottom edge; the one in front is straight and the others lean away. */
@Composable
private fun Deck(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val side = ((LocalConfiguration.current.screenWidthDp.dp - 230.dp) / 2).coerceAtLeast(0.dp)
    val cur = movies[pager.currentPage.coerceIn(0, n - 1)]
    Column(modifier, Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = side), pageSize = PageSize.Fixed(230.dp), key = { movies[it].id },
        ) { i ->
            val m = movies[i]
            Box(
                Modifier.zIndex(-abs(i - pager.currentPage).toFloat()).padding(top = 6.dp, bottom = 12.dp).size(230.dp, 300.dp)
                    .graphicsLayer {
                        val o = (i - pager.currentPage) - pager.currentPageOffsetFraction
                        val ao = abs(o).coerceAtMost(1.5f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationZ = o * dir * 8f
                        val k = 1f - 0.06f * ao
                        scaleX = k; scaleY = k; alpha = 1f - 0.3f * ao
                    }
                    .press { a.onMovie(m.id) }.slab(c.glass, c.accent, 6.dp, 6.dp),
            ) {
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
                else OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 120.sp, lineHeight = 130.sp), c.text.copy(alpha = 0.2f), Modifier.align(Alignment.Center))
            }
        }
        AnimatedContent(
            cur, Modifier.fillMaxWidth(),
            transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }) togetherWith fadeOut(tween(120)) },
            label = "info",
        ) { m ->
            val progress = a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Arrangement.spacedBy(10.dp), Alignment.CenterHorizontally) {
                OneText(
                    m.title, OneType.Display, c.onAccent,
                    Modifier.graphicsLayer { rotationZ = -1.5f }.slab(c.accent, c.border, 4.dp, 4.dp).padding(horizontal = 14.dp, vertical = 4.dp), 1,
                )
                OneText("${m.year}  ·  ${m.genres.joinToString(" · ")}", OneType.Caption, c.dim, maxLines = 1)
                Row(
                    Modifier.padding(top = 4.dp).press { a.onMovie(m.id) }.slab(c.text, c.accent, 4.dp, 4.dp).padding(horizontal = 22.dp, vertical = 11.dp),
                    Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                ) {
                    OneIconView(OneIcon.Play, Modifier.size(18.dp)) { c.bg }
                    OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body.copy(fontWeight = FontWeight.Bold), c.bg, maxLines = 1)
                }
            }
        }
        if (n > 1) Row(Modifier.padding(top = 4.dp), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
            repeat(n) { j ->
                val w by animateDpAsState(if (j == pager.currentPage) 26.dp else 10.dp, spring(0.7f, 500f), label = "dot")
                Box(Modifier.size(w, 8.dp).background(if (j == pager.currentPage) c.accent else c.bg).border(1.5.dp, c.border))
            }
        }
    }
}
