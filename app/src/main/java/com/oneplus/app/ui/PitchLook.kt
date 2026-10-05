package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Match
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * PITCH (ملعب): the fifth look, made for football nights. Everything is stadium:
 *   · Sky/ground: mown stripes under floodlights that breathe (ambient() in OneKit); surfaces are squarer with chalk-line borders.
 *   · Header: a hanging SCOREBOARD with a live LED clock. It folds up out of sight while you scroll down and flips back on the way up.
 *   · Nav: the dock is a top-down PITCH (stripes, touchline, penalty boxes). A real ball sits on it, rolls to the tab you choose,
 *     hops on the way and juggles when idle. Every label stays visible.
 *   · Home: a big-screen hero with sweeping light, matches as scoreboard tickets, films as collectible PLAYER CARDS (tier by rating:
 *     gold / silver / bronze) whose foil shines and tilts as the row scrolls. Sections light up as they reach the middle of the screen.
 *   · Details: a floodlit hero, a stats strip, numbered sections. Pages arrive with a camera pan.
 */

private val Led = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
private val Chalk = Color.White.copy(alpha = 0.55f)

// ---- Ball and pitch --------------------------------------------------------------------------------------------------------

private fun DrawScope.ball() {
    val r = size.minDimension / 2f; val ink = Color(0xFF14181A)
    fun at(a: Double, d: Float) = Offset(center.x + r * d * cos(a).toFloat(), center.y + r * d * sin(a).toFloat())
    drawCircle(Color.White, r)
    clipPath(Path().apply { addOval(Rect(center, r)) }) {
        for (i in 0 until 5) drawCircle(ink, r * 0.2f, at(Math.toRadians(-54.0 + i * 72.0), 0.98f)) // patches on the rim, between the spokes
    }
    val pent = Path().apply {
        for (i in 0 until 5) at(Math.toRadians(-90.0 + i * 72.0), 0.4f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
        close()
    }
    drawPath(pent, ink)
    for (i in 0 until 5) { val a = Math.toRadians(-90.0 + i * 72.0); drawLine(ink, at(a, 0.4f), at(a, 0.8f), r * 0.08f) }
    drawCircle(Color.Black.copy(alpha = 0.3f), r, style = Stroke(r * 0.07f))
}

private fun DrawScope.pitchDock() {
    val w = size.width; val h = size.height; val m = 5.dp.toPx(); val st = Stroke(1.2.dp.toPx())
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14573A), Color(0xFF0B3524))))
    val bw = w / 8f
    for (i in 0 until 8 step 2) drawRect(Color.White.copy(alpha = 0.06f), Offset(i * bw, 0f), Size(bw, h)) // mown stripes
    drawRoundRect(Chalk, Offset(m, m), Size(w - 2 * m, h - 2 * m), CornerRadius(8.dp.toPx()), st)           // touchline
    drawRect(Chalk, Offset(m, h * 0.3f), Size(w * 0.1f, h * 0.4f), style = st)                              // penalty boxes
    drawRect(Chalk, Offset(w - m - w * 0.1f, h * 0.3f), Size(w * 0.1f, h * 0.4f), style = st)
}

// ---- Header ----------------------------------------------------------------------------------------------------------------

/** The LED clock: HH:mm with a colon that blinks. Only this composable recomposes, twice a second. */
@Composable
private fun Clock() {
    val t by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(500) } }
    val cal = Calendar.getInstance().apply { timeInMillis = t }
    val colon = if ((t / 500) % 2 == 0L) ":" else " "
    OneText(
        String.format(Locale.US, "%02d%s%02d", cal.get(Calendar.HOUR_OF_DAY), colon, cal.get(Calendar.MINUTE)),
        Led.copy(fontSize = 15.sp), PitchGold, Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp), 1,
    )
}

