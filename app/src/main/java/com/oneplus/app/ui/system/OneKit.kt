package com.oneplus.app.ui.system

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- Tokens -------------------------------------------------------------
val LocalGlassEffects = staticCompositionLocalOf { true }

object Sp { val S4 = 4.dp; val S8 = 8.dp; val S12 = 12.dp; val S16 = 16.dp; val S20 = 20.dp; val S24 = 24.dp; val S32 = 32.dp }

object OneType {
    val Display = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val Section = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp)
    val Caption = TextStyle(fontSize = 12.sp)
    /** Editorial headline of the phone Feed (serif; no letter-spacing, which would break Arabic joins). */
    val Lux = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
    val LuxHero = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, lineHeight = 38.sp)
}

@Composable
fun OneText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style.copy(color = color), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

// ---- Background / Glass / Press ----------------------------------------
@Composable
fun Modifier.ambient(): Modifier {
    val c = LocalColors.current
    if (LocalFeed.current) return background(c.bg) // Feed: one flat colour, no glow, no dither
    if (LocalLook.current.cosmic) return background(c.bg).drawWithCache { // Orbit: a nebula and a fixed star field
        val night = c.bg.luminance() < 0.2f
        val glowA = Brush.radialGradient(listOf(c.accent.copy(alpha = if (night) 0.20f else 0.10f), c.accent.copy(alpha = 0f)),
            Offset(size.width * 0.9f, 0f), size.maxDimension * 0.75f)
        val glowB = Brush.radialGradient(listOf(NebulaViolet.copy(alpha = if (night) 0.16f else 0.08f), NebulaViolet.copy(alpha = 0f)),
            Offset(0f, size.height * 0.95f), size.maxDimension * 0.7f)
        val rnd = kotlin.random.Random(11)
        val stars = if (night) List(70) { Triple(Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), 0.5f + rnd.nextFloat() * 1.1f, 0.15f + rnd.nextFloat() * 0.55f) } else emptyList()
        onDrawBehind {
            drawRect(glowA); drawRect(glowB)
            stars.forEach { (o, r, a) -> drawCircle(c.text.copy(alpha = a), r.dp.toPx(), o) }
        }
    }
    return background(c.bg).drawWithCache {
        // Fade to the same colour at alpha 0: fading to Color.Transparent (= transparent BLACK) is interpolated through gray
        // and leaves a dirty halo, most visible in day mode.
        val b = Brush.radialGradient(listOf(c.ambient.copy(alpha = 0.16f), c.ambient.copy(alpha = 0f)),
            Offset(size.width * 0.85f, 0f), size.maxDimension * 0.7f)
        onDrawBehind { drawRect(b); if (c.bg != Color.Black) drawDither() } // pure black (Amoled) must stay pure: dither would light the pixels
    }
}

private val GlassAlpha = floatArrayOf(0.40f, 0.55f, 0.70f, 0.88f) // Glass 1..4
internal val Sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f))) // same hue at both ends

/**
 * Translucent tint + sheen + hairline border. Real backdrop blur is not applied (see README note).
 * The user's [GlassStyle] scales it: density = how solid the tint is; depth = how strong the sheen and the edge are, and above 1
 * the surface also lifts off the page with a soft shadow. At the default (1, 1) nothing is added or changed.
 */
