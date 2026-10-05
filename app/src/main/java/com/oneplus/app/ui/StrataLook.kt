package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlin.math.max

/*
 * STRATA (طبقات): the fourth look. Orbit makes depth out of SPACE (things turn around you); Strata makes it out of LAYERS.
 *   · Home: a full-bleed hero, then every section is a sheet that rises over the one before it. A sheet that gets covered holds its
 *     place at the top of the screen, shrinks and darkens, so the page reads as a deck being dealt rather than a long list.
 *   · Header: a top sheet with rounded lower corners (search opens inside it); it slides away while you read, back on the first scroll up.
 *   · Nav: a capsule dock. The chosen tab widens to carry its name; the others stay icons.
 *   · Everywhere else: opaque sheets lifted off the page by a soft shadow (see glass()), light on a darker ground in both modes,
 *     and pages arrive by rising from below (see OnePlusApp).
 */

/** How far a sheet's top edge overlaps what is above it (so its rounded corners always show something behind them). */
private val StrataLip = 28.dp

/** The tone of a sheet: between the page and the cards that sit on it. */
private fun sheetColor(c: OneColors) = lerp(c.bg, c.glass, 0.6f)

// ---- Header ----------------------------------------------------------------------------------------------------------------

@Composable
fun StrataHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val s = rememberSearch(a.hasSearch, false, a.onQuery) {}
    val morph = s.morph
    val inset = topInset()
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    val fill = remember(c) { sheetColor(c) }
    Box(
        modifier.fillMaxWidth().height(inset + 60.dp).graphicsLayer {
            translationY = -(1f - max(a.reveal(), morph)) * (size.height + 14.dp.toPx())
        }
    ) {
        Box(Modifier.matchParentSize().drawBehind { softShadow(CornerRadius(28.dp.toPx()), (1.2f + a.collapse()) * max(a.reveal(), morph)) })
        Box(Modifier.matchParentSize().clip(shape).background(fill).border(0.5.dp, c.border, shape))
        Box(Modifier.padding(top = inset).fillMaxSize().padding(horizontal = 16.dp)) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morph },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.onBack?.let { back ->
                        Box(Modifier.size(40.dp).press { if (a.reveal() > 0.6f) back() }.glass(3, 20.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                    }
                    Crossfade(a.context ?: a.title, animationSpec = tween(220), label = "title") { t -> OneText(t, OneType.Title, c.text, maxLines = 1) }
                }
                if (a.hasSearch) Box(Modifier.size(40.dp).press { if (a.reveal() > 0.6f) s.show() }.glass(3, 20.dp), Alignment.Center) {
                    OneIconView(OneIcon.Search) { c.text }
                }
            }
            if (s.open || morph > 0f) SearchRow(a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().graphicsLayer { alpha = morph })
        }
    }
}

// ---- Nav -------------------------------------------------------------------------------------------------------------------

private val StrataTabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)

/** A capsule dock. The chosen tab widens and carries its name in the accent; the other two are bare icons. */
@Composable
fun StrataNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(
        modifier.navigationBarsPadding().padding(bottom = 14.dp).height(60.dp).glass(4, 30.dp).padding(6.dp),
        Arrangement.spacedBy(2.dp), Alignment.CenterVertically,
    ) {
        StrataTabs.forEachIndexed { i, (icon, label) ->
            val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.78f, 380f), label = "strataTab")
            Row(
                Modifier.fillMaxHeight().width(48.dp + 84.dp * on).press { onSelect(i) }.clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.18f * on)),
                Arrangement.Center, Alignment.CenterVertically,
            ) {
                OneIconView(icon) { lerp(c.dim, c.accent, on) }
                if (on > 0.01f) OneText(stringResource(label), OneType.Body, c.accent, Modifier.padding(start = 6.dp).graphicsLayer { alpha = on }, 1)
            }
        }
    }
}

