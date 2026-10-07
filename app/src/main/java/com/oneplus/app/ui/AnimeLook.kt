package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF1D1033)

private val Plate: Shape = GenericShape { size, _ ->
    val k = size.height * 0.28f
    moveTo(k, 0f); lineTo(size.width, 0f); lineTo(size.width - k, size.height); lineTo(0f, size.height); close()
}

private fun burst(points: Int, inner: Float): Shape = GenericShape { size, _ ->
    val r = size.minDimension / 2f
    for (i in 0 until points * 2) {
        val rad = if (i % 2 == 0) r else r * inner
        val a = PI * i / points - PI / 2
        val x = size.width / 2f + rad * cos(a).toFloat(); val y = size.height / 2f + rad * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
private val Starburst = burst(12, 0.8f)
private val Spark = burst(4, 0.28f)

private fun Modifier.sticker(shape: Shape, fill: Color, ink: Color, shadow: Color, off: Dp = 3.dp): Modifier =
    drawBehind { translate(off.toPx(), off.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), shadow) } }
        .clip(shape).background(fill).border(2.dp, ink, shape)

@Composable
private fun Inked(text: String, style: TextStyle, fill: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Box(modifier) {
        OneText(text, style.copy(drawStyle = Stroke(width = 9f, join = StrokeJoin.Round)), Ink, maxLines = maxLines)
        OneText(text, style, fill, maxLines = maxLines)
    }
}

private fun DrawScope.rays(color: Color, origin: Offset, count: Int, turn: Float) {
    val r = size.maxDimension * 1.3f
    for (i in 0 until count) {
        val a0 = Math.toRadians(turn + i * 360.0 / count); val a1 = a0 + Math.toRadians(360.0 / count * 0.3)
        drawPath(
            Path().apply {
                moveTo(origin.x, origin.y)
                lineTo(origin.x + r * cos(a0).toFloat(), origin.y + r * sin(a0).toFloat())
                lineTo(origin.x + r * cos(a1).toFloat(), origin.y + r * sin(a1).toFloat())
                close()
            },
            color,
        )
    }
}

@Composable
fun AnimeHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val s = rememberSearch(a.hasSearch, a.onQuery)
    val morph = s.morph
    val inset = topInset()
    val spin = rememberInfiniteTransition(label = "spark").animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "s")
    val shadow = c.accent.copy(alpha = 0.75f)
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(inset).graphicsLayer { alpha = max(a.collapse(), morph) }.background(c.bg))
        Box(
            Modifier.padding(top = inset + 10.dp).padding(horizontal = 14.dp).fillMaxWidth().height(50.dp)
                .graphicsLayer {
                    val r = max(a.reveal(), morph)
                    translationX = -(1f - r) * size.width * 1.1f * dir; rotationZ = -(1f - r) * 6f * dir
                }
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - morph }, Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
            ) {
                val back = a.onBack
                if (back != null) Box(Modifier.size(44.dp).press { if (a.reveal() > 0.6f) back() }.sticker(CircleShape, c.glass, c.border, shadow), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                else Box(Modifier.size(34.dp).graphicsLayer { rotationZ = spin.value }.clip(Spark).background(AnimeYellow))
                Box(Modifier.weight(1f).fillMaxHeight().sticker(Plate, c.glass, c.border, shadow).padding(horizontal = 24.dp), Alignment.CenterStart) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Crossfade(a.context ?: a.title, Modifier.weight(1f), tween(220), label = "title") { t ->
                            OneText(t, OneType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Black), c.text, maxLines = 1)
                        }
                        OneText("桜", OneType.Title, c.accent)
                    }
                }
                if (a.hasSearch) Box(Modifier.size(44.dp).press { if (a.reveal() > 0.6f) s.show() }.sticker(CircleShape, c.accent, c.border, c.border.copy(alpha = 0.5f)), Alignment.Center) { OneIconView(OneIcon.Search) { c.onAccent } }
            }
            if (s.open || morph > 0f) Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = morph }.sticker(RoundedCornerShape(25.dp), c.glass, c.border, shadow),
            ) { SearchRow(a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 18.dp, end = 6.dp)) }
        }
    }
}

