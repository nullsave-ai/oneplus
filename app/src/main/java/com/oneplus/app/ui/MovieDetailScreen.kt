package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Library
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import java.util.Locale
import kotlinx.coroutines.launch

@Stable
class DetailState(isOpen: Boolean) {
    val enter = Animatable(if (isOpen) 1f else 0f)

    var drag by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(1f)

    val depth: Float get() = enter.value * (1f - (drag / height).coerceIn(0f, 1f))
}

@Composable
fun MovieDetailHost(state: DetailState, movies: List<Movie>, lib: Library, movieId: Int, onOpen: (Int) -> Unit, onPlay: (Int, Int) -> Unit, onClosed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kind = LocalTransition.current
    val open = movieId >= 0
    val close: () -> Unit = { scope.launch { state.enter.move(0f, kind); onClosed(); state.drag = 0f } }
    val latestClose by rememberUpdatedState(close)

    LaunchedEffect(open) { if (open) state.enter.move(1f, kind) }
    BackHandler(open, close)

    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && state.drag > 0f) {
                    val d = maxOf(available.y, -state.drag)
                    state.drag += d
                    return Offset(0f, d)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    state.drag += available.y * 0.55f
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.drag <= 0f) return Velocity.Zero
                if (state.drag > state.height * 0.14f || available.y > 2200f) latestClose()
                else animate(state.drag, 0f, animationSpec = spring(0.8f, 420f)) { v, _ -> state.drag = v }
                return available
            }
        }
    }

    val movie = remember(movies, movieId) { movies.firstOrNull { it.id == movieId } } ?: return
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { state.height = it.height.toFloat() }
            .graphicsLayer {
                val p = state.enter.value
                val pull = (state.drag / state.height).coerceIn(0f, 1f)
                if (kind == PageTransition.Look) {
                    translationY = (1f - p) * state.height + state.drag
                    val s = 1f - 0.07f * pull
                    scaleX = s; scaleY = s
                } else {
                    overMotion(kind, p)
                    translationY = state.drag
                    scaleX *= 1f - 0.07f * pull; scaleY *= 1f - 0.07f * pull
                }
                clip = pull > 0.002f
                shape = RoundedCornerShape(36.dp * pull)
            }
            .ambient()
            .nestedScroll(connection)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        key(movie.id) { MovieDetail(movie, movies, lib, close, onOpen, onPlay) }
    }
}