@Composable
fun PitchHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val s = rememberSearch(a.hasSearch, false, a.onQuery) {}
    val morph = s.morph
    val inset = topInset()
    Box(modifier.fillMaxWidth()) {
        Box( // the board: it hangs from the top edge and folds up when the page scrolls down
            Modifier.padding(top = inset).fillMaxWidth().height(56.dp)
                .graphicsLayer {
                    val r = max(a.reveal(), morph)
                    transformOrigin = TransformOrigin(0.5f, 0f); rotationX = (1f - r) * 90f; cameraDistance = 16f * density
                }
                .background(c.glass)
                .drawBehind { // touchline with a bright centre mark
                    drawRect(c.border, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                    drawRect(PitchGold, Offset(size.width / 2f - 24.dp.toPx(), size.height - 2.dp.toPx()), Size(48.dp.toPx(), 2.dp.toPx()))
                }
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp).graphicsLayer { alpha = 1f - morph },
                Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
            ) {
                val back = a.onBack
                if (back != null) Box(Modifier.size(40.dp).press { if (a.reveal() > 0.6f) back() }.glass(3, 12.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                else Canvas(Modifier.size(26.dp)) { ball() }
                Box(Modifier.width(3.dp).height(24.dp).background(PitchGold))
                OneText(a.context ?: a.title, Led.copy(fontSize = 19.sp), c.text, Modifier.weight(1f), 1)
                Clock()
                if (a.hasSearch) Box(Modifier.size(40.dp).press { if (a.reveal() > 0.6f) s.show() }.glass(3, 12.dp), Alignment.Center) { OneIconView(OneIcon.Search) { c.text } }
            }
            if (s.open || morph > 0f) SearchRow(
                a.query, a.onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp).graphicsLayer { alpha = morph },
            )
        }
        Box(Modifier.fillMaxWidth().height(inset).background(c.glass)) // the status area wears the board's colour, always
    }
}

// ---- Nav -------------------------------------------------------------------------------------------------------------------

private val PitchTabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)

@Composable
fun PitchNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val pos by animateFloatAsState(selected.toFloat(), spring(0.5f, 170f), label = "roll") // under-damped: the ball overshoots and settles
    val hop = remember { Animatable(1f) }
    LaunchedEffect(selected) { hop.snapTo(0f); hop.animateTo(1f, tween(560)) }
    val juggle = rememberInfiniteTransition(label = "juggle").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "j")
    Box(modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).widthIn(max = 400.dp).fillMaxWidth().height(120.dp)) {
        val shape = RoundedCornerShape(18.dp)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(76.dp).clip(shape).drawBehind { pitchDock() }.border(1.dp, Color.White.copy(alpha = 0.25f), shape)) {
            PitchTabs.forEachIndexed { i, (icon, label) ->
                val on by animateFloatAsState(if (i == selected) 1f else 0f, spring(0.8f, 400f), label = "tab")
                val ink = lerp(Color.White.copy(alpha = 0.62f), PitchGold, on)
                Column(Modifier.weight(1f).fillMaxHeight().press { onSelect(i) }, Arrangement.Center, Alignment.CenterHorizontally) {
                    OneIconView(icon, Modifier.graphicsLayer { val k = 1f + 0.12f * on; scaleX = k; scaleY = k }) { ink }
                    OneText(stringResource(label), OneType.Caption, ink, Modifier.padding(top = 4.dp), 1)
                }
            }
        }
        Box( // the ball's lane: one third of the dock wide, slid by the (springy) tab position
            Modifier.align(Alignment.BottomStart).padding(bottom = 66.dp).fillMaxWidth(1f / 3f).height(34.dp)
                .graphicsLayer { translationX = pos * size.width * dir },
            Alignment.Center,
        ) {
            Canvas(Modifier.offset(y = 12.dp).size(22.dp, 6.dp).graphicsLayer { alpha = 0.4f * (1f - sin(PI * hop.value).toFloat()) }) { drawOval(Color.Black) }
            Canvas(
                Modifier.size(26.dp).graphicsLayer {
                    translationY = -(sin(PI * hop.value).toFloat() * 20.dp.toPx() + abs(sin(PI * juggle.value)).toFloat() * 4.dp.toPx() * hop.value)
                    rotationZ = pos * 540f * dir
                }
            ) { ball() }
        }
    }
}