@Composable
fun Modifier.glass(level: Int, radius: Dp): Modifier {
    val c = LocalColors.current
    if (LocalFeed.current) { // Feed: a flat solid card (phone: hairline border)
        if (!LocalLux.current) return clip(RoundedCornerShape(minOf(radius, 14.dp))).background(c.glass)
        val s = RoundedCornerShape(radius)
        return clip(s).background(c.glass).border(0.5.dp, c.border, s)
    }
    val fx = LocalGlassEffects.current
    val g = LocalGlassStyle.current
    val shape = RoundedCornerShape(radius)
    if (LocalLook.current.sheet) { // Strata: an opaque sheet; each level a touch lighter than the one under it, lifted by a soft shadow (depth scales it)
        val lift = if (fx) drawBehind { softShadow(CornerRadius(radius.toPx()), 2f * g.depth) } else this
        return lift.clip(shape).background(lerp(c.glass, c.text, 0.015f * level)).border(0.5.dp, c.border, shape)
    }
    val sheen = remember(g.depth) { if (g.depth == 1f) Sheen else Brush.verticalGradient(listOf(Color.White.copy(alpha = (0.10f * g.depth).coerceIn(0f, 1f)), Color.White.copy(alpha = 0f))) }
    val edge = if (g.depth == 1f) c.border else c.border.copy(alpha = (c.border.alpha * g.depth).coerceIn(0f, 1f))
    val tint = ((if (fx) GlassAlpha[level - 1] else 0.94f) * g.density).coerceIn(0f, 1f)
    val lift = if (fx && g.depth > 1f) drawBehind { softShadow(CornerRadius(radius.toPx()), (g.depth - 1f) * 3f) } else this
    val base = lift.clip(shape).background(c.glass.copy(alpha = tint))
    val face = if (fx) base.background(sheen) else base
    if (LocalLook.current.cosmic) { // Orbit: the edge is a lit rim, brighter where the light hits
        val rim = remember(c.accent) { Brush.linearGradient(listOf(c.accent.copy(alpha = 0.75f), c.accent.copy(alpha = 0.08f), NebulaViolet.copy(alpha = 0.45f))) }
        return face.border(1.dp, rim, shape)
    }
    return face.border(0.5.dp, edge, shape)
}

private val NebulaViolet = Color(0xFFB36BFF)

/** Press physics (no ripple): scale down, spring back. */
@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val feed = LocalFeed.current // Feed: a press dims the item instead of squeezing it
    val s by animateFloatAsState(if (pressed && !feed) 0.96f else 1f, spring(0.6f, 500f), label = "press")
    val dim by animateFloatAsState(if (pressed && feed) 0.6f else 1f, tween(90), label = "pressDim")
    val tv = LocalTvMode.current
    val click by rememberUpdatedState(onClick)
    return graphicsLayer { scaleX = s; scaleY = s; alpha = dim }.tvFocusRing(src).tvLockable()
        // a gamepad's A button is "OK" too (the remote's centre / Enter is already handled by clickable)
        .then(if (tv) Modifier.onKeyEvent { e -> (e.key == Key.ButtonA).also { if (it && e.type == KeyEventType.KeyUp) click() } } else Modifier)
        .clickable(src, null, onClick = onClick)
}

/**
 * Entrance for a feed section: it fades up once, staggered by [index]. [seen] remembers which ones already played, so a section
 * that scrolls out and back in does not play again (the set dies with the page, so coming back to the tab plays it anew).
 */
@Composable
fun Modifier.reveal(index: Int, seen: MutableSet<Int>): Modifier {
    val done = index in seen
    val p = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(Unit) { if (!done) { delay(index * 80L); p.animateTo(1f, tween(560, easing = FastOutSlowInEasing)); seen += index } }
    return graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 32.dp.toPx() }
}

/**
 * A card sliding off either end of its row shrinks and fades a little, so the row seems to curve away from the finger.
 * [key] is the item's key in [state]. Reads the row's layout in the draw phase only: scrolling never recomposes.
 */
