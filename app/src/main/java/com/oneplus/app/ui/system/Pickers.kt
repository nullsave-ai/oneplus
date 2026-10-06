package com.oneplus.app.ui.system

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A two-axis control: one surface, one puck. [x] runs left to right and [y] bottom to top (both 0..1). [content] is drawn
 * under the puck, so a live preview can sit inside the pad and answer to every move. Always laid out left-to-right.
 */
@Composable
fun OnePad(
    x: Float, y: Float, onChange: (Float, Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalColors.current
    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var held by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (held) 1.18f else 1f, spring(0.6f, 500f), label = "puck")
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val shape = RoundedCornerShape(26.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.fillMaxWidth().clip(shape).border(0.5.dp, c.border, shape).onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
                .drawBehind { // a quiet dot grid: the pad reads as a surface to move over, not a track to slide along
                    val gap = 24.dp.toPx()
                    var gx = gap / 2f
                    while (gx < size.width) {
                        var gy = gap / 2f
                        while (gy < size.height) { drawCircle(c.dim.copy(alpha = 0.22f), 1.2.dp.toPx(), Offset(gx, gy)); gy += gap }
                        gx += gap
                    }
                }
                .pointerInput(Unit) { detectTapGestures { p -> change((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f)); done() } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p -> held = true; change((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f)) },
                        onDragEnd = { held = false; done() }, onDragCancel = { held = false; done() },
                    ) { ch, _ -> ch.consume(); change((ch.position.x / w).coerceIn(0f, 1f), 1f - (ch.position.y / h).coerceIn(0f, 1f)) }
                },
            Alignment.Center,
        ) {
            content()
            Box(
                Modifier.offset { IntOffset((x.coerceIn(0f, 1f) * w - 14.dp.toPx()).roundToInt(), ((1f - y.coerceIn(0f, 1f)) * h - 14.dp.toPx()).roundToInt()) }
                    .align(Alignment.TopStart)
                    .graphicsLayer { scaleX = s; scaleY = s; shadowElevation = 6.dp.toPx(); this.shape = CircleShape }
                    .size(28.dp).background(Color.White, CircleShape).border(3.dp, c.accent, CircleShape)
            )
        }
    }
}

/**
 * Colour wheel: the hue on the ring, saturation (left to right) and brightness (bottom to top) on the square inside it.
 * [sat] and [bri] are 0..1 inside the allowed custom range (see [CustomRange]); the caller maps them. Touch only.
 */
@Composable
fun OneWheel(hue: Float, sat: Float, bri: Float, onChange: (hue: Float, sat: Float, bri: Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var w by remember { mutableFloatStateOf(1f) }
    var onRing by remember { mutableStateOf(true) }
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val cur by rememberUpdatedState(Triple(hue, sat, bri))
    // all geometry is a fraction of the width, so the same numbers serve drawing and touch
    val ringW = { w * 0.11f }
    val inner = { w / 2f - ringW() - w * 0.04f }      // radius of the circle that holds the square
    val half = { inner() * 0.7071f }                    // half side of the square inscribed in it
    val pick = { p: Offset -> onRing = hypot(p.x - w / 2f, p.y - w / 2f) > inner() + w * 0.02f }
    val move = { p: Offset ->
        val dx = p.x - w / 2f; val dy = p.y - w / 2f
        if (onRing) {
            var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            if (a < 0f) a += 360f
            change(a, cur.second, cur.third)
        } else change(cur.first, ((dx + half()) / (2f * half())).coerceIn(0f, 1f), 1f - ((dy + half()) / (2f * half())).coerceIn(0f, 1f))
    }
    Canvas(
        modifier.size(224.dp).onSizeChanged { w = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { p -> pick(p); move(p); done() } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { p -> pick(p); move(p) }, onDragEnd = { done() }, onDragCancel = { done() }) { ch, _ -> ch.consume(); move(ch.position) }
            },
    ) {
        val ctr = Offset(size.width / 2f, size.height / 2f)
        val rw = size.width * 0.11f
        val ringR = size.width / 2f - rw / 2f
        val hs = (size.width / 2f - rw - size.width * 0.04f) * 0.7071f
        // ring
        drawCircle(Brush.sweepGradient(Spectrum, ctr), ringR, ctr, style = Stroke(rw))
        // square: the allowed saturation range left to right, then the allowed brightness range as a black veil from top to bottom
        val tl = Offset(ctr.x - hs, ctr.y - hs)
        val sq = Size(hs * 2f, hs * 2f)
        val r = CornerRadius(size.width * 0.05f)
        drawRoundRect(Brush.horizontalGradient(listOf(hsv(hue, CustomRange.SAT_MIN, 1f), hsv(hue, 1f, 1f)), tl.x, tl.x + sq.width), tl, sq, r)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 1f - CustomRange.VAL_MIN)), tl.y, tl.y + sq.height), tl, sq, r)
        // thumbs
        val rad = Math.toRadians(hue.toDouble())
        val ringPt = Offset(ctr.x + ringR * cos(rad).toFloat(), ctr.y + ringR * sin(rad).toFloat())
        val sqPt = Offset(tl.x + sat * sq.width, tl.y + (1f - bri) * sq.height)
        val mine = hsv(hue, CustomRange.SAT_MIN + (1f - CustomRange.SAT_MIN) * sat, CustomRange.VAL_MIN + (1f - CustomRange.VAL_MIN) * bri)
        drawCircle(hsv(hue, 1f, 1f), rw * 0.62f, ringPt); drawCircle(Color.White, rw * 0.62f, ringPt, style = Stroke(3.dp.toPx()))
        drawCircle(mine, rw * 0.62f, sqPt); drawCircle(Color.White, rw * 0.62f, sqPt, style = Stroke(3.dp.toPx()))
    }
}
