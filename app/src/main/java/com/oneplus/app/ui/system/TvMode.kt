package com.oneplus.app.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * TV Mode is a property of the SCREEN UI, not of the device: whatever the hardware is (phone, tablet, Android TV, box),
 * when the user turns it on the app is drawn as a TV screen. Nothing here looks at the device type, its model or its resolution;
 * only the size of the window the app actually has.
 */
val LocalTvMode = staticCompositionLocalOf { false }

/** True for a layer that is covered by another one (details page, dialog...): it must not take D-pad focus. */
val LocalTvLock = compositionLocalOf { false }

// The TV canvas: a 16:9 screen of this many dp. Not a device size: it is the unit the window is measured against, so that
// any window (any size, any aspect ratio, split screen) is mapped onto the same layout language.
private const val CanvasW = 854f
private const val CanvasH = 480f
private const val MinScale = 0.75f
private const val MaxScale = 3f

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
 * Focus indicator for D-pad / remote: the element grows a little and gets a clear ring (the theme's text colour, so it reads on
 * the accent buttons too). Touch never focuses these elements, so it is only ever seen with a remote or keyboard.
 * Phone Mode: returns the modifier untouched.
 */
@Composable
fun Modifier.tvFocusRing(src: InteractionSource): Modifier {
    if (!LocalTvMode.current) return this
    val focused by src.collectIsFocusedAsState()
    val a by animateFloatAsState(if (focused) 1f else 0f, tween(140), label = "tvFocus")
    val ring = LocalColors.current.text
    return graphicsLayer { val k = 1f + 0.05f * a; scaleX = k; scaleY = k }.drawWithContent {
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