// ---- Scroll physics --------------------------------------------------------------------------------------------------------

private class Pose(val up: Float, val cover: Float)

/**
 * Where an item of the Strata column stands: [Pose.up] is how far it is held back at the top of the screen (an item never scrolls
 * past it; the next one climbs over it instead), [Pose.cover] is 0..1 how much of it the next item has covered. Layout info only,
 * so call it from draw / layer blocks.
 */
private fun Density.pose(s: LazyListState, key: Any): Pose? {
    val items = s.layoutInfo.visibleItemsInfo
    val i = items.indexOfFirst { it.key == key }
    if (i < 0) return null
    val me = items[i]
    val top = max(me.offset.toFloat(), 0f)
    val next = items.getOrNull(i + 1) ?: return Pose(top - me.offset, 0f)
    val covered = (top + me.size - max(next.offset.toFloat(), 0f)) / me.size.coerceAtLeast(1)
    return Pose(top - me.offset, covered.coerceIn(0f, 1f))
}

/** Holds the item at the top while the next one rises over it; as it is covered it steps back (smaller, from its top edge). */
private fun Modifier.strataStack(s: LazyListState, key: Any): Modifier = graphicsLayer {
    val p = pose(s, key) ?: return@graphicsLayer
    translationY = p.up
    val k = 1f - 0.07f * p.cover
    scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0.5f, 0f)
}

/** …and darker, as if the sheet above were shading it. Put it INSIDE the clip so it follows the rounded corners. */
private fun Modifier.strataShade(s: LazyListState, key: Any): Modifier = drawWithContent {
    drawContent()
    pose(s, key)?.let { drawRect(Color.Black, alpha = 0.5f * it.cover) }
}

