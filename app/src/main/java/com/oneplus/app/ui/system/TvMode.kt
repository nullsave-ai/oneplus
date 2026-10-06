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

/**
 * TV Mode is a property of the SCREEN UI, not of the device: whatever the hardware is (phone, tablet, Android TV, box),
 * when the user turns it on the app is drawn as a TV screen. Nothing here looks at the device type, its model or its resolution;
 * only the size of the window the app actually has.
 */
val LocalTvMode = staticCompositionLocalOf { false }

/** True for a layer that is covered by another one (details page, dialog...): it must not take D-pad focus. */
val LocalTvLock = compositionLocalOf { false }

/** Which UI the user wants. [Auto] = whatever [looksLikeTv] finds on this device; the other two are an explicit choice. */
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

/**
 * Does this device present itself as a TV? Only used to pick the starting choice ([DisplayMode.Auto]); the UI itself is always
 * decided by TV Mode, never by this. It asks the system in three ways so brands that are not known by name are still found:
 * the UI mode (Android TV / Google TV), the TV feature flags (leanback, Fire TV), and, for boxes and sticks that declare
 * nothing, a device with no touchscreen that is not a PC, a car or a watch.
 */
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

// The TV canvas: a 16:9 screen of this many dp. Not a device size: it is the unit the window is measured against, so that
// any window (any size, any aspect ratio, split screen) is mapped onto the same layout language.
private const val CanvasW = 854f
private const val CanvasH = 480f
private const val MinScale = 0.75f
private const val MaxScale = 5f   // a 4K panel reported at 160dpi is 3840dp wide: still has to land on the same canvas

/**
 * How much bigger than "normal" one dp becomes in TV Mode: the largest scale at which the whole canvas still fits the window.
 * The window then sees at least a full canvas: a wider window (21:9) simply has more room, so grids get more columns; a taller
 * one (4:3) gets more rows. Everything the app measures in dp or sp (cards, grids, spacing, padding, text, toolbar) follows.
 */
fun tvScale(widthDp: Float, heightDp: Float): Float = minOf(widthDp / CanvasW, heightDp / CanvasH).coerceIn(MinScale, MaxScale)

/**
 * Root of the app. With [tv] off it is invisible (density untouched, nothing locked): Phone Mode is exactly what it was.
 * With [tv] on it (1) rescales the density to the window, (2) holds the screen landscape like a TV, and (3) tells the
 * components to show D-pad focus. The same composition is used in both cases, so switching mode keeps the user where they are.
 */
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

/**
 * Focus indicator for D-pad / remote: the element lifts a little and a soft glow in the app colour spreads around it. There is no
 * outline, so it never draws a rectangle over a rounded or irregular shape; the glow is painted only outside the element, so it
 * never tints what is inside. Moving the remote onto an element also scrolls whatever holds it so that the element AND a margin of
 * its neighbours are in view (every step reveals a little more of the row or page). It also tells the layer which element was
 * focused last ([TvLayer]). Touch never focuses these elements. Phone Mode: returns the modifier untouched.
 * Put it OUTSIDE any clip of the element, or the glow is cut off.
 */
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

/** Soft halo outside an element's own outline: stacked, ever larger and fainter rounded rectangles with the element cut out. */
private fun DrawScope.focusGlow(color: Color, a: Float) {
    val r = (minOf(size.width, size.height) / 2f).coerceAtMost(22.dp.toPx())
    val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    clipPath(hole, ClipOp.Difference) {
        val layers = 7
        for (i in 1..layers) {
            val grow = i * 2.dp.toPx()
            drawRoundRect(
                color.copy(alpha = 0.17f * a * (1f - (i - 1f) / layers)),
                Offset(-grow, -grow), Size(size.width + grow * 2f, size.height + grow * 2f), CornerRadius(r + grow),
            )
        }
    }
}