private val AnimeTabs = listOf(
    Triple(OneIcon.Home, R.string.tab_home, "ピカッ"), Triple(OneIcon.Channels, R.string.tab_channels, "ザザッ"), Triple(OneIcon.Settings, R.string.tab_settings, "カチッ"),
)

@Composable
fun AnimeNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) { pop.snapTo(0f); pop.animateTo(1f, tween(800)) }
    Box(modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp).widthIn(max = 400.dp).fillMaxWidth().height(100.dp)) {
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().height(34.dp), Arrangement.spacedBy(8.dp)) {
            AnimeTabs.forEachIndexed { i, t ->
                Box(Modifier.weight(1f).fillMaxHeight(), Alignment.Center) {
                    if (i == selected) Inked(
                        t.third, OneType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic), AnimeYellow,
                        Modifier.graphicsLayer {
                            val p = pop.value; val k = 1.9f - 0.9f * (1f - (1f - p) * (1f - p))
                            scaleX = k; scaleY = k; rotationZ = -10f; alpha = 1f - p * p; translationY = -p * 14.dp.toPx()
                        },
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(66.dp), Arrangement.spacedBy(8.dp)) {
            AnimeTabs.forEachIndexed { i, (icon, label, _) ->
                val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.45f, 420f), label = "panel")
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .graphicsLayer { translationY = -8.dp.toPx() * on; val k = 1f + 0.06f * on; scaleX = k; scaleY = k }
                        .tvTab(i).press { onSelect(i) }
                        .sticker(Plate, lerp(c.glass, c.accent, on), c.border, lerp(c.accent.copy(alpha = 0.75f), AnimeYellow, on)),
                    Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneIconView(icon) { lerp(c.dim, c.onAccent, on) }
                        OneText(stringResource(label), OneType.Caption.copy(fontWeight = FontWeight.Bold), lerp(c.dim, c.onAccent, on), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun LazyListState.slot(key: Any): Float {
    val i = layoutInfo
    val v = i.visibleItemsInfo.firstOrNull { x -> x.key == key } ?: return 0f
    return ((v.offset + v.size / 2f) - (i.viewportStartOffset + i.viewportEndOffset) / 2f) / v.size
}

@Composable
private fun Modifier.dash(index: Int, seen: MutableSet<Int>, sign: Float): Modifier {
    val done = index in seen
    val p = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(Unit) { if (!done) { delay(index * 90L); p.animateTo(1f, spring(0.55f, 260f)); seen += index } }
    return graphicsLayer { translationX = (1f - p.value) * sign * 140.dp.toPx(); alpha = p.value.coerceIn(0f, 1f) }
}

@Composable
fun AnimeHome(a: HomeArgs) {
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
        if (heroes.isNotEmpty()) item(key = "hero") { Splash(heroes, a, Modifier.dash(0, seen, 1f)) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Chapter(1, R.string.sec_matches, null, Modifier.dash(1, seen, -1f)) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> MatchCard(m, Modifier.width(236.dp).edgeFx(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Chapter(2, R.string.sec_resume, null, Modifier.dash(2, seen, 1f)) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(200.dp).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        Kind.entries.forEach { k ->
            val shelf = d.movies.filter { it.kind == k }
            if (shelf.isNotEmpty()) item(key = k.name) {
                Chapter(3 + k.ordinal, k.title, { a.onAll(k) }, Modifier.dash(3 + k.ordinal, seen, if (k.ordinal % 2 == 0) -1f else 1f)) { row ->
                    items(shelf.take(10), key = { it.id }) { m -> Cover(m, row) { a.onMovie(m.id) } }
                }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Chapter(6, R.string.sec_channels, a.onAllChannels, Modifier.dash(6, seen, 1f)) { _ ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
    }
}

@Composable
internal fun AnimeHead(n: Int, @StringRes title: Int, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
        OneText("第${n}話", OneType.Section.copy(fontWeight = FontWeight.Black), c.onAccent, Modifier.sticker(Plate, c.accent, c.border, c.border.copy(alpha = 0.5f), 2.dp).padding(horizontal = 18.dp, vertical = 4.dp), 1)
        OneText(stringResource(title), OneType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Black), c.text, maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp).drawBehind {
            val step = 10.dp.toPx(); val n2 = (size.width / step).toInt().coerceAtLeast(1)
            for (i in 0 until n2) {
                val x = (i + 0.5f) * step
                drawCircle(c.accent.copy(alpha = 0.6f), 3.5.dp.toPx() * (1f - i / n2.toFloat()), Offset(if (rtl) size.width - x else x, size.height / 2f))
            }
        })
        trailing?.invoke()
    }
}

@Composable
private fun Chapter(n: Int, @StringRes title: Int, onAll: (() -> Unit)?, modifier: Modifier, row: LazyListScope.(LazyListState) -> Unit) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    Column(modifier, Arrangement.spacedBy(16.dp)) {
        AnimeHead(n, title) { if (onAll != null) Box(Modifier.size(36.dp).press(onAll).glass(3, 18.dp), Alignment.Center) { OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text } } }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { this.row(state) }
    }
}

@Composable
private fun Cover(m: Movie, row: LazyListState, onClick: () -> Unit) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val shape = remember { RoundedCornerShape(14.dp) }
    val fill = remember(c) { Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.65f), c.glass)) }
    Column(
        Modifier.width(132.dp).graphicsLayer { val f = row.slot(m.id).coerceIn(-2f, 2f) * dir; rotationZ = f * 5f; translationY = f * f * 8.dp.toPx() }.press(onClick),
        Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp).sticker(shape, c.glass, c.border, c.accent.copy(alpha = 0.75f))) {
            Box(Modifier.matchParentSize().background(fill))
            OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 80.sp, lineHeight = 90.sp), Color.White.copy(alpha = 0.25f), Modifier.align(Alignment.Center))
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(42.dp).clip(Starburst).background(AnimeYellow), Alignment.Center) {
                OneText(String.format(Locale.US, "%.1f", m.rating), OneType.Caption.copy(fontWeight = FontWeight.Black), Ink)
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            OneText(m.title, OneType.Body.copy(fontWeight = FontWeight.Bold), c.text, maxLines = 1)
            OneText("${m.year}", OneType.Caption, c.dim, maxLines = 1)
        }
    }
}