fun Modifier.edgeFx(state: LazyListState, key: Any): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val out = maxOf(info.viewportStartOffset - item.offset, item.offset + item.size - info.viewportEndOffset).coerceAtLeast(0) / item.size.toFloat()
    val f = ((out - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val k = 1f - 0.14f * f
    scaleX = k; scaleY = k; alpha = 1f - 0.45f * f
}

// ---- Icons (custom, 24dp grid, 1.75 stroke) ------------------------------
enum class OneIcon { Home, Channels, Settings, Search, Close, Back, Next, Play, Pause, Plus, Check, Star, Replay, Forward, Sun, Volume, Mute, Fit, Fill, Expand, Shrink, Filter, Cc, Wave, Send }

@Composable
fun OneIconView(icon: OneIcon, modifier: Modifier = Modifier, tint: () -> Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(24.dp)) {
        val k = size.width / 24f
        val color = tint()
        val st = Stroke(1.75f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, o(x1, y1), o(x2, y2), st.width, StrokeCap.Round)
        when (icon) {
            OneIcon.Home -> {
                drawPath(Path().apply { moveTo(5 * k, 10.5f * k); lineTo(12 * k, 4.5f * k); lineTo(19 * k, 10.5f * k)
                    lineTo(19 * k, 19 * k); lineTo(5 * k, 19 * k); close() }, color, style = st)
                drawPath(Path().apply { moveTo(10 * k, 19 * k); lineTo(10 * k, 14 * k); lineTo(14 * k, 14 * k); lineTo(14 * k, 19 * k) }, color, style = st)
            }
            OneIcon.Channels -> {
                drawRoundRect(color, o(3f, 7f), Size(18 * k, 12 * k), CornerRadius(3.5f * k), st)
                drawPath(Path().apply { moveTo(9 * k, 3.5f * k); lineTo(12 * k, 7 * k); lineTo(15 * k, 3.5f * k) }, color, style = st)
            }
            OneIcon.Settings -> {
                line(4f, 8f, 6.6f, 8f); line(11.4f, 8f, 20f, 8f); line(4f, 16f, 12.6f, 16f); line(17.4f, 16f, 20f, 16f)
                drawCircle(color, 2.4f * k, o(9f, 8f), style = st); drawCircle(color, 2.4f * k, o(15f, 16f), style = st)
            }
            OneIcon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            OneIcon.Search -> { drawCircle(color, 6f * k, o(11f, 11f), style = st); line(15.5f, 15.5f, 20f, 20f) }
            OneIcon.Back -> { // chevron pointing "back" for the current layout direction
                fun mx(x: Float) = (if (rtl) 24f - x else x) * k
                drawPath(Path().apply { moveTo(mx(15f), 5.5f * k); lineTo(mx(8.5f), 12f * k); lineTo(mx(15f), 18.5f * k) }, color, style = st)
            }
            OneIcon.Next -> { // chevron pointing "forward" for the current layout direction
                fun mx(x: Float) = (if (rtl) x else 24f - x) * k
                drawPath(Path().apply { moveTo(mx(15f), 5.5f * k); lineTo(mx(8.5f), 12f * k); lineTo(mx(15f), 18.5f * k) }, color, style = st)
            }
            OneIcon.Pause -> {
                drawRoundRect(color, o(6.6f, 5.5f), Size(3.8f * k, 13f * k), CornerRadius(1.4f * k))
                drawRoundRect(color, o(13.6f, 5.5f), Size(3.8f * k, 13f * k), CornerRadius(1.4f * k))
            }
            OneIcon.Replay, OneIcon.Forward -> { // open circle arrow (counter-clockwise / clockwise)
                val mirror = icon == OneIcon.Replay
                fun px(x: Float) = (if (mirror) 24f - x else x) * k
                val cx = 12f; val cy = 12.8f; val r = 7.6f
                val arc = Path()
                var a = 315f
                while (a <= 585f) {
                    val rad = Math.toRadians(a.toDouble())
                    val x = cx + r * cos(rad).toFloat(); val y = cy + r * sin(rad).toFloat()
                    if (a == 315f) arc.moveTo(px(x), y * k) else arc.lineTo(px(x), y * k)
                    a += 15f
                }
                drawPath(arc, color, style = st)
                val ae = Math.toRadians(225.0)
                val tipX = cx + r * cos(ae).toFloat(); val tipY = cy + r * sin(ae).toFloat()
                val tx = -sin(ae).toFloat(); val ty = cos(ae).toFloat() // clockwise tangent at the tip
                val nx = cos(ae).toFloat(); val ny = sin(ae).toFloat()  // radial direction
                val d = 3.4f
                drawPath(Path().apply {
                    moveTo(px(tipX - tx * d + nx * d), (tipY - ty * d + ny * d) * k)
                    lineTo(px(tipX), tipY * k)
                    lineTo(px(tipX - tx * d - nx * d), (tipY - ty * d - ny * d) * k)
                }, color, style = st)
            }
            OneIcon.Sun -> {
                drawCircle(color, 3.4f * k, o(12f, 12f), style = st)
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    val c1 = cos(rad).toFloat(); val s1 = sin(rad).toFloat()
                    line(12f + 6.5f * c1, 12f + 6.5f * s1, 12f + 8.8f * c1, 12f + 8.8f * s1)
                }
            }
            OneIcon.Volume, OneIcon.Mute -> {
                drawPath(Path().apply {
                    moveTo(4f * k, 9.5f * k); lineTo(8f * k, 9.5f * k); lineTo(12.5f * k, 5.8f * k)
                    lineTo(12.5f * k, 18.2f * k); lineTo(8f * k, 14.5f * k); lineTo(4f * k, 14.5f * k); close()
                }, color, style = st)
                if (icon == OneIcon.Volume) {
                    drawArc(color, -48f, 96f, false, o(8.3f, 7.8f), Size(8.4f * k, 8.4f * k), style = st)
                    drawArc(color, -48f, 96f, false, o(4.9f, 4.4f), Size(15.2f * k, 15.2f * k), style = st)
                } else { line(15.5f, 9.5f, 20.5f, 14.5f); line(20.5f, 9.5f, 15.5f, 14.5f) }
            }
            OneIcon.Fit -> drawRoundRect(color, o(4f, 7f), Size(16f * k, 10f * k), CornerRadius(2.5f * k), st)
            OneIcon.Fill -> drawRoundRect(color, o(4f, 7f), Size(16f * k, 10f * k), CornerRadius(2.5f * k))
            OneIcon.Expand, OneIcon.Shrink -> {
                fun corner(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
                    drawPath(Path().apply { moveTo(x1 * k, y1 * k); lineTo(x2 * k, y2 * k); lineTo(x3 * k, y3 * k) }, color, style = st)
                if (icon == OneIcon.Expand) { // corners point outwards
                    corner(4f, 9f, 4f, 4f, 9f, 4f); corner(15f, 4f, 20f, 4f, 20f, 9f)
                    corner(20f, 15f, 20f, 20f, 15f, 20f); corner(9f, 20f, 4f, 20f, 4f, 15f)
                } else { // corners point inwards
                    corner(4f, 9f, 9f, 9f, 9f, 4f); corner(15f, 4f, 15f, 9f, 20f, 9f)
                    corner(20f, 15f, 15f, 15f, 15f, 20f); corner(9f, 20f, 9f, 15f, 4f, 15f)
                }
            }
            OneIcon.Cc -> {
                drawRoundRect(color, o(3f, 5.5f), Size(18f * k, 13f * k), CornerRadius(3f * k), st)
                drawArc(color, 40f, 280f, false, o(6.4f, 9.4f), Size(5.2f * k, 5.2f * k), style = st)
                drawArc(color, 40f, 280f, false, o(13.4f, 9.4f), Size(5.2f * k, 5.2f * k), style = st)
            }
            OneIcon.Wave -> {
                val h = floatArrayOf(3f, 6f, 9f, 6f, 3f)
                for (i in h.indices) line(5f + i * 3.5f, 12f - h[i], 5f + i * 3.5f, 12f + h[i])
            }
            OneIcon.Filter -> drawPath(Path().apply {
                moveTo(4f * k, 6f * k); lineTo(20f * k, 6f * k); lineTo(14f * k, 13f * k)
                lineTo(14f * k, 19f * k); lineTo(10f * k, 17f * k); lineTo(10f * k, 13f * k); close()
            }, color, style = st)
            OneIcon.Play -> {
                val tri = Path().apply { moveTo(8f * k, 5.5f * k); lineTo(19f * k, 12f * k); lineTo(8f * k, 18.5f * k); close() }
                drawPath(tri, color); drawPath(tri, color, style = st) // fill + round join = softened corners
            }
            OneIcon.Send -> { // paper plane: outline + the fold line
                drawPath(Path().apply { moveTo(21f * k, 3f * k); lineTo(14.5f * k, 21f * k); lineTo(10.8f * k, 13.2f * k); lineTo(3f * k, 9.5f * k); close() }, color, style = st)
                line(21f, 3f, 10.8f, 13.2f)
            }
            OneIcon.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            OneIcon.Check -> drawPath(Path().apply { moveTo(5f * k, 12.5f * k); lineTo(10f * k, 17.5f * k); lineTo(19f * k, 7f * k) }, color, style = st)
            OneIcon.Star -> {
                val star = Path()
                for (i in 0 until 10) {
                    val a = -PI / 2 + i * PI / 5
                    val r = if (i % 2 == 0) 8.5f else 3.9f
                    val x = (12f + r * cos(a).toFloat()) * k
                    val y = (12.6f + r * sin(a).toFloat()) * k
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, color, style = st) // outline like the rest of the set (a filled yellow star reads as an emoji)
            }
        }
    }
}

