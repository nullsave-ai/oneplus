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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp

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
 * Focus indicator for D-pad / remote: a clear ring (the theme's text colour, so it reads on the accent buttons too) and a faint
 * tint. Drawn inside the element's own bounds, so no scrolling list ever clips it. Touch never focuses these elements, so it is
 * only ever seen with a remote, gamepad or keyboard. It also tells the layer which element was focused last ([TvLayer]).
 * Phone Mode: returns the modifier untouched.
 */
@Composable
fun Modifier.tvFocusRing(src: InteractionSource): Modifier {
    if (!LocalTvMode.current) return this
    val focused by src.collectIsFocusedAsState()
    val a by animateFloatAsState(if (focused) 1f else 0f, tween(140), label = "tvFocus")
    val ring = LocalColors.current.text
    val me = remember { FocusRequester() }
    val memory = LocalTvMemory.current
    LaunchedEffect(focused) { if (focused) memory?.last = me }
    return focusRequester(me).drawWithContent {
        drawContent()
        if (a > 0.01f) {
            val sw = 3.dp.toPx()
            val topLeft = Offset(sw / 2f, sw / 2f)
            val box = Size(size.width - sw, size.height - sw)
            val r = CornerRadius((minOf(size.width, size.height) / 2f).coerceAtMost(20.dp.toPx()) - sw / 2f)
            drawRoundRect(ring.copy(alpha = 0.10f * a), topLeft, box, r)
            drawRoundRect(ring.copy(alpha = 0.95f * a), topLeft, box, r, Stroke(sw))
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

/** Space a page keeps clear below its content for the phone's bottom navigation island. TV Mode has none (its rail is at the side). */
@Composable
fun bottomNavSpace(): Dp = if (LocalTvMode.current) 24.dp else 112.dp

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