@Composable
private fun Splash(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    val scope = rememberCoroutineScope()
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val turn = rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "t")
    val shape = remember { RoundedCornerShape(topStart = 30.dp, topEnd = 12.dp, bottomEnd = 30.dp, bottomStart = 12.dp) }
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.75f), c.glass)) }
    val shade = remember { Brush.verticalGradient(0.4f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.85f)) }
    Column(modifier, Arrangement.spacedBy(14.dp), Alignment.CenterHorizontally) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), pageSpacing = 14.dp, key = { movies[it].id },
        ) { i ->
            val m = movies[i]
            val at = { ((i - pager.currentPage) - pager.currentPageOffsetFraction) * dir }
            val progress = a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }
            Box(
                Modifier.fillMaxWidth().height(250.dp)
                    .graphicsLayer { val p = at().coerceIn(-1f, 1f); rotationZ = p * 6f; translationY = abs(p) * 24.dp.toPx(); alpha = 1f - 0.45f * abs(p) }
                    .press { a.onMovie(m.id) }.sticker(shape, c.glass, c.border, c.accent.copy(alpha = 0.75f)),
            ) {
                Box(Modifier.matchParentSize().background(fill))
                OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 150.sp, lineHeight = 160.sp), Color.White.copy(alpha = 0.14f), Modifier.align(Alignment.Center))
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer { translationX = -at() * size.width * 0.2f; scaleX = 1.35f; scaleY = 1.35f })
                Box(Modifier.matchParentSize().drawBehind { rays(Color.White.copy(alpha = 0.13f), Offset(size.width * 0.8f, size.height * 0.3f), 24, turn.value) })
                Box(Modifier.matchParentSize().background(shade))
                OneText("おすすめ", OneType.Caption.copy(fontWeight = FontWeight.Bold), c.onAccent, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 14.dp).sticker(Plate, c.accent, Ink, Ink.copy(alpha = 0.6f), 2.dp).padding(horizontal = 18.dp, vertical = 3.dp), 1)
                Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(62.dp).clip(Starburst).background(AnimeYellow), Alignment.Center) {
                    OneText(String.format(Locale.US, "%.1f", m.rating), OneType.Section.copy(fontWeight = FontWeight.Black), Ink)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp), Arrangement.spacedBy(8.dp)) {
                    Inked(m.title, OneType.Display.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic), Color.White)
                    OneText("${m.year}  ·  ${m.genres.take(2).joinToString(" · ")}", OneType.Caption, Color.White.copy(alpha = 0.85f), maxLines = 1)
                    Row(
                        Modifier.padding(top = 2.dp).sticker(Plate, AnimeYellow, Ink, Ink.copy(alpha = 0.6f), 2.dp).padding(horizontal = 24.dp, vertical = 9.dp),
                        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                    ) {
                        OneIconView(OneIcon.Play, Modifier.size(18.dp)) { Ink }
                        OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body.copy(fontWeight = FontWeight.Black), Ink, maxLines = 1)
                    }
                }
            }
        }
        if (n > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(n) { j ->
                val on = j == pager.currentPage
                val k by animateFloatAsState(if (on) 1f else 0.6f, spring(0.5f, 500f), label = "dot")
                Box(
                    Modifier.size(22.dp).graphicsLayer { scaleX = k; scaleY = k; rotationZ = j * 36f }.press { scope.launch { pager.animateScrollToPage(j) } }
                        .clip(burst(5, 0.55f)).background(if (on) c.accent else c.dim.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

@Composable
internal fun AnimeHero(m: Movie, duration: String, height: Dp, scroll: ScrollState) {
    val c = LocalColors.current
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.75f), c.glass)) }
    val shade = remember { Brush.verticalGradient(0.35f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.72f)) }
    val turn = rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "t")
    Box(Modifier.fillMaxWidth().height(height).clipToBounds().background(fill)) {
        val art = Modifier.matchParentSize().graphicsLayer { translationY = scroll.value * 0.5f; scaleX = 1.12f; scaleY = 1.12f }
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, art)
        else OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 240.sp, lineHeight = 260.sp), Color.White.copy(alpha = 0.14f), Modifier.align(Alignment.Center).graphicsLayer { translationY = scroll.value * 0.5f })
        Box(Modifier.matchParentSize().drawBehind { rays(Color.White.copy(alpha = 0.12f), Offset(size.width * 0.5f, size.height * 0.4f), 32, turn.value) })
        Box(Modifier.matchParentSize().background(shade))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 56.dp)
                .graphicsLayer { translationY = -scroll.value * 0.25f; alpha = (1f - scroll.value / (size.height * 2.5f)).coerceIn(0f, 1f) },
            Arrangement.spacedBy(12.dp), Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), Arrangement.spacedBy(8.dp)) {
                OneText(m.genres.joinToString(" · "), OneType.Caption.copy(fontWeight = FontWeight.Bold), AnimeYellow, maxLines = 1)
                Inked(m.title, OneType.Display.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic), Color.White, maxLines = 2)
                OneText("${m.year}  ·  $duration", OneType.Caption, Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            Box(Modifier.size(70.dp).clip(Starburst).background(AnimeYellow), Alignment.Center) {
                OneText(String.format(Locale.US, "%.1f", m.rating), OneType.Title.copy(fontWeight = FontWeight.Black), Ink)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).drawBehind {
            drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f); close() }, c.bg)
            drawLine(c.border, Offset(0f, size.height), Offset(size.width, 0f), 2.dp.toPx())
        })
    }
}
