package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MineBg = Color(0xFF07060D)
private val MineA = Color(0xFFB46CFF)
private val MineB = Color(0xFF38D6FF)
private val MineSheet = Color(0xE615121F)

@Composable
fun MineHost(open: Boolean, movies: List<Movie>, theme: ThemeController, onMovie: (Int) -> Unit, onClose: () -> Unit) {
    val p = remember { Animatable(if (open) 1f else 0f) }
    var visible by remember { mutableStateOf(open) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) visible = true
        p.animateTo(if (open) 1f else 0f, tween(520, easing = FastOutSlowInEasing))
        if (!open) visible = false
    }
    BackHandler(open) { if (sheet) sheet = false else onClose() }
    if (!visible) return
    val tv = LocalTvMode.current
    val blurOn = theme.prefs.nativeBlur && Build.VERSION.SDK_INT >= 31
    val gear = remember { FocusRequester() }
    val fresh = remember { booleanArrayOf(true) }
    LaunchedEffect(sheet) {
        if (fresh[0]) fresh[0] = false
        else if (!sheet && tv) { delay(150); runCatching { gear.requestFocus() } }
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = p.value; val s = 1.12f - 0.12f * p.value; scaleX = s; scaleY = s }
            .background(MineBg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        CompositionLocalProvider(LocalTvLock provides (LocalTvLock.current || sheet)) {
            Box(Modifier.fillMaxSize().then(if (sheet && blurOn && !tv) Modifier.blur(22.dp) else Modifier)) {
                MineHome(movies, blurOn && !tv, gear, onMovie, { sheet = true }, onClose)
            }
        }
        if (sheet) MineSettings(theme, { sheet = false }, onClose)
    }
}

@Composable
private fun MineHome(movies: List<Movie>, blur: Boolean, gear: FocusRequester, onMovie: (Int) -> Unit, onSettings: () -> Unit, onExit: () -> Unit) {
    var kind by rememberSaveable { mutableIntStateOf(-1) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val pool = remember(movies, kind, genre) { movies.filter { (kind < 0 || it.kind.ordinal == kind) && (genre == null || genre in it.genres) } }
    val top = remember(pool) { pool.sortedByDescending { it.rating } }
    val genres = remember(movies, kind) {
        movies.filter { kind < 0 || it.kind.ordinal == kind }.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topInset() + 8.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "bar") { MineBar(gear, onSettings, onExit) }
        item(key = "kinds") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { MinePill(stringResource(R.string.filter_all), kind < 0, Modifier.tvAutoFocus()) { kind = -1; genre = null } }
                items(Kind.entries) { k -> MinePill(stringResource(k.title), kind == k.ordinal) { kind = k.ordinal; genre = null } }
            }
        }
        if (genre == null) {
            if (top.isNotEmpty()) item(key = "hero") { MineHero(top.take(5), blur, onMovie) }
            if (genres.isNotEmpty()) item(key = "genres") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(genres) { g -> MineGenre(g) { genre = g } }
                }
            }
            item(key = "top") { MineRail(stringResource(R.string.mine_rated), top.take(12), blur, onMovie) }
            item(key = "new") { MineRail(stringResource(R.string.mine_new), pool.sortedByDescending { it.year }.take(12), blur, onMovie) }
            genres.take(3).forEach { g -> item(key = "g$g") { MineRail(g, pool.filter { g in it.genres }, blur, onMovie) } }
        } else {
            item(key = "chosen") {
                Row(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                    OneText(genre.orEmpty(), OneType.Title, Color.White)
                    MinePill("×", false) { genre = null }
                }
            }
            items(pool.chunked(3), key = { row -> row.first().id }) { row ->
                Row(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(10.dp)) {
                    row.forEach { m -> MineCard(m, blur, Modifier.weight(1f), onMovie) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MineBar(gear: FocusRequester, onSettings: () -> Unit, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OneText("ONE+", OneType.Display, Color.White)
            OneText("MINE", OneType.Display, MineB)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MineButton(OneIcon.Settings, Modifier.focusRequester(gear), onSettings)
            MineButton(OneIcon.Close, Modifier, onExit)
        }
    }
}

@Composable
private fun MineButton(icon: OneIcon, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).press(onClick).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
        OneIconView(icon) { Color.White }
    }
}

@Composable
private fun MinePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(36.dp).press(onClick).clip(shape)
            .background(if (selected) Brush.horizontalGradient(listOf(MineA, MineB)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f))))
            .padding(horizontal = 16.dp),
        Alignment.Center,
    ) { OneText(text, OneType.Body, if (selected) Color.Black else Color.White, maxLines = 1) }
}

