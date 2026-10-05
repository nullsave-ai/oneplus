package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlin.math.max

/*
 * AURORA (شفق): the fourth look. Where Orbit is a deep-space SKY you look up at, Aurora is a LIGHT you walk through.
 *   · Sky: slow curtains of green / magenta / app-colour light drift behind every page (see ambient() in OneKit).
 *   · Header: one centred crystal capsule with a spinning portal ring. It narrows while the page scrolls, vanishes on the way down,
 *     returns on the way up; search widens it to full width.
 *   · Nav: a dock where the chosen tab opens a portal ring and shoots a beam of light up the screen. Every label stays visible.
 *   · Home: featured titles are arched GATES on a rotating cube (swipe = the cube turns). Sections are rows of smaller gates, and
 *     the rows DRIFT sideways in alternating directions while the page scrolls, like wind through curtains.
 *   · Pages arrive by "rise": the new page lifts out of the light while the old one sinks away.
 */

private val Rim = Brush.linearGradient(listOf(AuroraMint.copy(alpha = 0.9f), AuroraMint.copy(alpha = 0f), AuroraPink.copy(alpha = 0.7f)))
private val Ring = Brush.sweepGradient(listOf(AuroraMint, AuroraPink, AuroraMint))
private val AuroraTabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)

/** A gate: a rectangle whose top is a half circle. */
private fun arch(w: Dp, r: Dp = 22.dp) = RoundedCornerShape(w / 2, w / 2, r, r)

private fun DrawScope.portalRing(turn: Float, accent: Color, on: Float) {
    if (on < 0.01f) return
    val r = size.minDimension / 2f
    drawCircle(accent.copy(alpha = 0.22f * on), r * 1.25f)
    rotate(turn) { drawCircle(Ring, r - 1.5.dp.toPx(), alpha = on, style = Stroke(2.dp.toPx())) }
}

/** The beam a chosen tab shoots up: narrow at the dock, wide and faded at the top. */
private fun DrawScope.beam(on: Float, accent: Color) {
    if (on < 0.01f) return
    val dock = size.height - 66.dp.toPx(); val mid = size.width / 2f
    val p = Path().apply {
        moveTo(mid - 18.dp.toPx(), dock); lineTo(mid + 18.dp.toPx(), dock); lineTo(mid + 42.dp.toPx(), 0f); lineTo(mid - 42.dp.toPx(), 0f); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.34f * on)), 0f, dock))
}

// ---- Header ----------------------------------------------------------------------------------------------------------------

@Composable
fun AuroraHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val s = rememberSearch(a.hasSearch, false, a.onQuery) {}
    val morph = s.morph
    val inset = topInset()
    val turn = rememberInfiniteTransition(label = "gem").animateFloat(0f, 360f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "g")
    val scrim = remember(c) { Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0.85f), c.bg.copy(alpha = 0f))) }
    Box(modifier.fillMaxWidth()) {
        // page-colour fade under the clock: nothing at the top of a page, there from the first scroll on
        Box(Modifier.fillMaxWidth().height(inset + 84.dp).graphicsLayer {
            translationY = -(1f - max(a.reveal(), morph)) * 76.dp.toPx(); alpha = max(a.collapse(), morph)
        }.background(scrim))
        Box(
            Modifier.padding(top = inset + 10.dp).fillMaxWidth()
                .layout { m, cs -> // the capsule narrows with the scroll and spans the screen while searching
                    val base = 0.9f - 0.32f * a.collapse().coerceIn(0f, 1f)
                    val w = ((cs.maxWidth - 32.dp.roundToPx()) * (base + (1f - base) * morph)).toInt()
                    val h = 48.dp.roundToPx()
                    val pl = m.measure(Constraints.fixed(w, h))
                    layout(cs.maxWidth, h) { pl.place((cs.maxWidth - w) / 2, 0) }
                }
                .graphicsLayer {
                    val r = max(a.reveal(), morph)
                    translationY = -(1f - r) * (inset.toPx() + 70.dp.toPx()); alpha = r
                }
                .glass(3, 24.dp)
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp).graphicsLayer { alpha = 1f - morph },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                val back = a.onBack
                Box(
                    Modifier.size(40.dp).then(if (back != null) Modifier.press { if (a.reveal() > 0.6f) back() } else Modifier)
                        .drawBehind { if (back == null) portalRing(turn.value, c.accent, 1f) },
                    Alignment.Center,
                ) { if (back != null) OneIconView(OneIcon.Back) { c.text } }
                Crossfade(a.context ?: a.title, Modifier.weight(1f), animationSpec = tween(220), label = "title") { t ->
                    OneText(
                        t, OneType.Section.copy(shadow = Shadow(c.accent.copy(alpha = 0.6f), blurRadius = 18f), textAlign = TextAlign.Center),
                        c.text, Modifier.fillMaxWidth(), 1,
                    )
                }
                if (a.hasSearch) Box(Modifier.size(40.dp).press { if (a.reveal() > 0.6f) s.show() }, Alignment.Center) { OneIconView(OneIcon.Search) { c.text } }
                else Spacer(Modifier.size(40.dp))
            }
            if (s.open || morph > 0f) SearchRow(
                a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp).graphicsLayer { alpha = morph },
            )
        }
    }
}

