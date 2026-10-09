package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val Violet = Color(0xFFB36BFF)

@Composable
fun OrbitHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val s = rememberSearch(a.hasSearch, a.onQuery)
    val morph = s.morph
    val inset = topInset()
    val pulse = rememberInfiniteTransition(label = "beacon").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "p")
    val scrim = remember(c) { Brush.verticalGradient(listOf(c.bg, c.bg.copy(alpha = 0.92f), c.bg.copy(alpha = 0f))) }
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(inset + 84.dp).graphicsLayer {
                translationY = -(1f - max(a.reveal(), morph)) * 76.dp.toPx(); alpha = max(a.collapse(), morph)
            }.background(scrim)
        )
        Box(
            Modifier.padding(top = inset + 10.dp).padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).graphicsLayer {
                val r = max(a.reveal(), morph)
                translationY = -(1f - r) * (inset.toPx() + 62.dp.toPx()); alpha = r
            }
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morph },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.onBack?.let { back ->
                        Box(Modifier.size(44.dp).press { if (a.reveal() > 0.6f) back() }.glass(3, 22.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                    }
                    Box(Modifier.size(12.dp).drawBehind {
                        val p = pulse.value
                        drawCircle(c.accent.copy(alpha = 0.35f * (1f - p)), size.minDimension / 2f * (1f + 2.2f * p))
                        drawCircle(c.accent, size.minDimension / 2f)
                    })
                    Crossfade(a.context ?: a.title, animationSpec = tween(220), label = "title") { t ->
                        OneText(t, OneType.Title.copy(shadow = Shadow(c.accent.copy(alpha = 0.6f), blurRadius = 22f)), c.text, Modifier.graphicsLayer {
                            val k = androidx.compose.ui.util.lerp(1f, 0.82f, a.collapse()); scaleX = k; scaleY = k
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        }, 1)
                    }
                }
                if (a.hasSearch) Box(Modifier.size(44.dp).press { if (a.reveal() > 0.6f) s.show() }.glass(3, 22.dp), Alignment.Center) {
                    OneIconView(OneIcon.Search) { c.text }
                }
            }
            if (s.open || morph > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = morph }.glass(3, 26.dp)) {
                SearchRow(a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp))
            }
        }
    }
}

private val OrbTabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)

@Composable
fun OrbitNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val spin = rememberInfiniteTransition(label = "dock").animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "a")
    Box(modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).widthIn(max = 380.dp).fillMaxWidth().height(88.dp)) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(62.dp).glass(3, 31.dp))
        Row(Modifier.fillMaxSize()) {
            OrbTabs.forEachIndexed { i, (icon, label) ->
                val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.55f, 380f), label = "orbTab")
                Column(Modifier.weight(1f).fillMaxHeight().tvTab(i).press { onSelect(i) }, Arrangement.Bottom, Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp)
                            .graphicsLayer { translationY = -18.dp.toPx() * on; val k = 1f + 0.2f * on; scaleX = k; scaleY = k }
                            .drawBehind { orb(on, spin.value, c.accent) },
                        Alignment.Center,
                    ) { OneIconView(icon) { lerp(c.dim, Color.White, on) } }
                    OneText(stringResource(label), OneType.Caption, lerp(c.dim, c.text, on), Modifier.padding(bottom = 9.dp), 1)
                }
            }
        }
    }
}

private fun DrawScope.orb(on: Float, spin: Float, accent: Color) {
    if (on < 0.01f) return
    val r = size.minDimension / 2f
    drawCircle(accent.copy(alpha = 0.30f * on), r * 1.9f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.9f), 0.3f to accent, 1f to lerp(accent, Color.Black, 0.6f),
            center = Offset(size.width * 0.35f, size.height * 0.3f), radius = r * 1.5f,
        ),
        r,
    )
    rotate(-20f, center) {
        val rx = r * 1.45f; val ry = r * 0.34f
        drawOval(Color.White.copy(alpha = 0.5f * on), Offset(center.x - rx, center.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(1.dp.toPx()))
        val t = Math.toRadians(spin.toDouble())
        drawCircle(Color.White.copy(alpha = on), 2.2.dp.toPx(), Offset(center.x + rx * cos(t).toFloat(), center.y + ry * sin(t).toFloat()))
    }
}

