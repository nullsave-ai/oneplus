package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneplus.app.R
import com.oneplus.app.App
import com.oneplus.app.data.Kind
import com.oneplus.app.player.PlaySource
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OnePlusApp(
    theme: ThemeController,
    app: App = LocalContext.current.applicationContext as App,
    vm: MainViewModel = viewModel { MainViewModel(app.repository) },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tv = LocalTvMode.current
    val look = LocalLook.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var movieId by rememberSaveable { mutableIntStateOf(-1) }
    var showMovies by rememberSaveable { mutableStateOf(false) }
    var moviesKind by rememberSaveable { mutableIntStateOf(0) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var showMatches by rememberSaveable { mutableStateOf(false) }
    var matchFocus by rememberSaveable { mutableIntStateOf(-1) }
    var showTg by rememberSaveable { mutableStateOf(telegramDue(ctx)) }
    val library = app.library
    val matchesP = remember { Animatable(if (showMatches) 1f else 0f) }
    var playKind by rememberSaveable { mutableIntStateOf(0) }
    var playId by rememberSaveable { mutableIntStateOf(-1) }
    var playEp by rememberSaveable { mutableIntStateOf(-1) }
    var playStart by rememberSaveable { mutableLongStateOf(0L) }
    var lastChannel by rememberSaveable { mutableIntStateOf(-1) }
    var pickedGroup by rememberSaveable { mutableStateOf(app.prefs.string("group").orEmpty()) }
    val groups = state.base.groups
    val group = if (pickedGroup in groups) pickedGroup else groups.firstOrNull().orEmpty()
    val pickGroup = { g: String -> pickedGroup = g; app.prefs.put("group" to g) }
    val saveLast = { id: Int ->
        lastChannel = id
        state.base.channelsById[id]?.let { pickGroup(it.group) }
    }
    var fullscreen by rememberSaveable { mutableStateOf(true) }
    var slot by remember { mutableStateOf<Rect?>(null) }
    val byId = state.base.byId
    val detail = remember { DetailState(movieId >= 0) }
    val homeList = rememberLazyListState()
    val channelsList = rememberLazyListState()
    val pages = rememberSaveableStateHolder()
    val settingsScroll = rememberScrollState()
    val subScroll = rememberScrollState()
    var sPage by rememberSaveable { mutableIntStateOf(-1) }
    val range = with(LocalDensity.current) { look.collapseAt.toPx() }
    val progress = rememberToolbarProgress {
        when (tab) {
            0 -> scrollTarget(homeList.firstVisibleItemIndex, homeList.firstVisibleItemScrollOffset, range)
            1 -> scrollTarget(channelsList.firstVisibleItemIndex, channelsList.firstVisibleItemScrollOffset, range)
            else -> scrollTarget(0, (if (sPage >= 0) subScroll else settingsScroll).value, range)
        }
    }
    val revealScroll = rememberToolbarReveal(tab * 10 + sPage) {
        when (tab) {
            0 -> homeList.firstVisibleItemIndex * 100_000 + homeList.firstVisibleItemScrollOffset
            1 -> 0
            else -> (if (sPage >= 0) subScroll else settingsScroll).value
        }
    }
    val reveal: () -> Float = if (tv) ({ 1f }) else revealScroll
    BackHandler(tab == 2 && sPage >= 0) { sPage = -1 }
    val nearChannels by remember {
        derivedStateOf {
            val info = homeList.layoutInfo
            homeList.canScrollBackward && info.visibleItemsInfo.any { it.key == "channels" && it.offset < info.viewportSize.height / 2 }
        }
    }
    val source = remember(playKind, playId, playEp, state.base) {
        when (playKind) {
            1 -> byId[playId]?.let { m ->
                val e = m.episodes.getOrNull(playEp)
                if (e != null) PlaySource(e.url, "${m.title} - ${e.title}", live = false, cacheable = true)
                else PlaySource(m.url, m.title, live = false, cacheable = true, startMs = playStart)
            }
            2 -> state.base.channelsById[playId]?.let { PlaySource(it.url, it.name, live = true) }
            else -> null
        }
    }
    LaunchedEffect(source, playKind, state.base) {
        if (playKind in 1..2 && source == null && byId.isNotEmpty()) playKind = 0
    }
    val wantsInPlace = playKind == 2 && !fullscreen
    val parked = wantsInPlace && tab != 1
    val progressId = playId
    val session = rememberPlayerSession(
        source, parked, if (playKind == 1 && playEp < 0) ({ pos, dur -> library.saveProgress(progressId, pos, dur) }) else null,
    )
    LaunchedEffect(parked) { if (parked) { delay(5 * 60_000L); playKind = 0 } }
    val playFull = { kind: Int, id: Int, ep: Int ->
        if (kind == 2) saveLast(id) else playStart = if (ep < 0) library.resumeMs(id) else 0L
        playKind = kind; playId = id; playEp = ep; fullscreen = true
    }
    val playInline = { id: Int ->
        if (tv && playKind == 2 && playId == id) fullscreen = true else { saveLast(id); playKind = 2; playId = id; playEp = -1; fullscreen = false }
        Unit
    }
    LaunchedEffect(tab, state.base) {
        if (tab != 1) return@LaunchedEffect
        if (playKind == 2) return@LaunchedEffect
        val inGroup = state.base.byGroup[group].orEmpty()
        (inGroup.firstOrNull { it.id == lastChannel } ?: inGroup.firstOrNull())?.let { playInline(it.id) }
    }
    LaunchedEffect(movieId, state.base) {
        if (movieId >= 0 && byId.isNotEmpty() && movieId !in byId) {
            detail.enter.snapTo(0f)
            movieId = -1
        }
    }
    val tabReqs = remember { List(Tabs.size) { FocusRequester() } }
    val tvTabs = if (tv) TvTabs(tab, { tab = it }, tabReqs) else null
    val onTab = { i: Int -> tab = i; if (tv) runCatching { tabReqs[i].requestFocus() }; Unit }
    var baseFocused by remember { mutableStateOf(false) }
    val dialogOpen = showTg || showClear
    val playerFull = session != null && !wantsInPlace
    val lockBase = movieId >= 0 || showMatches || showMovies || playerFull || dialogOpen
    val lockOver = playerFull || dialogOpen
    BackHandler(enabled = tv && baseFocused && !lockBase && !(tab == 2 && sPage >= 0)) { runCatching { tabReqs[tab].requestFocus() } }
    var tvNavH by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalTvNavHeight provides tvNavH) {
        BoxWithConstraints(Modifier.fillMaxSize().ambient()) {
            val wide = maxWidth >= 600.dp
            val portrait = maxHeight >= maxWidth
            Box(Modifier.fillMaxSize().graphicsLayer {
                val d = maxOf(detail.depth, matchesP.value)
                if (!tv) underMotion(theme.prefs.transition, d)
            }.drawWithContent {
                drawContent()
                drawRect(Color.Black, alpha = 0.35f * maxOf(detail.depth, matchesP.value))
            }) {
                Box(
                    Modifier.fillMaxSize()
                        .then(if (tv) Modifier.onFocusChanged { baseFocused = it.hasFocus }.focusGroup() else Modifier)
                ) {
                TvLayer(lockBase, tabReqs[tab]) {
                AnimatedContent(tab, Modifier.fillMaxSize(), transitionSpec = {
                    val dir = if (targetState > initialState) -1 else 1
                    pageTransition(theme.prefs.transition, dir) { lookTransition(look, dir) }
                }, label = "page") { t ->
                    Box(Modifier.fillMaxSize().then(pageBackdrop(theme.prefs.transition))) {
                    pages.SaveableStateProvider(t) {
                    when (t) {
                        0 -> {
                            val onMovie = { id: Int -> focus.clearFocus(); movieId = id }
                            val onChannel = { id: Int -> playFull(2, id, -1) }
                            val onAll = { k: Kind -> focus.clearFocus(); moviesKind = k.ordinal; showMovies = true }
                            val onAllChannels = { tab = 1 }
                            val onMatches = { id: Int -> focus.clearFocus(); matchFocus = id; showMatches = true }
                            look.home(HomeArgs(state, homeList, portrait, wide, library, onMovie, onChannel, onAll, onAllChannels, onMatches))
                        }
                        1 -> {
                            val playing = if (playKind == 2) playId else -1
                            ChannelsScreen(state.data.channels, state.query.isNotBlank(), playing, group, portrait, channelsList, { slot = it }, pickGroup, playInline)
                        }
                        else -> SettingsScreen(
                            theme, settingsScroll, subScroll, sPage, { sPage = it }, wide,
                            library.list.mapNotNull { byId[it] }, { id -> focus.clearFocus(); movieId = id },
                            { openTelegram(ctx) }, { showClear = true },
                        )
                    }
                    }
                    }
                }
                val inSub = tab == 2 && sPage >= 0
                val title = stringResource(if (inSub) settingsTitle(sPage) else if (tab == 0) R.string.app_name else Tabs[tab].second)
                val context = if (tab == 0 && nearChannels) stringResource(R.string.tab_channels) else null
                look.header(
                    HeaderArgs(title, context, tab != 2, state.query, vm::onQuery, progress, reveal, if (inSub) ({ sPage = -1 }) else null),
                    Modifier.align(Alignment.TopCenter),
                )
                }
                }
                TvLayer(movieId >= 0 || lockOver) {
                    MoviesHost(showMovies, Kind.entries[moviesKind], state.base.shelves[Kind.entries[moviesKind]].orEmpty(), portrait, { id -> movieId = id }) { showMovies = false }
                }
            }
            TvLayer(lockOver) {
                MatchesHost(showMatches, matchesP, state.all.matches, wide, matchFocus) { showMatches = false }
                MovieDetailHost(detail, state.all.movies, library, movieId, { movieId = it }, { id, ep -> playFull(1, id, ep) }, { movieId = -1 })
            }
            val r = if (tab == 1) slot else null
            val inPlace = wantsInPlace && r != null
            if (session != null && (!wantsInPlace || inPlace)) {
                val box = if (r != null && inPlace) with(LocalDensity.current) {
                    Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(r.width.toDp(), r.height.toDp())
                } else Modifier.fillMaxSize()
                Box(box.then(if (inPlace) Modifier.clip(RoundedCornerShape(20.dp)) else Modifier)) {
                    TvLayer(dialogOpen) {
                        PlayerScreen(
                            session, fullscreen = !inPlace,
                            onToggleFullscreen = if (playKind == 2 && r != null) ({ fullscreen = !fullscreen }) else null,
                            onClose = { playKind = 0; fullscreen = true },
                        )
                    }
                }
            }
            val covered = showMovies || (session != null && !wantsInPlace)
            val navShow by animateFloatAsState(if (covered) 0f else 1f, tween(160), label = "nav")
            val navAlpha = { navShow * (1f - maxOf(detail.depth, matchesP.value)) }
            val navMod = Modifier
                .layout { m, c ->
                    val pl = m.measure(c)
                    if (navAlpha() < 0.02f) layout(0, 0) {} else layout(pl.width, pl.height) { pl.place(0, 0) }
                }
                .graphicsLayer { alpha = navAlpha() }
            CompositionLocalProvider(LocalTvLock provides lockBase, LocalTvTabs provides tvTabs) {
                if (tv) {
                    val density = LocalDensity.current
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(top = topInset() + look.top)
                            .onSizeChanged { if (it.height > 0) tvNavH = with(density) { it.height.toDp() } }.tvNavShrink(),
                    ) { look.nav(tab, onTab, navMod) }
                } else look.nav(tab, onTab, navMod.align(Alignment.BottomCenter))
            }
            if (showTg) TelegramDialog(
                onSkip = { showTg = false; telegramSkipped(ctx) },
                onJoin = { showTg = false; telegramJoined(ctx); openTelegram(ctx) },
            )
            if (showClear) ClearHistoryDialog(
                onCancel = { showClear = false },
                onConfirm = { showClear = false; library.clearHistory() },
            )
        }
    }
}