// ---- Switch (draggable, spring, slight thumb deformation) ---------------
@Composable
fun OneSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val p by animateFloatAsState(if (checked) 1f else 0f, spring(0.7f, 600f), label = "switch")
    val off = c.dim.copy(alpha = 0.28f)
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape).drawBehind { drawRect(lerp(off, c.active, p)) }
            .pointerInput(checked, rtl) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val wantOn = (dx > 0) != rtl
                    if (abs(dx) > 1f && wantOn != checked) onChange(wantOn)
                }
            }
            .press { onChange(!checked) }
    ) {
        Box(Modifier.padding(3.dp).size(26.dp).graphicsLayer {
            translationX = p * 20.dp.toPx() * (if (rtl) -1f else 1f)
            scaleX = 1f + 0.18f * (1f - abs(2f * p - 1f))
        }.background(Color.White, CircleShape))
    }
}

// ---- Segmented control (sliding indicator, same look as the nav blob) ----
@Composable
fun OneSegmented(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    height: Dp = 40.dp, textStyle: TextStyle = OneType.Body,
) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pos by animateFloatAsState(selected.toFloat(), spring(0.75f, 520f), label = "segment")
    val n = labels.size
    Row(
        modifier.height(height).clip(RoundedCornerShape(height * 0.35f)).background(c.dim.copy(alpha = 0.12f)).drawBehind {
            val w = size.width / n
            val inset = 3.dp.toPx()
            val x = (if (rtl) (n - 1) - pos else pos) * w
            val tl = Offset(x + inset, inset)
            val sz = Size(w - inset * 2f, size.height - inset * 2f)
            val r = CornerRadius((height * 0.35f).toPx() - inset)
            drawRoundRect(c.selection, tl, sz, r)
            drawRoundRect(c.accent.copy(alpha = 0.45f), tl, sz, r, Stroke(1.dp.toPx()))
        }
    ) {
        labels.forEachIndexed { i, label ->
            Box(Modifier.weight(1f).fillMaxHeight().press { onSelect(i) }, Alignment.Center) {
                OneText(label, textStyle, if (selected == i) c.accent else c.dim, maxLines = 1)
            }
        }
    }
}