// ---- Home ------------------------------------------------------------------------------------------------------------------

/** Where a row item sits against the middle of its row, in item widths (0 = centred). */
private fun LazyListState.at(key: Any): Float {
    val i = layoutInfo
    val v = i.visibleItemsInfo.firstOrNull { x -> x.key == key } ?: return 0f
    return ((v.offset + v.size / 2f) - (i.viewportStartOffset + i.viewportEndOffset) / 2f) / v.size
}

/** Floodlights: a section is bright in the middle of the screen and dims toward the edges. */
internal fun Modifier.spot(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val i = state.layoutInfo
    val v = i.visibleItemsInfo.firstOrNull { x -> x.key == key } ?: return@graphicsLayer
    val span = (i.viewportEndOffset - i.viewportStartOffset).coerceAtLeast(1)
    val f = abs((v.offset + v.size / 2f - (i.viewportStartOffset + i.viewportEndOffset) / 2f) / span).coerceIn(0f, 1f)
    alpha = 1f - 0.55f * f; val k = 1f - 0.05f * f; scaleX = k; scaleY = k
}

@Composable
fun PitchHome(a: HomeArgs) {
    val d = a.state.data
    val seen = remember { mutableSetOf<Int>() }
    val byId = d.movies.associateBy { it.id }
    val resume = a.lib.progress.keys.mapNotNull { byId[it] }
    val heroes = remember(d.movies, resume) { (resume.take(2) + d.movies.sortedByDescending { it.rating }).distinct().take(5) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LazyColumn(
        Modifier.fillMaxSize(), a.list, PaddingValues(top = toolbarInset(), bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (heroes.isNotEmpty()) item(key = "hero") { BigScreen(heroes, a, Modifier.reveal(0, seen)) }
        if (d.matches.isNotEmpty()) item(key = "matches") {
            Lineup(1, R.string.sec_matches, null, Modifier.reveal(1, seen).spot(a.list, "matches")) { row ->
                items(d.matches.take(6), key = { it.id }) { m -> ScoreCard(m, Modifier.width(250.dp).edgeFx(row, m.id)) { a.onMatches(m.id) } }
            }
        }
        if (resume.isNotEmpty()) item(key = "resume") {
            Lineup(2, R.string.sec_resume, null, Modifier.reveal(2, seen).spot(a.list, "resume")) { row ->
                items(resume, key = { it.id }) { m -> ResumeCard(m, a.lib.fraction(m.id), Modifier.width(200.dp).edgeFx(row, m.id)) { a.onMovie(m.id) } }
            }
        }
        if (d.movies.isNotEmpty()) item(key = "movies") {
            Lineup(3, R.string.sec_movies, a.onAllMovies, Modifier.reveal(3, seen).spot(a.list, "movies")) { row ->
                items(d.movies.take(10), key = { it.id }) { m -> PlayerCard(m, row) { a.onMovie(m.id) } }
            }
        }
        if (d.channels.isNotEmpty()) item(key = "channels") {
            Lineup(4, R.string.sec_channels, a.onAllChannels, Modifier.reveal(4, seen).spot(a.list, "channels")) { _ ->
                items(d.channels.take(12), key = { it.id }) { ch -> ChannelTile(ch) { a.onChannel(ch.id) } }
            }
        }
    }
}

/** A numbered heading: a gold shirt-number square, the title, a dashed touchline. Shared with the details page. */
@Composable
internal fun PitchHead(n: Int, @StringRes title: Int, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalColors.current
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
        OneText("$n", Led.copy(fontSize = 14.sp), Color.Black, Modifier.background(PitchGold, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
        OneText(stringResource(title), OneType.Section, c.text)
        Box(Modifier.weight(1f).height(1.dp).drawBehind {
            drawLine(c.dim.copy(alpha = 0.5f), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        })
        trailing?.invoke()
    }
}

@Composable
private fun Lineup(n: Int, @StringRes title: Int, onAll: (() -> Unit)?, modifier: Modifier, row: LazyListScope.(LazyListState) -> Unit) {
    val c = LocalColors.current
    val state = rememberLazyListState()
    Column(modifier, Arrangement.spacedBy(14.dp)) {
        PitchHead(n, title) { if (onAll != null) Box(Modifier.size(34.dp).press(onAll).glass(3, 12.dp), Alignment.Center) { OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.text } } }
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { this.row(state) }
    }
}

/** A match as a scoreboard ticket: competition, the two crests around a gold LED kick-off time, the channel. Live ones glow red. */
@Composable
private fun ScoreCard(m: Match, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
    val pulse = rememberInfiniteTransition(label = "live").animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    @Composable fun Crest(name: String, mod: Modifier) = Column(mod, Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).border(1.dp, Chalk, CircleShape).background(c.accent.copy(alpha = 0.18f), CircleShape), Alignment.Center) { OneText(name.take(1), OneType.Section, c.text) }
        OneText(name, OneType.Caption, c.text, Modifier.fillMaxWidth(), 2)
    }
    Column(
        modifier.press(onClick).clip(shape).background(c.glass).border(1.dp, if (m.live) PitchRed.copy(alpha = 0.85f) else c.border, shape).padding(14.dp),
        Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            OneText(m.competition, OneType.Caption, c.dim, Modifier.weight(1f), 1)
            if (m.live) Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse.value }.background(PitchRed, CircleShape))
            OneText(m.status, OneType.Caption, if (m.live) PitchRed else c.dim, Modifier.padding(start = 6.dp), 1)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Crest(m.home, Modifier.weight(1f))
            OneText(m.time, Led.copy(fontSize = 22.sp), PitchGold, Modifier.padding(horizontal = 6.dp), 1)
            Crest(m.away, Modifier.weight(1f))
        }
        OneText(m.channel, OneType.Caption, c.dim, maxLines = 1)
    }
}

