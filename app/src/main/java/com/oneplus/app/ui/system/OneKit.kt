package com.oneplus.app.ui.system

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.abs

// ---- Tokens -------------------------------------------------------------
val LocalGlassEffects = staticCompositionLocalOf { true }

object Sp { val S4 = 4.dp; val S8 = 8.dp; val S12 = 12.dp; val S16 = 16.dp; val S20 = 20.dp; val S24 = 24.dp; val S32 = 32.dp }

object OneType {
    val Display = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val Section = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp)
    val Caption = TextStyle(fontSize = 12.sp)
}

@Composable
fun OneText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style.copy(color = color), overflow = TextOverflow.Ellipsis, maxLines = maxLines)
}

// ---- Background / Glass / Press ----------------------------------------
@Composable
fun Modifier.ambient(): Modifier {
    val c = LocalColors.current
    return background(c.bg).drawWithCache {
        val b = Brush.radialGradient(listOf(c.ambient.copy(alpha = 0.16f), Color.Transparent),
            Offset(size.width * 0.85f, 0f), size.maxDimension * 0.7f)
        onDrawBehind { drawRect(b) }
    }
}

private val GlassAlpha = floatArrayOf(0.40f, 0.55f, 0.70f, 0.88f) // Glass 1..4
internal val Sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))

/** Translucent tint + sheen + hairline border. Real backdrop blur is not applied (see README note). */
@Composable
fun Modifier.glass(level: Int, radius: Dp): Modifier {
    val c = LocalColors.current
    val fx = LocalGlassEffects.current
    val shape = RoundedCornerShape(radius)
    val base = clip(shape).background(c.glass.copy(alpha = if (fx) GlassAlpha[level - 1] else 0.94f))
    return (if (fx) base.background(Sheen) else base).border(0.5.dp, c.border, shape)
}

/** Press physics (no ripple): scale down, spring back. */
@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.6f, 500f), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }.clickable(src, null, onClick = onClick)
}

// ---- Icons (custom, 24dp grid, 1.75 stroke) ------------------------------
enum class OneIcon { Home, Channels, Settings, Search, Close }

@Composable
fun OneIconView(icon: OneIcon, modifier: Modifier = Modifier, tint: () -> Color) {
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