// ---- Slider (gradient track, spring thumb). Always laid out left-to-right so gradients read naturally. ----
@Composable
fun OneSlider(
    value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, track: Brush, modifier: Modifier = Modifier,
    height: Dp = 32.dp, thumb: Dp = 28.dp,
) {
    val c = LocalColors.current
    var w by remember { mutableFloatStateOf(1f) }
    var held by remember { mutableStateOf(false) }
    val tv = LocalTvMode.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (held || focused) 1.14f else 1f, spring(0.6f, 500f), label = "thumb")
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.fillMaxWidth().height(height).onSizeChanged { w = it.width.toFloat() }
                // TV Mode: reachable with the D-pad; left / right move the thumb a little, and the value is saved on every step
                .tvFocusRing(src)
                .then(
                    if (tv) Modifier.tvLockable().onKeyEvent { e ->
                        val dir = if (e.key == Key.DirectionRight) 1 else if (e.key == Key.DirectionLeft) -1 else 0
                        if (dir != 0 && e.type == KeyEventType.KeyDown) { change((value + dir * 0.05f).coerceIn(0f, 1f)); done() }
                        dir != 0
                    }.focusable(interactionSource = src) else Modifier
                )
                .pointerInput(Unit) {
                    val t = thumb.toPx()
                    detectTapGestures { p -> change(((p.x - t / 2f) / (w - t).coerceAtLeast(1f)).coerceIn(0f, 1f)); done() }
                }
                .pointerInput(Unit) {
                    val t = thumb.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { held = true },
                        onDragEnd = { held = false; done() },
                        onDragCancel = { held = false; done() },
                    ) { ch, _ ->
                        ch.consume()
                        change(((ch.position.x - t / 2f) / (w - t).coerceAtLeast(1f)).coerceIn(0f, 1f))
                    }
                },
            Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(height * 0.375f).clip(CircleShape).background(track).border(0.5.dp, c.border, CircleShape))
            Box(
                Modifier.offset { IntOffset((value.coerceIn(0f, 1f) * (w - thumb.toPx()).coerceAtLeast(0f)).roundToInt(), 0) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = 4.dp.toPx(); shape = CircleShape }
                    .size(thumb).background(Color.White, CircleShape).border(0.5.dp, c.border, CircleShape)
            )
        }
    }
}