/**
 * A film as a collectible player card. Its tier follows the rating (gold from 8, silver from 6.5, bronze below), the big number
 * is the rating, and a band of foil crosses the card as the row scrolls while the card tilts toward the middle.
 */
@Composable
private fun PlayerCard(m: Movie, row: LazyListState, onClick: () -> Unit) {
    val c = LocalColors.current
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val shape = remember { CutCornerShape(topStart = 28.dp, topEnd = 6.dp, bottomEnd = 28.dp, bottomStart = 6.dp) }
    val tier = remember(m.rating) {
        when {
            m.rating >= 8f -> listOf(Color(0xFFF7E08A), Color(0xFFC8962B), Color(0xFF7A5A12))
            m.rating >= 6.5f -> listOf(Color(0xFFEEF3F6), Color(0xFFA3B0BA), Color(0xFF59656E))
            else -> listOf(Color(0xFFE9B98A), Color(0xFF9A6234), Color(0xFF55331A))
        }
    }
    val ink = Color(0xFF17120A)
    Column(
        Modifier.width(140.dp).height(206.dp)
            .graphicsLayer { rotationY = -row.at(m.id).coerceIn(-1.5f, 1.5f) * 14f * dir; cameraDistance = 14f * density }
            .press(onClick).clip(shape).background(Brush.verticalGradient(tier)).border(1.dp, Color.White.copy(alpha = 0.5f), shape)
            .drawWithContent {
                drawContent()
                val x = size.width * (0.5f + row.at(m.id) * 1.1f * dir) // the foil band follows the card's place in the row
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0f)), Offset(x - 70.dp.toPx(), 0f), Offset(x + 20.dp.toPx(), size.height * 0.7f)))
            },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer { alpha = 0.85f; scaleX = 1.5f; scaleY = 1.5f })
            else OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 96.sp, lineHeight = 100.sp), ink.copy(alpha = 0.18f), Modifier.align(Alignment.Center))
            Column(Modifier.padding(start = 14.dp, top = 12.dp)) {
                OneText("${(m.rating * 10).roundToInt()}", Led.copy(fontSize = 30.sp), ink)
                OneText(m.genres.firstOrNull().orEmpty(), OneType.Caption, ink, maxLines = 1)
            }
        }
        Column(Modifier.fillMaxWidth().background(ink.copy(alpha = 0.78f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            OneText(m.title, OneType.Body.copy(fontWeight = FontWeight.Bold), Color.White, maxLines = 1)
            OneText("${m.year}", OneType.Caption, tier[0], maxLines = 1)
        }
    }
}