// ---- Nav -------------------------------------------------------------------------------------------------------------------

@Composable
fun AuroraNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val turn = rememberInfiniteTransition(label = "dock").animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "a")
    Box(modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).widthIn(max = 400.dp).fillMaxWidth().height(110.dp)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(66.dp).glass(3, 22.dp))
        Row(Modifier.fillMaxSize()) {
            AuroraTabs.forEachIndexed { i, (icon, label) ->
                val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.7f, 300f), label = "tab")
                Column(
                    Modifier.weight(1f).fillMaxHeight().press { onSelect(i) }.drawBehind { beam(on, c.accent) },
                    Arrangement.Bottom, Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(42.dp).drawBehind { portalRing(turn.value, c.accent, on) }, Alignment.Center) { OneIconView(icon) { lerp(c.dim, c.text, on) } }
                    OneText(stringResource(label), OneType.Caption, lerp(c.dim, c.text, on), Modifier.padding(bottom = 7.dp), 1)
                }
            }
        }
    }
}

// ---- Home ------------------------------------------------------------------------------------------------------------------

/** Sections drift sideways while the page scrolls: [sign] alternates, so neighbours move against each other. Zero at mid-screen. */
internal fun Modifier.drift(state: LazyListState, key: Any, sign: Float): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val span = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val f = ((item.offset + item.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f) / span).coerceIn(-1f, 1f)
    translationX = f * 44.dp.toPx() * sign; alpha = 1f - 0.4f * f * f
}

@Composable
fun AuroraHome(a: HomeArgs) {
    val d = a.state.data
    val seen = remember { mutableSetOf<Int>() }
    val byId = d.movies.associateBy { it.id }
    val resume = a.lib.progress.keys.mapNotNull { byId[it] }
    val heroes = remember(d.movies, resume) { (resume.take(2) + d.movies.sortedByDescending { it.rating }).distinct().take(5) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), a.list, PaddingValues(top = toolbarInset(), bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        if (heroes.isNotEmpty()) item(key = "hero") { Cube(heroes, a, Modifier.reveal(0, seen)) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Realm(R.string.sec_matches, null, Modifier.reveal(1, seen).drift(a.list, "matches", 1f)) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(236.dp).edgeFx(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Realm(R.string.sec_resume, null, Modifier.reveal(2, seen).drift(a.list, "resume", -1f)) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(200.dp).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            Realm(R.string.sec_movies, a.onAllMovies, Modifier.reveal(3, seen).drift(a.list, "movies", 1f)) { row ->
                items(d.movies.take(10), key = { it.id }) { m -> Gate(m, Modifier.edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Realm(R.string.sec_channels, a.onAllChannels, Modifier.reveal(4, seen).drift(a.list, "channels", -1f)) { row ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
    }
}

/** A section: a diamond, the title, a light line, and one sideways row. */
@Composable
private fun Realm(@StringRes title: Int, onAll: (() -> Unit)?, modifier: Modifier, row: LazyListScope.(LazyListState) -> Unit) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    val line = remember { Brush.horizontalGradient(listOf(AuroraMint.copy(alpha = 0f), AuroraMint.copy(alpha = 0.5f), AuroraPink.copy(alpha = 0.5f), AuroraPink.copy(alpha = 0f))) }
    Column(modifier, Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).graphicsLayer { rotationZ = 45f }.background(Ring))
            OneText(stringResource(title), OneType.Section.copy(shadow = Shadow(AuroraMint.copy(alpha = 0.45f), blurRadius = 16f)), c.text)
            Box(Modifier.weight(1f).height(1.dp).background(line))
            if (onAll != null) Box(Modifier.size(34.dp).press(onAll).glass(3, 17.dp), Alignment.Center) { OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text } }
        }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { this.row(state) }
    }
}

/** A small gate: the artwork (or the first letter) inside an arch with a lit rim, the title under it. */
@Composable
private fun Gate(m: Movie, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = remember { arch(132.dp) }
    val fill = remember(c) { Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.5f), c.glass)) }
    Column(modifier.width(132.dp).press(onClick), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp, 192.dp).clip(shape).background(fill).border(1.dp, Rim, shape), Alignment.Center) {
            OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 54.sp), c.text.copy(alpha = 0.2f))
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
        }
        OneText(m.title, OneType.Caption, c.text, maxLines = 1)
    }
}