// ---- Button (primary = solid accent, otherwise glass) ----
@Composable
fun OneButton(text: String, icon: OneIcon?, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val c = LocalColors.current
    val fg = if (primary) c.onAccent else c.text
    val base = modifier.height(52.dp).press(onClick)
    val r = if (LocalLux.current) 26.dp else 18.dp // Feed: full pills
    Row(
        (if (primary) base.clip(RoundedCornerShape(r)).background(c.accent) else base.glass(2, r)).padding(horizontal = 16.dp),
        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Alignment.CenterVertically,
    ) {
        if (icon != null) OneIconView(icon) { fg }
        OneText(text, OneType.Section, fg, maxLines = 1)
    }
}

// ---- Chip (single-select filter / tag) ----
@Composable
fun OneChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val p by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "chip")
    val lux = LocalLux.current // Feed: a pill that inverts when chosen (ink on ivory, ivory on ink)
    val shape = if (lux) CircleShape else RoundedCornerShape(14.dp)
    val on = if (lux) c.text else c.selection
    Box(
        modifier.height(36.dp).press(onClick).clip(shape)
            .drawBehind { drawRect(lerp(if (lux) c.text.copy(alpha = 0f) else c.dim.copy(alpha = 0.10f), on, p)) }
            .border(0.5.dp, lerp(c.border, if (lux) c.text else c.accent.copy(alpha = 0.5f), p), shape)
            .padding(horizontal = 14.dp),
        Alignment.Center,
    ) { OneText(text, OneType.Body, lerp(c.dim, if (lux) c.bg else c.accent, p), maxLines = 1) }
}

/** Separator dot, drawn (no text glyph). */
@Composable
fun OneDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(4.dp)) { drawCircle(color) }
}