@Composable
private fun MovieDetail(m: Movie, all: List<Movie>, lib: Library, onBack: () -> Unit, onOpen: (Int) -> Unit, onPlay: (Int, Int) -> Unit) {
    val c = LocalColors.current
    val scroll = rememberScrollState()
    val pitch = LocalLook.current.pitch
    val anime = LocalLook.current.anime
    val seen = remember(m.id) { mutableSetOf<Int>() }
    val heroH = topInset() + if (pitch || anime) 420.dp else 330.dp
    val heroPx = with(LocalDensity.current) { heroH.toPx() }
    val added = m.id in lib.list
    val similar = remember(m.id, all) {
        all.filter { it.id != m.id && it.kind == m.kind }.sortedByDescending { o -> o.genres.count { it in m.genres } }.take(6)
    }
    val duration = "${m.durationMin / 60} ${stringResource(R.string.unit_hour)} ${m.durationMin % 60} ${stringResource(R.string.unit_min)}"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp)
        ) {
            if (anime) AnimeHero(m, duration, heroH, scroll) else if (pitch) PitchHero(m, duration, heroH, scroll) else Hero(m, duration, heroH, scroll)
            Box(Modifier.fillMaxWidth(), Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(12.dp)) {
                        OneButton(stringResource(if (lib.resumeMs(m.id) > 0L) R.string.movie_resume else R.string.movie_play), OneIcon.Play, { onPlay(m.id, if (m.episodes.isEmpty()) -1 else 0) }, Modifier.weight(1.4f).tvAutoFocus())
                        OneButton(
                            stringResource(R.string.movie_list), if (added) OneIcon.Check else OneIcon.Plus,
                            { lib.toggle(m.id) }, Modifier.weight(1f), primary = false,
                        )
                    }
                    if (pitch) PitchStats(m)
                    Genres(m.genres)
                    if (m.episodes.isNotEmpty()) Section(R.string.movie_episodes, 0, seen) {
                        val seasons = remember(m.id) { m.episodes.map { it.season }.distinct().sorted() }
                        var season by rememberSaveable(m.id) { mutableIntStateOf(seasons.first()) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seasons) { n -> OneChip(stringResource(R.string.season_n, n), n == season, { season = n }) }
                            }
                            Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(2, 22.dp)) {
                                m.episodes.withIndex().filter { it.value.season == season }.forEachIndexed { n, (i, e) -> EpisodeRow(n + 1, e.title) { onPlay(m.id, i) } }
                            }
                        }
                    }
                    Section(R.string.movie_story, 1, seen) { Story(m.synopsis) }
                    Section(R.string.movie_cast, 2, seen) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(m.cast) { name -> CastMember(name) }
                        }
                    }
                    Section(R.string.movie_details, 3, seen) {
                        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(2, 22.dp).padding(vertical = 4.dp)) {
                            InfoRow(R.string.movie_director, m.director)
                            InfoRow(R.string.movie_year, m.year.toString())
                            InfoRow(R.string.movie_duration, duration)
                        }
                    }
                    if (similar.isNotEmpty()) Section(R.string.movie_similar, 4, seen) {
                        val row = rememberLazyListState()
                        LazyRow(state = row, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(similar, key = { it.id }) { s -> Poster(s, Modifier.width(124.dp).edgeFx(row, s.id)) { onOpen(s.id) } }
                        }
                    }
                }
            }
        }
        TopBar(m.title, scroll, heroPx, onBack, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Hero(m: Movie, duration: String, height: Dp, scroll: ScrollState) {
    val c = LocalColors.current
    val banner = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.dim.copy(alpha = 0.20f))) }
    val poster = remember(c) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.40f), c.dim.copy(alpha = 0.22f))) }
    val fade = remember(c) { Brush.verticalGradient(0.45f to c.bg.copy(alpha = 0f), 1f to c.bg) }
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        Box(
            Modifier.matchParentSize().graphicsLayer { translationY = scroll.value * 0.4f }.background(banner)
                .drawWithCache {
                    val glow = Brush.radialGradient(
                        listOf(c.accent.copy(alpha = 0.35f), c.accent.copy(alpha = 0f)),
                        Offset(size.width * 0.82f, size.height * 0.18f), size.maxDimension * 0.6f,
                    )
                    onDrawBehind { drawRect(glow); drawDither() }
                }
        ) {
            if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
        }
        Box(Modifier.matchParentSize().background(fade))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            Arrangement.spacedBy(16.dp), Alignment.Bottom,
        ) {
            Box(
                Modifier.width(112.dp).aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp)).background(c.bg).background(poster)
                    .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
            ) {
                if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.matchParentSize())
            }
            Column(Modifier.weight(1f).padding(bottom = 4.dp), Arrangement.spacedBy(6.dp)) {
                OneText(m.title, OneType.Title, c.text, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OneText("${m.year}", OneType.Caption, c.dim)
                    OneDot(c.dim)
                    OneText(duration, OneType.Caption, c.dim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OneIconView(OneIcon.Star) { c.accent }
                    OneText(String.format(Locale.US, "%.1f", m.rating), OneType.Section, c.text)
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, scroll: ScrollState, heroPx: Float, onBack: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    if (LocalLook.current.pitch || LocalLook.current.anime) Box(
        modifier.fillMaxWidth().height(topInset())
            .graphicsLayer { alpha = ((scroll.value - heroPx * 0.55f) / (heroPx * 0.1f)).coerceIn(0f, 1f) }.background(c.bg)
    )
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = topInset() + 12.dp),
        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).press(onBack).glass(3, 22.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
        Box(
            Modifier.weight(1f).height(44.dp)
                .graphicsLayer { alpha = ((scroll.value - heroPx * 0.6f) / (heroPx * 0.2f)).coerceIn(0f, 1f) }
                .glass(3, 22.dp),
            Alignment.Center,
        ) { OneText(title, OneType.Section, c.text, Modifier.padding(horizontal = 16.dp), 1) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Genres(genres: List<String>) {
    val c = LocalColors.current
    val thick = LocalLook.current.anime
    val outlined = LocalLook.current.pitch || thick
    FlowRow(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
        genres.forEach { g ->
            OneText(
                g, OneType.Caption, if (outlined) c.text else c.accent,
                (if (outlined) Modifier.border(if (thick) 2.dp else 0.5.dp, c.border, CircleShape) else Modifier.background(c.accentSoft, RoundedCornerShape(12.dp))).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Story(text: String) {
    val c = LocalColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().press { expanded = !expanded }.glass(2, 22.dp)
            .animateContentSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text, style = OneType.Body.copy(color = c.text, lineHeight = 23.sp),
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow },
        )
        if (overflow || expanded) OneText(stringResource(if (expanded) R.string.movie_less else R.string.movie_more), OneType.Caption, c.accent)
    }
}

@Composable
private fun EpisodeRow(index: Int, title: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().press(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
    ) {
        OneText("${index + 1}", OneType.Section, c.accent, Modifier.width(28.dp))
        OneText(title, OneType.Body, c.text, Modifier.weight(1f), 1)
        OneIconView(OneIcon.Play, Modifier.size(18.dp)) { c.dim }
    }
}

@Composable
private fun CastMember(name: String) {
    val c = LocalColors.current
    Column(Modifier.width(72.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).press { }.glass(2, 28.dp), Alignment.Center) { OneText(name.take(1), OneType.Section, c.accent) }
        OneText(name, OneType.Caption, c.dim, maxLines = 1)
    }
}

@Composable
private fun InfoRow(@StringRes label: Int, value: String) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        OneText(stringResource(label), OneType.Body, c.dim)
        OneText(value, OneType.Body, c.text, maxLines = 1)
    }
}

@Composable
private fun Section(@StringRes title: Int, n: Int, seen: MutableSet<Int>, content: @Composable () -> Unit) {
    val pitch = LocalLook.current.pitch
    val anime = LocalLook.current.anime
    Column(Modifier.then(if (pitch || anime) Modifier.reveal(n, seen) else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (anime) AnimeHead(n, title) else if (pitch) PitchHead(n, title)
        else OneText(stringResource(title), OneType.Section, LocalColors.current.text, Modifier.padding(horizontal = 20.dp))
        content()
    }
}