/** The item reports [StrataLip] less height than it draws, so the next item starts that far up inside it. */
private fun Modifier.strataLap(): Modifier = layout { m, cs ->
    val p = m.measure(cs)
    layout(p.width, (p.height - StrataLip.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
}

// ---- Home ------------------------------------------------------------------------------------------------------------------

@Composable
fun StrataHome(a: HomeArgs) {
    val d = a.state.data
    val c = LocalColors.current
    val k = LocalCardScale.current
    val byId = d.movies.associateBy { it.id }
    val resume = a.lib.progress.keys.mapNotNull { byId[it] }
    val heroes = remember(d.movies, resume) { (resume.take(2) + d.movies.sortedByDescending { it.rating }).distinct().take(5) }
    val foot = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(Modifier.fillMaxSize(), a.list) {
        item(key = "hero") {
            if (heroes.isEmpty()) Spacer(Modifier.height(toolbarInset()))
            else StrataHero(
                heroes, a,
                Modifier.fillMaxWidth().strataStack(a.list, "hero").strataLap()
                    .fillParentMaxHeight(if (a.portrait) 0.8f else 0.92f).strataShade(a.list, "hero"),
            )
        }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            StrataSheet("matches", a, R.string.sec_matches, null) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(236.dp * k).edgeFx(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            StrataSheet("resume", a, R.string.sec_resume, null) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(176.dp * k).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            StrataSheet("movies", a, R.string.sec_movies, a.onAllMovies) { row ->
                items(d.movies.take(10), key = { it.id }) { m -> LuxPoster(m, Modifier.width(138.dp * k).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            StrataSheet("channels", a, R.string.sec_channels, a.onAllChannels) { _ ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
        item(key = "foot") { Box(Modifier.fillMaxWidth().height(foot + StrataLip).background(sheetColor(c))) } // the last sheet runs on under the dock
    }
}

/** One section: an opaque sheet with a grabber, an accent-barred title and one sideways row. */
@Composable
private fun StrataSheet(key: String, a: HomeArgs, @StringRes title: Int, onAll: (() -> Unit)?, row: LazyListScope.(LazyListState) -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val fill = remember(c) { sheetColor(c) }
    val state = rememberLazyListState()
    Column(
        Modifier.fillMaxWidth().strataStack(a.list, key).strataLap()
            .pointerInput(Unit) { detectTapGestures { } } // a sheet is solid: a tap on its empty part must not reach the hero under it
            .drawBehind { softShadow(CornerRadius(30.dp.toPx()), 2.2f) }
            .clip(shape).background(fill).border(0.5.dp, c.border, shape).strataShade(a.list, key)
            .padding(top = 10.dp, bottom = StrataLip + 14.dp),
        Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(c.dim.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 20.dp).clip(CircleShape).background(c.accent))
                OneText(stringResource(title), OneType.Title.copy(fontSize = 20.sp), c.text, maxLines = 1)
            }
            if (onAll != null) Row(
                Modifier.press(onAll).clip(CircleShape).background(c.accentSoft).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                Arrangement.spacedBy(2.dp), Alignment.CenterVertically,
            ) {
                OneText(stringResource(R.string.sec_all), OneType.Caption, c.accent, maxLines = 1)
                OneIconView(OneIcon.Next, Modifier.size(16.dp)) { c.accent }
            }
        }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { this.row(state) }
    }
}

/** The featured titles, full-bleed. The artwork drifts slower than the page and the words faster; dots show where you are. */
@Composable
private fun StrataHero(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    Box(modifier) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { movies[it].id }) { i ->
            val m = movies[i]
            StrataPage(m, { (i - pager.currentPage) - pager.currentPageOffsetFraction }, m.id in a.lib.progress) { a.onMovie(m.id) }
        }
        if (n > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), Arrangement.spacedBy(6.dp)) {
            repeat(n) { j ->
                val on by animateFloatAsState(if (j == pager.currentPage) 1f else 0f, tween(240), label = "dot")
                Box(Modifier.size(6.dp + 16.dp * on, 4.dp).clip(CircleShape).background(lerp(Color.White.copy(alpha = 0.4f), c.accent, on)))
            }
        }
    }
}

/** [off]: pages away from the focused one (0 = in front, ±1 = beside it). */
@Composable
private fun StrataPage(m: Movie, off: () -> Float, resuming: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.6f), c.bg)) }
    val shade = remember {
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.3f to Color.Black.copy(alpha = 0f), 0.5f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.92f))
    }
    Box(Modifier.fillMaxSize().press(onClick).clipToBounds().background(fill)) {
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer {
            translationX = -off() * size.width * 0.25f * dir; scaleX = 1.4f; scaleY = 1.4f // the scale keeps the edges covered while it drifts
        }) else OneText(m.title.take(1), OneType.Display.copy(fontSize = 220.sp, lineHeight = 240.sp), Color.White.copy(alpha = 0.08f), Modifier.align(Alignment.Center))
        Box(Modifier.matchParentSize().background(shade))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 76.dp)
                .graphicsLayer { val o = off(); translationX = o * size.width * 0.3f * dir; alpha = (1f - abs(o) * 1.5f).coerceIn(0f, 1f) },
            Arrangement.spacedBy(8.dp),
        ) {
            OneText(stringResource(R.string.lux_featured), OneType.Caption, c.accent)
            OneText(m.title, OneType.Display.copy(fontSize = 36.sp, lineHeight = 42.sp), Color.White, maxLines = 2)
            OneText("${m.year}  ·  ${m.genres.joinToString(" · ")}", OneType.Caption, Color.White.copy(alpha = 0.72f), maxLines = 1)
            Row(
                Modifier.padding(top = 8.dp).clip(CircleShape).background(c.accent).padding(horizontal = 20.dp, vertical = 11.dp),
                Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
            ) {
                OneIconView(OneIcon.Play, Modifier.size(18.dp)) { c.onAccent }
                OneText(stringResource(if (resuming) R.string.movie_resume else R.string.movie_play), OneType.Body, c.onAccent, maxLines = 1)
            }
        }
    }
}