@Composable
internal fun Modifier.coverflow(state: LazyListState, key: Any): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val f = ((item.offset + item.size / 2f - mid) / item.size).coerceIn(-2f, 2f) * (if (rtl) -1f else 1f)
        val a = abs(f).coerceAtMost(1.5f)
        rotationY = -f * 30f
        val k = 1f - 0.12f * a
        scaleX = k; scaleY = k; alpha = 1f - 0.3f * a
        cameraDistance = 14f * density
    }
}

internal fun Modifier.drum(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val span = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val f = ((item.offset + item.size / 2f - (info.viewportStartOffset + info.viewportEndOffset) / 2f) / span).coerceIn(-1f, 1f)
    rotationX = f * 14f
    val k = 1f - 0.05f * abs(f)
    scaleX = k; scaleY = k; alpha = 1f - 0.3f * f * f
    cameraDistance = 20f * density
}

@Composable
fun OrbitHome(a: HomeArgs) {
    val d = a.state.data
    val seen = remember { mutableSetOf<Int>() }
    val view = a.state.view
    val resume = remember(view, a.lib.progress) { a.lib.progress.keys.mapNotNull { view.byId[it] } }
    val heroes = remember(view, resume) { (resume.take(2) + view.topRated).distinct().take(5) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), a.list, PaddingValues(top = toolbarInset(), bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (heroes.isNotEmpty()) item(key = "hero") { PlanetPager(heroes, a, Modifier.reveal(0, seen).drum(a.list, "hero")) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Constellation(R.string.sec_matches, null, 220.dp, Modifier.reveal(1, seen).drum(a.list, "matches")) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(220.dp).coverflow(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Constellation(R.string.sec_resume, null, 200.dp, Modifier.reveal(2, seen).drum(a.list, "resume")) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(200.dp).coverflow(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        Kind.entries.forEach { k ->
            val shelf = view.shelves[k].orEmpty()
            if (shelf.isNotEmpty()) item(key = k.name) {
                Constellation(k.title, { a.onAll(k) }, 150.dp, Modifier.reveal(3 + k.ordinal, seen).drum(a.list, k.name)) { row ->
                    items(shelf.take(10), key = { it.id }) { m -> TitledPoster(m, Modifier.width(150.dp).coverflow(row, m.id)) { a.onMovie(m.id) } }
                }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Constellation(R.string.sec_channels, a.onAllChannels, null, Modifier.reveal(6, seen).drum(a.list, "channels")) { _ ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Constellation(
    @StringRes title: Int, onAll: (() -> Unit)?, itemWidth: androidx.compose.ui.unit.Dp?, modifier: Modifier,
    row: LazyListScope.(LazyListState) -> Unit,
) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    val snap: FlingBehavior = if (itemWidth != null) rememberSnapFlingBehavior(state, SnapPosition.Center) else ScrollableDefaults.flingBehavior()
    val pad = if (itemWidth != null) ((screenWidth() - itemWidth) / 2).coerceAtLeast(20.dp) else 20.dp
    Column(modifier, Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).drawBehind {
                drawCircle(c.accent.copy(alpha = 0.3f), size.minDimension * 1.2f); drawCircle(c.accent, size.minDimension / 2f)
            })
            OneText(stringResource(title), OneType.Section.copy(shadow = Shadow(c.accent.copy(alpha = 0.5f), blurRadius = 16f)), c.text)
            Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.5f), c.accent.copy(alpha = 0f)))))
            if (onAll != null) Box(Modifier.size(34.dp).press(onAll).glass(3, 17.dp), Alignment.Center) { OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text } }
        }
        LazyRow(
            state = state, flingBehavior = snap, contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(if (itemWidth != null) 6.dp else 12.dp),
        ) { this.row(state) }
    }
}