/** The big screen: wide cut-corner cards with a band of light sweeping across, the artwork and the text sliding against each other. */
@Composable
private fun BigScreen(movies: List<Movie>, a: HomeArgs, modifier: Modifier) {
    val c = LocalColors.current
    val n = movies.size
    val pager = rememberPagerState { n }
    val scope = rememberCoroutineScope()
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val shape = remember { CutCornerShape(topStart = 34.dp, bottomEnd = 34.dp) }
    val sweep = rememberInfiniteTransition(label = "sweep").animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "s")
    val fill = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.glass)) }
    val shade = remember { Brush.verticalGradient(0.35f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.88f)) }
    Column(modifier, Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally) {
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp, key = { movies[it].id },
        ) { i ->
            val m = movies[i]
            val at = { ((i - pager.currentPage) - pager.currentPageOffsetFraction) * dir } // physical: > 0 = to the right of the middle
            val progress = a.lib.fraction(m.id).takeIf { m.id in a.lib.progress }
            Box(
                Modifier.fillMaxWidth().height(240.dp).graphicsLayer { val k = 1f - 0.07f * abs(at()).coerceAtMost(1f); scaleX = k; scaleY = k; alpha = 1f - 0.5f * abs(at()).coerceAtMost(1f) }
                    .press { a.onMovie(m.id) }.clip(shape).background(fill).border(1.dp, Chalk, shape),
            ) {
                OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 150.sp, lineHeight = 160.sp), Color.White.copy(alpha = 0.12f), Modifier.align(Alignment.Center))
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize().graphicsLayer { translationX = -at() * size.width * 0.25f; scaleX = 1.35f; scaleY = 1.35f })
                Box(Modifier.matchParentSize().background(shade).drawBehind {
                    val x = size.width * sweep.value // the band of light
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0f)), Offset(x - 90.dp.toPx(), 0f), Offset(x + 40.dp.toPx(), size.height)))
                })
                OneText(stringResource(R.string.lux_featured), OneType.Caption, Color.White, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 14.dp).background(PitchRed, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                OneText(String.format(Locale.US, "%.1f", m.rating), Led.copy(fontSize = 26.sp), PitchGold, Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp).graphicsLayer { translationX = at() * size.width * 0.35f }, // text moves against the art
                    Arrangement.spacedBy(6.dp),
                ) {
                    OneText(m.title, OneType.Display.copy(shadow = Shadow(Color.Black, blurRadius = 12f)), Color.White, maxLines = 1)
                    OneText("${m.year}  ·  ${m.genres.take(2).joinToString(" · ")}", OneType.Caption, Color.White.copy(alpha = 0.75f), maxLines = 1)
                    Row(
                        Modifier.padding(top = 4.dp).clip(CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)).background(PitchGold).padding(horizontal = 18.dp, vertical = 9.dp),
                        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                    ) {
                        OneIconView(OneIcon.Play, Modifier.size(18.dp)) { Color.Black }
                        OneText(stringResource(if (progress != null) R.string.movie_resume else R.string.movie_play), OneType.Body.copy(fontWeight = FontWeight.Bold), Color.Black, maxLines = 1)
                    }
                }
            }
        }
        if (n > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { // shirt numbers
            repeat(n) { j ->
                val on = j == pager.currentPage
                Box(
                    Modifier.size(26.dp).press { scope.launch { pager.animateScrollToPage(j) } }.clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                        .background(if (on) PitchGold else c.glass),
                    Alignment.Center,
                ) { OneText("${j + 1}", Led.copy(fontSize = 13.sp), if (on) Color.Black else c.dim) }
            }
        }
    }
}