@Composable
private fun MineGenre(name: String, onClick: () -> Unit) {
    val h = remember(name) { name.hashCode().mod(360).toFloat() }
    Box(
        Modifier.size(120.dp, 64.dp).press(onClick).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(hsv(h, 0.6f, 0.8f), hsv((h + 60f) % 360f, 0.7f, 0.35f)))),
        Alignment.Center,
    ) { OneText(name, OneType.Section, Color.White, maxLines = 1) }
}

@Composable
private fun MineHero(items: List<Movie>, blur: Boolean, onMovie: (Int) -> Unit) {
    val state = rememberPagerState { items.size }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val reqs = remember(items) { List(items.size) { FocusRequester() } }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(items, held) {
        while (items.size > 1 && !held) { delay(6000); state.animateScrollToPage((state.currentPage + 1) % items.size) }
    }
    HorizontalPager(state, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 28.dp), pageSpacing = 14.dp) { i ->
        val m = items[i]
        MineArt(
            m, blur, 84.dp,
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).focusRequester(reqs[i]).onFocusChanged { held = it.hasFocus }
                .onKeyEvent { e ->
                    val dir = when (e.key) { Key.DirectionRight -> 1; Key.DirectionLeft -> -1; else -> 0 }
                    val step = if (rtl) -dir else dir
                    val to = i + step
                    if (dir != 0 && to in items.indices) {
                        if (e.type == KeyEventType.KeyDown) scope.launch { state.animateScrollToPage(to); runCatching { reqs[to].requestFocus() } }
                        true
                    } else false
                }
                .press { onMovie(m.id) }.clip(RoundedCornerShape(28.dp)),
        )
    }
}

@Composable
private fun MineRail(title: String, list: List<Movie>, blur: Boolean, onMovie: (Int) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OneText(title, OneType.Section, Color.White, Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { m -> MineCard(m, blur, Modifier.width(132.dp), onMovie) }
        }
    }
}

@Composable
private fun MineCard(m: Movie, blur: Boolean, modifier: Modifier, onMovie: (Int) -> Unit) {
    MineArt(m, blur, 56.dp, modifier.aspectRatio(2f / 3f).press { onMovie(m.id) }.clip(RoundedCornerShape(20.dp)))
}

@Composable
private fun MineArt(m: Movie, blur: Boolean, strip: Dp, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        MineImage(m, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(strip).clipToBounds()) {
            if (blur) MineImage(m, Modifier.align(Alignment.BottomCenter).requiredSize(w, h).blur(26.dp, BlurredEdgeTreatment.Unbounded))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (blur) 0.26f else 0.62f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(MineA.copy(alpha = 0.22f), MineB.copy(alpha = 0.22f)))))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.30f)))
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp)) {
                OneText(m.title, OneType.Body, Color.White, maxLines = 1)
                OneText(listOfNotNull(m.year.takeIf { it > 0 }?.toString(), m.genres.firstOrNull()).joinToString(" · "), OneType.Caption, Color.White.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MineImage(m: Movie, modifier: Modifier) {
    val h = remember(m.title) { m.title.hashCode().mod(360).toFloat() }
    Box(modifier.background(Brush.linearGradient(listOf(hsv(h, 0.65f, 0.85f), hsv((h + 70f) % 360f, 0.7f, 0.35f)))), Alignment.Center) {
        if (m.backdrop.isNotBlank()) RemoteImage(m.backdrop, Modifier.fillMaxSize())
        else OneText(m.title.take(1), OneType.SerifHero.copy(fontSize = 64.sp), Color.White.copy(alpha = 0.25f))
    }
}

@Composable
private fun MineSettings(theme: ThemeController, onDismiss: () -> Unit, onExit: () -> Unit) {
    val p = theme.prefs
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onDismiss() } })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .clip(shape).background(MineSheet).border(0.5.dp, Color.White.copy(alpha = 0.20f), shape)
                .pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
            Arrangement.spacedBy(16.dp),
        ) {
            OneText(stringResource(R.string.mine_title), OneType.Section, Color.White)
            MineSwitch(R.string.mine_blur, p.nativeBlur) { theme.update { copy(nativeBlur = it) }; theme.save() }
            MineSwitch(R.string.mine_enable, p.mine) { theme.update { copy(mine = it) }; theme.save(); if (!it) onExit() }
            OneButton(stringResource(R.string.mine_back), OneIcon.Back, onExit, Modifier.fillMaxWidth().tvAutoFocus())
        }
    }
}

@Composable
private fun MineSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        OneText(stringResource(label), OneType.Body, Color.White, Modifier.weight(1f))
        OneSwitch(checked, onChange)
    }
}