/**
 * The featured titles as gates on a cube: neighbours are turned around their shared edge (swipe and the cube rotates), the artwork
 * slides across the glass, and a light of the sky's colours glows behind. Each gate carries its own title and play button.
 */
@Composable
private fun Cube(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val w = minOf(screen - 72.dp, 340.dp)
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val shape = remember(w) { arch(w, 28.dp) }
    val fill = remember(c) { Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.55f), c.glass)) }
    val shade = remember { Brush.verticalGradient(0.45f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.86f)) }
    Column(
        modifier.drawBehind {
            val r = minOf(size.width, size.height) * 0.6f
            drawCircle(Brush.radialGradient(listOf(AuroraMint.copy(alpha = 0.22f), AuroraMint.copy(alpha = 0f)), Offset(size.width * 0.25f, size.height * 0.35f), r), r, Offset(size.width * 0.25f, size.height * 0.35f))
            drawCircle(Brush.radialGradient(listOf(AuroraPink.copy(alpha = 0.18f), AuroraPink.copy(alpha = 0f)), Offset(size.width * 0.8f, size.height * 0.65f), r), r, Offset(size.width * 0.8f, size.height * 0.65f))
        },
        Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = (screen - w) / 2), pageSize = PageSize.Fixed(w),
            key = { movies[it].id },
        ) { i ->
            val m = movies[i]
            val at = { ((i - pager.currentPage) - pager.currentPageOffsetFraction) * dir } // physical: > 0 = to the right of the middle
            val progress = a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }
            Box(
                Modifier.fillMaxWidth().height(w * 1.3f).graphicsLayer {
                    val p = at().coerceIn(-1f, 1f)
                    transformOrigin = TransformOrigin(if (p > 0f) 0f else 1f, 0.5f) // turn around the edge it shares with the middle page
                    rotationY = -p * 68f; cameraDistance = 14f * density; alpha = 1f - 0.4f * abs(p)
                }.press { a.onMovie(m.id) }.clip(shape).background(fill).border(1.5.dp, Rim, shape),
                Alignment.BottomCenter,
            ) {
                OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 150.sp, lineHeight = 160.sp), Color.White.copy(alpha = 0.14f), Modifier.align(Alignment.Center).padding(bottom = 90.dp))
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer {
                    translationX = -at() * size.width * 0.2f * dir; scaleX = 1.4f; scaleY = 1.4f // the artwork slides behind the glass
                })
                Box(Modifier.matchParentSize().background(shade))
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
                    OneText(stringResource(R.string.lux_featured), OneType.Caption, AuroraMint)
                    OneText(m.title, OneType.Title.copy(shadow = Shadow(AuroraMint.copy(alpha = 0.6f), blurRadius = 24f), textAlign = TextAlign.Center), Color.White, Modifier.fillMaxWidth(), 2)
                    OneText("${m.year}  ·  ${m.genres.take(2).joinToString(" · ")}", OneType.Caption, Color.White.copy(alpha = 0.7f), maxLines = 1)
                    Row(
                        Modifier.padding(top = 6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(AuroraMint, AuroraPink))).padding(horizontal = 22.dp, vertical = 10.dp),
                        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                    ) {
                        OneIconView(OneIcon.Play, Modifier.size(18.dp)) { Color.Black }
                        OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body, Color.Black, maxLines = 1)
                    }
                }
            }
        }
        if (n > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(n) { j ->
                val on = j == pager.currentPage
                val dw by animateDpAsState(if (on) 26.dp else 8.dp, spring(0.7f, 500f), label = "seg")
                Box(Modifier.size(dw, 4.dp).clip(CircleShape).background(if (on) AuroraMint else c.dim.copy(alpha = 0.4f)))
            }
        }
    }
}