private fun lookTransition(look: Look, dir: Int): ContentTransform =
    if (look.anime) {
        (fadeIn(tween(260)) + scaleIn(tween(460, easing = CubicBezierEasing(0.2f, 1.35f, 0.4f, 1f)), 0.82f)) togetherWith
            (fadeOut(tween(160)) + scaleOut(tween(220), 1.12f))
    } else if (look.pitch) {
        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it } + fadeIn(tween(240))) togetherWith
            (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -dir * it / 3 } + fadeOut(tween(200)))
    } else if (look.cosmic) {
        (fadeIn(tween(420)) + scaleIn(tween(520, easing = FastOutSlowInEasing), 1.18f)) togetherWith
            (fadeOut(tween(260)) + scaleOut(tween(420), 0.86f))
    } else fadeIn(tween(180)) togetherWith fadeOut(tween(180))

private val Tabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)
private val NavSpring = spring<Float>(0.62f, 380f)

@Composable
fun LiquidNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pos = remember { Animatable(selected.toFloat()) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(selected) { pos.animateTo(selected.toFloat(), NavSpring) }

    val settle = {
        val t = pos.value.roundToInt().coerceIn(0, 2)
        if (t != selected && abs(pos.value - selected) > 0.35f) onSelect(t)
        else scope.launch { pos.animateTo(selected.toFloat(), NavSpring) }
        Unit
    }
    Box(
        modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .widthIn(max = 400.dp).fillMaxWidth().height(64.dp).glass(3, 28.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(selected, rtl) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    change.consume()
                    val d = dx / (widthPx / 3f) * (if (rtl) -1f else 1f)
                    scope.launch { pos.animateTo((pos.value + d).coerceIn(0f, 2f), spring(stiffness = 900f)) }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / 3f
            val v = pos.velocity * (if (rtl) -1f else 1f)
            val st = (abs(pos.velocity) * 0.05f).coerceAtMost(0.45f) * w
            val cx = (if (rtl) 3f - (pos.value + 0.5f) else pos.value + 0.5f) * w
            val half = w * 0.40f
            val l = cx - half - (if (v < 0) st else st * 0.25f)
            val r = cx + half + (if (v > 0) st else st * 0.25f)
            val h = size.height * 0.78f - st * 0.3f
            val tl = Offset(l, (size.height - h) / 2f)
            drawRoundRect(c.selection, tl, Size(r - l, h), CornerRadius(h / 2f))
            drawRoundRect(c.accent.copy(alpha = 0.45f), tl, Size(r - l, h), CornerRadius(h / 2f), Stroke(1.dp.toPx()))
        }
        Row(Modifier.fillMaxSize()) {
            Tabs.forEachIndexed { i, (icon, label) ->
                val prox = { (1f - abs(pos.value - i)).coerceIn(0f, 1f) }
                val src = remember { MutableInteractionSource() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().tvTab(i).tvFocusRing(src).tvLockable().clickable(src, null) { onSelect(i) },
                    Arrangement.Center, Alignment.CenterHorizontally,
                ) {
                    OneIconView(icon, Modifier.graphicsLayer {
                        val p = prox(); scaleX = 1f + 0.12f * p; scaleY = scaleX; translationY = -2.dp.toPx() * p
                    }) { lerp(c.dim, c.accent, prox()) }
                    OneText(stringResource(label), OneType.Caption, if (selected == i) c.accent else c.dim, Modifier.padding(top = 4.dp), 1)
                }
            }
        }
    }
}
