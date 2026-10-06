package com.oneplus.app.ui

import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.graphics.Brush
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
import com.oneplus.app.data.Library
import com.oneplus.app.player.PlaySource
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OnePlusApp(theme: ThemeController, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val isAppOutdated = remember(config) {
        val currentVer = com.oneplus.app.BuildConfig.VERSION_CODE
        (currentVer < config.minVersionCode) || (config.forceUpdate && currentVer < config.latestVersionCode)
    }
    val tv = LocalTvMode.current
    val look = LocalLook.current
    val noDepth = tv // the pages do not shrink behind the details page on a TV
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    // Low-RAM devices start with the light glass tier.
    var fx by rememberSaveable { mutableStateOf(!(ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var movieId by rememberSaveable { mutableIntStateOf(-1) }
    var showMovies by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) } // the "clear watch history" confirmation
    var showMatches by rememberSaveable { mutableStateOf(false) }
    var matchFocus by rememberSaveable { mutableIntStateOf(-1) } // the match tapped on Home: opens expanded on the matches page
    var showTg by rememberSaveable { mutableStateOf(telegramDue(ctx)) } // decided once per launch
    val library = remember { Library(ctx.getSharedPreferences("library", Context.MODE_PRIVATE)) }
    val matchesP = remember { Animatable(if (showMatches) 1f else 0f) } // opening progress of the matches page (also drives the depth effect)
    var playKind by rememberSaveable { mutableIntStateOf(0) } // 0 none · 1 movie · 2 channel
    var playId by rememberSaveable { mutableIntStateOf(-1) }
    var playStart by rememberSaveable { mutableLongStateOf(0L) } // movie: where to resume (ms)
    // Channels: the page plays by itself. Within a session it goes back to the last channel; across launches only the
    // last pressed group is kept (and only while it still exists, else the first group).
    val chPrefs = remember { ctx.getSharedPreferences("channels", Context.MODE_PRIVATE) }
    var lastChannel by rememberSaveable { mutableIntStateOf(-1) }
    var pickedGroup by rememberSaveable { mutableStateOf(chPrefs.getString("group", "").orEmpty()) }
    val groups = remember(state.all.channels) { state.all.channels.map { it.group }.distinct() }
    val group = if (pickedGroup in groups) pickedGroup else groups.firstOrNull().orEmpty()
    val pickGroup = { g: String -> pickedGroup = g; chPrefs.edit().putString("group", g).apply() }
    val saveLast = { id: Int ->
        lastChannel = id
        state.all.channels.firstOrNull { it.id == id }?.let { pickGroup(it.group) }
    }
    var fullscreen by rememberSaveable { mutableStateOf(true) }
    var slot by remember { mutableStateOf<Rect?>(null) } // where the Channels page wants the player drawn when it is not fullscreen
    val byId = remember(state.all.movies) { state.all.movies.associateBy { it.id } }
    val detail = remember { DetailState(movieId >= 0) }
    val homeList = rememberLazyListState()
    val channelsList = rememberLazyListState()
    val pages = rememberSaveableStateHolder() // each page keeps its own remembered state (expanded rows, inner scroll positions) while another tab is shown
    val settingsScroll = rememberScrollState()
    val subScroll = rememberScrollState() // the settings sub-page that is open (only one at a time)
    var sPage by rememberSaveable { mutableIntStateOf(-1) } // phone settings: -1 = the list, otherwise the sub-page that is open
    val range = with(LocalDensity.current) { look.collapseAt.toPx() }
    val progress = rememberToolbarProgress {
        when (tab) {
            0 -> scrollTarget(homeList.firstVisibleItemIndex, homeList.firstVisibleItemScrollOffset, range)
            1 -> scrollTarget(channelsList.firstVisibleItemIndex, channelsList.firstVisibleItemScrollOffset, range) // the player stays put; the channel list drives the toolbar
            else -> scrollTarget(0, (if (sPage >= 0) subScroll else settingsScroll).value, range)
        }
    }
    // The header leaves while the page scrolls down and returns on the first scroll up. (Channels keeps its player under it: no hiding.)
    // TV Mode: the remote can only reach a header that is there, so it never hides.
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
    // Only an id is persisted; the URL is always resolved from the catalogue (never stored or passed around as free text).
    val source = remember(playKind, playId, state.all) {
        when (playKind) {
            1 -> state.all.movies.firstOrNull { it.id == playId }?.let { PlaySource(it.url, it.title, live = false, cacheable = true, startMs = playStart) }
            2 -> state.all.channels.firstOrNull { it.id == playId }?.let { PlaySource(it.url, it.name, live = true) }
            else -> null
        }
    }
    LaunchedEffect(source, playKind, state.all) {
        if (playKind in 1..2 && source == null && state.all.movies.isNotEmpty()) playKind = 0
    }
    // The Channels page's in-place player is "parked" (paused, hidden, but still loaded) while another tab is shown, so coming
    // back to the page does not rebuild it. A player left parked for a long time is let go.
    val wantsInPlace = playKind == 2 && !fullscreen
    val parked = wantsInPlace && tab != 1
    val progressId = playId // by value: the final report of a movie that is replaced must still go to that movie
    val session = rememberPlayerSession(
        source, parked, if (playKind == 1) ({ pos, dur -> library.saveProgress(progressId, pos, dur) }) else null,
    )
    LaunchedEffect(parked) { if (parked) { delay(5 * 60_000L); playKind = 0 } }
    val playFull = { kind: Int, id: Int ->
        if (kind == 2) saveLast(id) else playStart = library.resumeMs(id)
        playKind = kind; playId = id; fullscreen = true
    }
    val playInline = { id: Int ->
        // TV Mode: OK on the channel that is already playing opens it fullscreen
        if (tv && playKind == 2 && playId == id) fullscreen = true else { saveLast(id); playKind = 2; playId = id; fullscreen = false }
        Unit
    } // from the Channels page: plays in place
    // Entering the Channels page starts its in-place player (the last channel if it is in the shown group, else that group's
    // first channel) unless one is already there: leaving the page only parks it (see above), it is not stopped.
    LaunchedEffect(tab, state.all.channels) {
        if (tab != 1) return@LaunchedEffect
        if (playKind == 2) return@LaunchedEffect
        val inGroup = state.all.channels.filter { it.group == group }
        (inGroup.firstOrNull { it.id == lastChannel } ?: inGroup.firstOrNull())?.let { playInline(it.id) }
    }
    // A stale id (e.g. the catalogue changed after process death) must never leave the page stuck in "depth" mode.
    LaunchedEffect(movieId, state.all.movies) {
        if (movieId >= 0 && state.all.movies.isNotEmpty() && state.all.movies.none { it.id == movieId }) {
            detail.enter.snapTo(0f)
            movieId = -1
        }
    }
    // TV Mode: which layers a remote can reach. A covered layer must not take D-pad focus (it is still composed underneath).
    // The navigation is the look's own; in TV Mode each of its tabs can take the remote's focus (see [tvTab]).
    val tabReqs = remember { List(Tabs.size) { FocusRequester() } }
    val tvTabs = if (tv) TvTabs(tab, { tab = it }, tabReqs) else null
    // A tap on a tab must move the focus there too, otherwise the highlight stays on the tab the remote started on.
    val onTab = { i: Int -> tab = i; if (tv) runCatching { tabReqs[i].requestFocus() }; Unit }
    var baseFocused by remember { mutableStateOf(false) } // the remote is somewhere in the pages (not on the rail)
    val dialogOpen = showTg || showClear
    val playerFull = session != null && !wantsInPlace
    val lockBase = movieId >= 0 || showMatches || showMovies || playerFull || dialogOpen
    val lockOver = playerFull || dialogOpen
    // TV Mode: "back" from the pages first returns to the navigation (on the current tab); from there it leaves the app as usual.
    // An open settings sub-page closes first.
    BackHandler(enabled = tv && baseFocused && !lockBase && !(tab == 2 && sPage >= 0)) { runCatching { tabReqs[tab].requestFocus() } }
    CompositionLocalProvider(LocalGlassEffects provides fx) {
        BoxWithConstraints(Modifier.fillMaxSize().ambient()) {
            val wide = maxWidth >= 600.dp
            val portrait = maxHeight >= maxWidth
            // Main layer: recedes (scale + dim) while the details page is open or being pulled.
            Box(Modifier.fillMaxSize().graphicsLayer {
                val d = maxOf(detail.depth, matchesP.value)
                val s = if (noDepth) 1f else 1f - 0.06f * d
                scaleX = s; scaleY = s
            }.drawWithContent {
                drawContent()
                // dim with a plain scrim: alpha on the whole tree would force an offscreen layer every frame of the transition
                drawRect(Color.Black, alpha = 0.35f * maxOf(detail.depth, matchesP.value))
            }) {
                Box(
                    Modifier.fillMaxSize()
                        .then(if (tv) Modifier.onFocusChanged { baseFocused = it.hasFocus }.focusGroup() else Modifier)
                ) {
                TvLayer(lockBase, tabReqs[tab]) {
                // Each look moves between its pages its own way (Glass: a plain cross-fade); TV Mode follows the look like the phone.
                AnimatedContent(tab, Modifier.fillMaxSize(), transitionSpec = {
                    val dir = if (targetState > initialState) -1 else 1
                    if (look.anime) { // pop: the new page springs in with an overshoot, the old one flashes away
                        (fadeIn(tween(260)) + scaleIn(tween(460, easing = CubicBezierEasing(0.2f, 1.35f, 0.4f, 1f)), 0.82f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(220), 1.12f))
                    } else if (look.pitch) { // pan: the camera swings across to the next page
                        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -dir * it / 3 } + fadeOut(tween(200)))
                    } else if (look.cosmic) { // warp: the new page rushes in from far away while the old one falls back
                        (fadeIn(tween(420)) + scaleIn(tween(520, easing = FastOutSlowInEasing), 1.18f)) togetherWith
                            (fadeOut(tween(260)) + scaleOut(tween(420), 0.86f))
                    } else fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                }, label = "page") { t ->
                    // each page keeps its remembered state (expanded rows, inner scrolls) while another tab is shown
                    pages.SaveableStateProvider(t) {
                    when (t) {
                        0 -> {
                            val onMovie = { id: Int -> focus.clearFocus(); movieId = id }
                            val onChannel = { id: Int -> playFull(2, id) }
                            val onAllMovies = { focus.clearFocus(); showMovies = true }
                            val onAllChannels = { tab = 1 }
                            val onMatches = { id: Int -> focus.clearFocus(); matchFocus = id; showMatches = true }
                            look.home(HomeArgs(state, homeList, portrait, wide, library, onMovie, onChannel, onAllMovies, onAllChannels, onMatches))
                        }
                        1 -> {
                            val playing = if (playKind == 2) playId else -1
                            ChannelsScreen(state.data.channels, state.query.isNotBlank(), playing, group, portrait, channelsList, { slot = it }, pickGroup, playInline)
                        }
                        else -> SettingsScreen(
                            fx, { fx = it }, theme, settingsScroll, subScroll, sPage, { sPage = it }, wide,
                            library.list.mapNotNull { byId[it] }, { id -> focus.clearFocus(); movieId = id },
                            { openTelegram(ctx) }, { showClear = true },
                        )
                    }
                    }
                }
                // The header belongs to the look (TV Mode too). On an open settings sub-page it carries that page's name and a back button.
                val inSub = tab == 2 && sPage >= 0
                val title = stringResource(if (inSub) settingsTitle(sPage) else if (tab == 0) R.string.app_name else Tabs[tab].second)
                val context = if (tab == 0 && nearChannels) stringResource(R.string.tab_channels) else null
                look.header(
                    HeaderArgs(title, context, tab != 2, state.query, vm::onQuery, progress, reveal, if (inSub) ({ sPage = -1 }) else null),
                    Modifier.align(Alignment.TopCenter),
                )
                }
                }
                // Inside the receding layer so it also steps back when a details page opens over it.
                TvLayer(movieId >= 0 || lockOver) {
                    MoviesHost(showMovies, state.all.movies, portrait, { id -> movieId = id }) { showMovies = false }
                }
            }
            // Overlays are declared in z-order: each one's back handler takes priority over the ones before it.
            TvLayer(lockOver) {
                MatchesHost(showMatches, matchesP, state.all.matches, wide, matchFocus) { showMatches = false }
                MovieDetailHost(detail, state.all.movies, library, movieId, { movieId = it }, { id -> playFull(1, id) }, { movieId = -1 })
            }
            // The app's single player. Fullscreen = the whole screen; otherwise it is laid exactly over the Channels page's slot,
            // so switching between the two never rebuilds it (the stream keeps playing). Its engine lives in [session], so a
            // parked player (another tab is shown) keeps its engine and simply is not drawn.
            // An in-place player is drawn only while the Channels page (and its slot) is on screen. Without this guard, the frame in
            // which the tab had already changed (or the slot was not measured yet) drew it as FULLSCREEN, which forces landscape
            // for a moment: that was the page "flipping" when entering or leaving Channels.
            val r = if (tab == 1) slot else null
            val inPlace = wantsInPlace && r != null
            if (session != null && (!wantsInPlace || inPlace)) {
                val box = if (r != null && inPlace) with(LocalDensity.current) {
                    // The slot's rect is in absolute (physical) root coordinates. Both the alignment and the offset must be absolute
                    // too: in RTL a plain TopStart puts the box at the RIGHT edge first and the offset is then added on top of that,
                    // which is what pushed the player to the right of its slot (portrait: a few dp, landscape: out of place entirely).
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
            // The one nav island, drawn after the shared player so it floats above it (in-place, portrait or landscape).
            // Whatever covers the whole screen (details, matches, "all" pages, fullscreen player) fades it out and takes its
            // size to 0, so it never intercepts touches; it is never recreated, only hidden.
            val covered = showMovies || (session != null && !wantsInPlace)
            val navShow by animateFloatAsState(if (covered) 0f else 1f, tween(160), label = "nav")
            val navAlpha = { navShow * (1f - maxOf(detail.depth, matchesP.value)) }
            // The look's own navigation, faded / collapsed the same way for every look.
            val navMod = Modifier
                .layout { m, c ->
                    val pl = m.measure(c)
                    if (navAlpha() < 0.02f) layout(0, 0) {} else layout(pl.width, pl.height) { pl.place(0, 0) }
                }
                .graphicsLayer { alpha = navAlpha() }
            // (not a TvLayer: the navigation must not compete with the pages for the focus that is given back when a layer is released)
            CompositionLocalProvider(LocalTvLock provides lockBase, LocalTvTabs provides tvTabs) {
                look.nav(tab, onTab, navMod.align(Alignment.BottomCenter))
            }
            if (isAppOutdated) {
                ForceUpdateDialog(config)
            } else {
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
}

private val Tabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)
private val NavSpring = spring<Float>(0.62f, 380f)

/**
 * Phone Feed's bottom bar: solid, full width, hairline on top. One accent line with a soft glow slides (spring) to the current tab,
 * instead of a marker that appears on each tab separately.
 */
@Composable
internal fun FeedBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pos by animateFloatAsState(selected.toFloat(), spring(0.8f, 380f), label = "feedLine")
    Row(
        modifier.fillMaxWidth().background(c.bg).drawBehind {
            val w = size.width / Tabs.size
            val cx = (if (rtl) Tabs.size - (pos + 0.5f) else pos + 0.5f) * w
            val glow = 64.dp.toPx()
            drawRect(c.border, size = Size(size.width, 1.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.30f), c.accent.copy(alpha = 0f)), Offset(cx, 0f), glow), glow, Offset(cx, 0f))
            drawRoundRect(c.accent, Offset(cx - 14.dp.toPx(), 0f), Size(28.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
        }.navigationBarsPadding().height(58.dp),
    ) {
        Tabs.forEachIndexed { i, (icon, label) ->
            val on by animateFloatAsState(if (i == selected) 1f else 0f, tween(220), label = "feedTab")
            Column(Modifier.weight(1f).fillMaxHeight().tvTab(i).press { onSelect(i) }, Arrangement.Center, Alignment.CenterHorizontally) {
                OneIconView(icon, Modifier.graphicsLayer { val k = 1f + 0.1f * on; scaleX = k; scaleY = k }) { lerp(c.dim, c.accent, on) }
                OneText(stringResource(label), OneType.Caption, lerp(c.dim, c.accent, on), Modifier.padding(top = 2.dp), 1)
            }
        }
    }
}

/** Floating glass island. The indicator is a blob drawn per frame only while it moves (stretch/squash by velocity). */
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
            val v = pos.velocity * (if (rtl) -1f else 1f)            // screen-space items/s
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
