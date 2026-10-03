package com.oneplus.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

/**
 * Subtitle look. [preset]: 0 clean (soft shadow) · 1 glass pill · 2 cinema (warm yellow) · 3 brand (accent highlight).
 * [size] and [lift] are 0..1 sliders (text 14-30sp, distance from the bottom 0-120dp). Persisted; values read back are clamped.
 */
@Immutable
data class SubStyle(val preset: Int = 1, val size: Float = 0.5f, val lift: Float = 0.15f) {
    fun save(app: Context) {
        app.getSharedPreferences("player", Context.MODE_PRIVATE).edit()
            .putInt("sub_preset", preset).putFloat("sub_size", size).putFloat("sub_lift", lift).apply()
    }

    companion object {
        fun load(app: Context): SubStyle = runCatching {
            val p = app.getSharedPreferences("player", Context.MODE_PRIVATE)
            val d = SubStyle()
            fun f(k: String, def: Float) = p.getFloat(k, def).let { if (it.isNaN() || it.isInfinite()) def else it.coerceIn(0f, 1f) }
            SubStyle(p.getInt("sub_preset", d.preset).coerceIn(0, 3), f("sub_size", d.size), f("sub_lift", d.lift))
        }.getOrDefault(SubStyle())
    }
}

@Composable
fun CaptionText(text: String, s: SubStyle, accent: Color, onAccent: Color, modifier: Modifier = Modifier, scale: Float = 1f) {
    val sp = (14f + 16f * s.size) * scale
    val (color, bg, shadow) = when (s.preset) {
        0 -> Triple(Color.White, null, Shadow(Color.Black, Offset(0f, 2f), 6f))
        1 -> Triple(Color.White, Color.Black.copy(alpha = 0.6f), null)
        2 -> Triple(Color(0xFFFFE066), null, Shadow(Color.Black, Offset(0f, 0f), 10f))
        else -> Triple(onAccent, accent, null)
    }
    val box = if (bg != null) Modifier.background(bg, RoundedCornerShape(if (s.preset == 3) 6.dp else 12.dp)).padding(horizontal = 10.dp, vertical = 4.dp) else Modifier
    BasicText(
        text, modifier.then(box),
        style = OneType.Body.copy(color = color, fontSize = sp.sp, lineHeight = (sp * 1.3f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, shadow = shadow),
    )
}

/** Four look cards (each previews itself) + size and position sliders. The caption on the video is the live preview. */
@Composable
fun SubtitleStyleEditor(s: SubStyle, onChange: (SubStyle) -> Unit, onDone: () -> Unit) {
    val c = LocalColors.current
    val track = Brush.horizontalGradient(listOf(c.dim.copy(alpha = 0.25f), c.accent))
    Column(Modifier.padding(top = 6.dp), Arrangement.spacedBy(4.dp)) {
        OneText(stringResource(R.string.subs_style), Tiny, c.dim)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.weight(1f).height(36.dp).press { onChange(s.copy(preset = i)); onDone() }.clip(shape)
                        .background(Color(0xFF0B1220))
                        .border(if (s.preset == i) 1.5.dp else 0.5.dp, if (s.preset == i) c.accent else c.border, shape),
                    Alignment.Center,
                ) { CaptionText("Aa", SubStyle(i, 0.15f), c.accent, c.onAccent, scale = 0.8f) }
            }
        }
        OneText(stringResource(R.string.style_size), Tiny, c.dim)
        OneSlider(s.size, { onChange(s.copy(size = it)) }, onDone, track, height = 22.dp, thumb = 16.dp)
        OneText(stringResource(R.string.style_lift), Tiny, c.dim)
        OneSlider(s.lift, { onChange(s.copy(lift = it)) }, onDone, track, height = 22.dp, thumb = 16.dp)
    }
}