// ---- Details ---------------------------------------------------------------------------------------------------------------

/** The details hero: the artwork under floodlights, parallax, a gold rating and the title on the pitch. */
@Composable
internal fun PitchHero(m: Movie, duration: String, height: androidx.compose.ui.unit.Dp, scroll: ScrollState) {
    val c = LocalColors.current
    val fill = remember(c) { Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.5f), c.glass)) }
    val fade = remember(c) { Brush.verticalGradient(0.3f to c.bg.copy(alpha = 0f), 1f to c.bg) }
    Box(Modifier.fillMaxWidth().height(height).clipToBounds().background(fill).drawBehind { floodlights(0.9f) }) {
        val art = Modifier.matchParentSize().graphicsLayer { translationY = scroll.value * 0.5f; scaleX = 1.12f; scaleY = 1.12f }
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, art)
        else OneText(m.title.take(1), OneType.LuxHero.copy(fontSize = 240.sp, lineHeight = 260.sp), c.text.copy(alpha = 0.10f), Modifier.align(Alignment.Center).graphicsLayer { translationY = scroll.value * 0.5f })
        Box(Modifier.matchParentSize().background(fade))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                .graphicsLayer { translationY = -scroll.value * 0.25f; alpha = (1f - scroll.value / (size.height * 2.5f)).coerceIn(0f, 1f) },
            Arrangement.spacedBy(14.dp), Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), Arrangement.spacedBy(8.dp)) {
                OneText(m.genres.joinToString(" · "), OneType.Caption, PitchGold, maxLines = 1)
                OneText(m.title, OneType.Display.copy(fontSize = 34.sp, lineHeight = 40.sp), c.text, maxLines = 2)
                OneText("${m.year}  ·  $duration", OneType.Caption, c.dim, maxLines = 1)
            }
            OneText(
                String.format(Locale.US, "%.1f", m.rating), Led.copy(fontSize = 34.sp), Color.Black,
                Modifier.background(PitchGold, CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Match-stats strip: rating with a gold bar, year, minutes. */
@Composable
internal fun PitchStats(m: Movie, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    @Composable fun Tile(value: String, @StringRes label: Int, bar: Float?, mod: Modifier) {
        Column(mod.clip(CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)).background(c.glass).border(1.dp, c.border, CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)).padding(12.dp), Arrangement.spacedBy(6.dp)) {
            OneText(value, Led.copy(fontSize = 22.sp), PitchGold, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.dim.copy(alpha = 0.25f)).drawBehind { if (bar != null) drawRect(PitchGold, size = Size(size.width * bar, size.height)) })
            OneText(stringResource(label), OneType.Caption, c.dim, maxLines = 1)
        }
    }
    Row(modifier.padding(horizontal = 20.dp).fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
        Tile(String.format(Locale.US, "%.1f", m.rating), R.string.pitch_rating, (m.rating / 10f).coerceIn(0f, 1f), Modifier.weight(1f))
        Tile("${m.year}", R.string.movie_year, null, Modifier.weight(1f))
        Tile("${m.durationMin}", R.string.movie_duration, (m.durationMin / 180f).coerceIn(0f, 1f), Modifier.weight(1f))
    }
}