/** Put on a focusable: while its layer is covered ([LocalTvLock]) it cannot be reached with the D-pad. No-op in Phone Mode. */
@Composable
fun Modifier.tvLockable(): Modifier {
    if (!LocalTvMode.current) return this
    val locked = LocalTvLock.current
    return focusProperties { canFocus = !locked }
}

/** The last element a layer had under the remote, so closing whatever covered the layer puts the remote back where it was. */
class TvFocusMemory { var last: FocusRequester? = null }
val LocalTvMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

/**
 * One screen layer (the pages, the movies list, the details page, the player...). While [locked] (covered by another layer)
 * its elements cannot be reached with the remote. When it is released the remote returns to the element it had, or to [fallback]
 * (this is also what gives the app its first focus at launch). Phone Mode: just a pass-through.
 */
@Composable
fun TvLayer(locked: Boolean, fallback: FocusRequester? = null, content: @Composable () -> Unit) {
    val tv = LocalTvMode.current
    val memory = remember { TvFocusMemory() }
    if (tv) LaunchedEffect(locked) {
        if (!locked) {
            delay(120) // let the layer that just left finish, so the focus tree is settled
            val back = memory.last
            val ok = back != null && runCatching { back.requestFocus() }.isSuccess // fails when that element is gone (scrolled away / closed)
            if (!ok && fallback != null) runCatching { fallback.requestFocus() }
        }
    }
    CompositionLocalProvider(LocalTvLock provides locked, LocalTvMemory provides memory, content = content)
}

/**
 * The element takes the remote's focus when it appears (and again when [key] changes): a page opened from the remote must
 * not leave it on nothing. Place it BEFORE the element's press / clickable. Phone Mode: untouched.
 */
@Composable
fun Modifier.tvAutoFocus(key: Any? = Unit): Modifier {
    if (!LocalTvMode.current) return this
    val me = remember { FocusRequester() }
    LaunchedEffect(key) { delay(150); runCatching { me.requestFocus() } }
    return focusRequester(me)
}

/** Space a page keeps clear below its content for the bottom navigation: the look's own bar, almost none in TV Mode (its navigation is at the top). */
@Composable
fun bottomNavSpace(): Dp = if (LocalTvMode.current) 24.dp else LocalLook.current.bottom

/** Height the look's navigation takes at the top of the screen in TV Mode (measured; 0 in Phone Mode). Pages start below it. */
val LocalTvNavHeight = compositionLocalOf { 0.dp }

private const val TvNavScale = 0.8f

/** TV Mode: the look's own navigation, a little smaller, as one block (its layout size shrinks with it, so nothing is left empty). */
fun Modifier.tvNavShrink(): Modifier = layout { m, c ->
    val p = m.measure(c)
    layout((p.width * TvNavScale).roundToInt(), (p.height * TvNavScale).roundToInt()) {
        p.placeWithLayer(0, 0) { scaleX = TvNavScale; scaleY = TvNavScale; transformOrigin = TransformOrigin(0f, 0f) }
    }
}

/**
 * Width of the window in the units the layout is drawn in. In TV Mode the density is rescaled ([TvScreen]) but
 * LocalConfiguration is not, so `screenWidthDp` would be too big there: this divides the scale back out.
 */
@Composable
fun screenWidth(): Dp {
    val cfg = LocalConfiguration.current
    val w = cfg.screenWidthDp.toFloat()
    return (if (LocalTvMode.current) w / tvScale(w, cfg.screenHeightDp.toFloat()) else w).dp
}

/**
 * The navigation of the current look, seen by the remote. Every look draws its own bar; each tab only adds [tvTab], so TV Mode
 * needs no navigation of its own. [reqs] lets the page put the focus on a tab (a tap, or "back" from a page); landing on a tab
 * with the D-pad opens it after a short pause. Null in Phone Mode: [tvTab] is then a no-op.
 */
class TvTabs(val selected: Int, val select: (Int) -> Unit, val reqs: List<FocusRequester>)
val LocalTvTabs = staticCompositionLocalOf<TvTabs?> { null }

/** Put on a navigation tab BEFORE its press / clickable. */
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
