package com.oneplus.app.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneplus.app.R
import com.oneplus.app.ui.system.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OnePlusApp(theme: ThemeController, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    // Low-RAM devices start with the light glass tier.
    var fx by rememberSaveable { mutableStateOf(!(ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var movieId by rememberSaveable { mutableIntStateOf(-1) }
    val detail = remember { DetailState(movieId >= 0) }
    val homeList = rememberLazyListState()
    val grid = rememberLazyGridState()
    val settingsScroll = rememberScrollState()
    val range = with(LocalDensity.current) { 96.dp.toPx() }
    val progress = rememberToolbarProgress {
        when (tab) {
            0 -> scrollTarget(homeList.firstVisibleItemIndex, homeList.firstVisibleItemScrollOffset, range)
            1 -> scrollTarget(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset, range)
            else -> scrollTarget(0, settingsScroll.value, range)
        }
    }
    val nearChannels by remember {
        derivedStateOf {
            val info = homeList.layoutInfo
            homeList.canScrollBackward && info.visibleItemsInfo.any { it.key == "channels" && it.offset < info.viewportSize.height / 2 }
        }
    }
    // A stale id (e.g. the catalogue changed after process death) must never leave the page stuck in "depth" mode.
    LaunchedEffect(movieId, state.all.movies) {
        if (movieId >= 0 && state.all.movies.isNotEmpty() && state.all.movies.none { it.id == movieId }) {
            detail.enter.snapTo(0f)
            movieId = -1
        }
    }
    CompositionLocalProvider(LocalGlassEffects provides fx) {
        BoxWithConstraints(Modifier.fillMaxSize().ambient()) {
            val wide = maxWidth >= 600.dp
            val portrait = maxHeight >= maxWidth
            // Main layer: recedes (scale + dim) while the details page is open or being pulled.
            Box(Modifier.fillMaxSize().graphicsLayer {
                val d = detail.depth
                val s = 1f - 0.06f * d
                scaleX = s; scaleY = s
                alpha = 1f - 0.35f * d
            }) {
                Crossfade(tab, Modifier.fillMaxSize(), tween(180), label = "page") { t ->
                    when (t) {
                        0 -> HomeScreen(state, homeList, wide, portrait) { id -> focus.clearFocus(); movieId = id }
                        1 -> ChannelsScreen(state.data.channels, grid, portrait)
                        else -> SettingsScreen(fx, { fx = it }, theme, settingsScroll)
                    }
                }
                FloatingToolbar(
                    title = stringResource(if (tab == 0) R.string.app_name else Tabs[tab].second),
                    context = if (tab == 0 && nearChannels) stringResource(R.string.tab_channels) else null,
                    hasSearch = tab != 2, query = state.query, onQuery = vm::onQuery, progress = progress,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 880.dp).statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                LiquidNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
            }
            // Declared after the toolbar so its back handler takes priority over search-close.
            MovieDetailHost(detail, state.all.movies, movieId, { movieId = it }, { movieId = -1 })
        }
    }
}

private val Tabs = listOf(OneIcon.Home to R.string.tab_home, OneIcon.Channels to R.string.tab_channels, OneIcon.Settings to R.string.tab_settings)
private val NavSpring = spring<Float>(0.62f, 380f)

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
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
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
