package com.oneplus.app.ui.system

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.StringRes
import com.oneplus.app.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val WeakDevice: Boolean = Runtime.getRuntime().maxMemory() <= 192L * 1024 * 1024 || Runtime.getRuntime().availableProcessors() <= 2 || android.os.Build.VERSION.SDK_INT < 24

val HighDevice: Boolean = !WeakDevice && Runtime.getRuntime().maxMemory() >= 320L * 1024 * 1024 && Runtime.getRuntime().availableProcessors() >= 6 && android.os.Build.VERSION.SDK_INT >= 28

val LocalTvMode = staticCompositionLocalOf { false }

val LocalTvLock = compositionLocalOf { false }

enum class DisplayMode(@StringRes val label: Int) {
    Auto(R.string.display_auto), Phone(R.string.display_phone), Tv(R.string.display_tv)
}

@Composable
fun DisplayMode.resolveTv(): Boolean {
    val ctx = LocalContext.current
    val detected = remember(ctx) { looksLikeTv(ctx) }
    return when (this) {
        DisplayMode.Auto -> detected
        DisplayMode.Phone -> false
        DisplayMode.Tv -> true
    }
}

fun looksLikeTv(ctx: Context): Boolean = runCatching {
    val pm = ctx.packageManager
    val cfg = ctx.resources.configuration
    val ui = (ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    if (ui == Configuration.UI_MODE_TYPE_TELEVISION || (cfg.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) return@runCatching true
    if (listOf(PackageManager.FEATURE_LEANBACK, "android.hardware.type.television", "amazon.hardware.fire_tv").any { pm.hasSystemFeature(it) }) return@runCatching true
    val noTouch = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) || cfg.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH
    val notATv = listOf("android.hardware.type.pc", "android.hardware.type.automotive", "android.hardware.type.watch", "org.chromium.arc").any { pm.hasSystemFeature(it) }
    noTouch && !notATv
}.getOrDefault(false)

private const val CanvasW = 854f
private const val CanvasH = 480f
private const val MinScale = 0.75f
private const val MaxScale = 5f

fun tvScale(widthDp: Float, heightDp: Float): Float = minOf(widthDp / CanvasW, heightDp / CanvasH).coerceIn(MinScale, MaxScale)

@Composable
fun TvScreen(tv: Boolean, content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val s = if (tv) tvScale(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat()) else 1f
    val activity = LocalContext.current.findActivity()
    DisposableEffect(tv, activity) {
        if (tv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (tv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    CompositionLocalProvider(
        LocalTvMode provides tv,
        LocalDensity provides Density(d.density * s, d.fontScale),
        content = content,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvFocusRing(src: InteractionSource): Modifier {
    if (!LocalTvMode.current) return this
    val focused by src.collectIsFocusedAsState()
    val a by animateFloatAsState(if (focused) 1f else 0f, spring(0.75f, 500f), label = "tvFocus")
    val glow = LocalColors.current.accent
    val me = remember { FocusRequester() }
    val memory = LocalTvMemory.current
    LaunchedEffect(focused) { if (focused) memory?.last = me }
    val bring = remember { BringIntoViewRequester() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(focused) {
        if (focused && box != IntSize.Zero) bring.bringIntoView(Rect(-margin, -margin, box.width + margin, box.height + margin))
    }
    return focusRequester(me).bringIntoViewRequester(bring).onSizeChanged { box = it }
        .graphicsLayer { val k = 1f + 0.06f * a; scaleX = k; scaleY = k }
        .drawBehind { if (a > 0.01f) focusGlow(glow, a) }
}

private fun DrawScope.focusGlow(color: Color, a: Float) {
    val r = (minOf(size.width, size.height) / 2f).coerceAtMost(22.dp.toPx())
    val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    clipPath(hole, ClipOp.Difference) {
        val layers = if (HighDevice) 7 else 3
        for (i in 1..layers) {
            val grow = i * (14f / layers).dp.toPx()
            drawRoundRect(
                color.copy(alpha = 0.17f * a * (1f - (i - 1f) / layers) * (7f / layers).coerceAtMost(1.6f)),
                Offset(-grow, -grow), Size(size.width + grow * 2f, size.height + grow * 2f), CornerRadius(r + grow),
            )
        }
    }
}

@Composable
fun Modifier.tvLockable(): Modifier {
    if (!LocalTvMode.current) return this
    val locked = LocalTvLock.current
    return focusProperties { canFocus = !locked }
}

class TvFocusMemory { var last: FocusRequester? = null }
val LocalTvMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

@Composable
fun TvLayer(locked: Boolean, fallback: FocusRequester? = null, content: @Composable () -> Unit) {
    val tv = LocalTvMode.current
    val memory = remember { TvFocusMemory() }
    if (tv) LaunchedEffect(locked) {
        if (!locked) {
            delay(120)
            val back = memory.last
            val ok = back != null && runCatching { back.requestFocus() }.isSuccess
            if (!ok && fallback != null) runCatching { fallback.requestFocus() }
        }
    }
    CompositionLocalProvider(LocalTvLock provides locked, LocalTvMemory provides memory, content = content)
}

@Composable
fun Modifier.tvAutoFocus(key: Any? = Unit): Modifier {
    if (!LocalTvMode.current) return this
    val me = remember { FocusRequester() }
    LaunchedEffect(key) { delay(150); runCatching { me.requestFocus() } }
    return focusRequester(me)
}

@Composable
fun bottomNavSpace(): Dp = if (LocalTvMode.current) 24.dp else LocalLook.current.bottom

val LocalTvNavHeight = compositionLocalOf { 0.dp }

private const val TvNavScale = 0.8f

fun Modifier.tvNavShrink(): Modifier = layout { m, c ->
    val p = m.measure(c)
    layout((p.width * TvNavScale).roundToInt(), (p.height * TvNavScale).roundToInt()) {
        p.placeWithLayer(0, 0) { scaleX = TvNavScale; scaleY = TvNavScale; transformOrigin = TransformOrigin(0f, 0f) }
    }
}

@Composable
fun screenWidth(): Dp {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp.toFloat()
    return (if (LocalTvMode.current) w / tvScale(w, cfg.screenHeightDp.toFloat()) else w).dp
}

class TvTabs(val selected: Int, val select: (Int) -> Unit, val reqs: List<FocusRequester>)
val LocalTvTabs = staticCompositionLocalOf<TvTabs?> { null }

@Composable
fun Modifier.tvTab(i: Int): Modifier {
    val t = LocalTvTabs.current ?: return this
    var focused by remember { mutableStateOf(false) }
    val sel by rememberUpdatedState(t.selected)
    LaunchedEffect(focused) { if (focused) { delay(350); if (sel != i) t.select(i) } }
    return focusRequester(t.reqs[i]).onFocusChanged { focused = it.isFocused }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