@Composable
private fun PlanetPager(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    val spin = rememberInfiniteTransition(label = "moon").animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "a")
    val side = ((screenWidth() - 250.dp) / 2).coerceAtLeast(0.dp)
    val cur = movies[pager.currentPage.coerceIn(0, n - 1)]
    Column(modifier, Arrangement.spacedBy(4.dp), Alignment.CenterHorizontally) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = side), pageSize = PageSize.Fixed(250.dp),
            key = { movies[it].id },
        ) { i -> Planet(movies[i], { (i - pager.currentPage) - pager.currentPageOffsetFraction }, spin) { a.onMovie(movies[i].id) } }
        AnimatedContent(
            cur, Modifier.fillMaxWidth(),
            transitionSpec = { (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 5 }) togetherWith fadeOut(tween(160)) },
            label = "info",
        ) { m ->
            val progress = a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
                OneText(stringResource(R.string.featured), OneType.Caption, c.accent)
                OneText(m.title, OneType.Display.copy(shadow = Shadow(c.accent.copy(alpha = 0.55f), blurRadius = 26f)), c.text, maxLines = 1)
                OneText("${m.year}  ·  ${m.genres.joinToString(" · ")}", OneType.Caption, c.dim, maxLines = 1)
                Row(
                    Modifier.padding(top = 6.dp).press { a.onMovie(m.id) }.clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(c.accent, lerp(c.accent, Violet, 0.6f)))).padding(horizontal = 22.dp, vertical = 11.dp),
                    Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                ) {
                    OneIconView(OneIcon.Play, Modifier.size(18.dp)) { c.onAccent }
                    OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body, c.onAccent, maxLines = 1)
                }
            }
        }
        if (n > 1) Row(Modifier.padding(top = 6.dp), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
            repeat(n) { j ->
                val w by animateDpAsState(if (j == pager.currentPage) 22.dp else 6.dp, spring(0.7f, 500f), label = "dot")
                Box(Modifier.size(w, 6.dp).clip(CircleShape).background(if (j == pager.currentPage) c.accent else c.dim.copy(alpha = 0.4f)))
            }
        }
    }
}

@Composable
private fun Planet(m: Movie, off: () -> Float, spin: State<Float>, onClick: () -> Unit) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val body = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.75f), c.glass)) }
    val limb = remember { Brush.radialGradient(0.45f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.72f)) }
    val rim = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.9f), c.accent.copy(alpha = 0.05f), Violet.copy(alpha = 0.6f))) }
    Box(
        Modifier.size(250.dp).graphicsLayer {
            val o = off(); val ao = abs(o).coerceAtMost(1f)
            val k = 1f - 0.34f * ao
            scaleX = k; scaleY = k; alpha = 1f - 0.5f * ao
            translationY = ao * 26.dp.toPx(); rotationY = -o * dir * 38f; cameraDistance = 14f * density
        }.press(onClick),
        Alignment.Center,
    ) {
        Canvas2(spin, back = true)
        Box(Modifier.size(188.dp).drawBehind {
            drawCircle(c.accent.copy(alpha = 0.28f), size.minDimension * 0.72f); drawCircle(c.accent.copy(alpha = 0.14f), size.minDimension * 0.95f)
        }) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(body)) {
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer {
                    translationX = -off() * size.width * 0.22f * dir; scaleX = 1.5f; scaleY = 1.5f
                }) else OneText(
                    m.title.take(1), OneType.SerifHero.copy(fontSize = 120.sp, lineHeight = 130.sp), Color.White.copy(alpha = 0.16f),
                    Modifier.align(Alignment.Center).graphicsLayer { translationX = -off() * 90.dp.toPx() * dir },
                )
                Box(Modifier.matchParentSize().background(limb))
            }
            Box(Modifier.matchParentSize().border(1.dp, rim, CircleShape))
        }
        Canvas2(spin, back = false)
    }
}

@Composable
private fun Canvas2(spin: State<Float>, back: Boolean) {
    val c = LocalColors.current
    androidx.compose.foundation.Canvas(Modifier.size(250.dp)) {
        val rx = size.width * 0.49f; val ry = size.height * 0.135f
        val tl = Offset(center.x - rx, center.y - ry); val sz = Size(rx * 2f, ry * 2f)
        rotate(-16f, center) {
            val t = Math.toRadians(spin.value.toDouble())
            val moon = Offset(center.x + rx * cos(t).toFloat(), center.y + ry * sin(t).toFloat())
            if (back) drawOval(c.accent.copy(alpha = 0.30f), tl, sz, style = Stroke(1.2.dp.toPx()))
            else drawArc(c.accent.copy(alpha = 0.85f), 0f, 180f, false, tl, sz, style = Stroke(1.6.dp.toPx()))
            if ((sin(t) > 0) != back) drawCircle(Color.White, 3.dp.toPx(), moon)
        }
    }
}
