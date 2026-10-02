package com.oneplus.app.ui.system

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.oneplus.app.R

/** Semantic tokens. Composables read these only; raw colors live in this file. */
@Immutable
class OneColors(
    val bg: Color, val ambient: Color, val glass: Color, val glassTint: Color, val border: Color,
    val text: Color, val dim: Color, val primary: Color, val secondary: Color, val accent: Color,
    val accentSoft: Color, val selection: Color, val active: Color, val focus: Color,
    val success: Color, val warning: Color, val error: Color,
)

/** Color experiences. Each has its own light and dark accent (muted, not neon). */
enum class Accent(@StringRes val label: Int, val light: Color, val dark: Color) {
    Blue(R.string.color_blue, Color(0xFF2F6FEB), Color(0xFF5B9BFF)),
    Green(R.string.color_green, Color(0xFF1E9E63), Color(0xFF4CC38A)),
    Teal(R.string.color_teal, Color(0xFF0E8F9C), Color(0xFF3CBFCB)),
    Orange(R.string.color_orange, Color(0xFFD9701A), Color(0xFFF29A4B)),
    Red(R.string.color_red, Color(0xFFD0443C), Color(0xFFEF6B63)),
    Purple(R.string.color_purple, Color(0xFF7552D6), Color(0xFFA088F0));

    fun color(dark: Boolean): Color = if (dark) this.dark else light
}

private fun build(a: Accent, dark: Boolean): OneColors {
    val ac = a.color(dark)
    val bg = if (dark) Color(0xFF0A0E16) else Color(0xFFEEF2F9)
    val glass = if (dark) Color(0xFF1A2333) else Color.White
    val dim = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    return OneColors(
        bg = lerp(bg, ac, 0.03f), ambient = ac,
        glass = lerp(glass, ac, 0.04f), glassTint = lerp(glass, ac, 0.12f),
        border = if (dark) Color(0x26FFFFFF) else Color(0x2E5B6B8C),
        text = if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A), dim = dim,
        primary = ac, secondary = lerp(ac, dim, 0.5f), accent = ac, accentSoft = ac.copy(alpha = 0.16f),
        selection = ac.copy(alpha = 0.20f), active = ac, focus = ac,
        success = if (dark) Color(0xFF4CC38A) else Color(0xFF2EA66B),
        warning = if (dark) Color(0xFFF0B455) else Color(0xFFD99A2B),
        error = if (dark) Color(0xFFEF6B63) else Color(0xFFD0443C),
    )
}

private fun mix(a: OneColors, b: OneColors, f: Float) = OneColors(
    lerp(a.bg, b.bg, f), lerp(a.ambient, b.ambient, f), lerp(a.glass, b.glass, f), lerp(a.glassTint, b.glassTint, f),
    lerp(a.border, b.border, f), lerp(a.text, b.text, f), lerp(a.dim, b.dim, f), lerp(a.primary, b.primary, f),
    lerp(a.secondary, b.secondary, f), lerp(a.accent, b.accent, f), lerp(a.accentSoft, b.accentSoft, f),
    lerp(a.selection, b.selection, f), lerp(a.active, b.active, f), lerp(a.focus, b.focus, f),
    lerp(a.success, b.success, f), lerp(a.warning, b.warning, f), lerp(a.error, b.error, f),
)

val LocalColors = compositionLocalOf { build(Accent.Blue, true) }

/** Theme change = interpolation of every token (bg, ambient, glass tint, accent...) over 320ms. */
@Composable
fun OnePlusTheme(accent: Accent, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val target = remember(accent, dark) { build(accent, dark) }
    var cur by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val start = cur
        if (start === target) return@LaunchedEffect
        Animatable(0f).animateTo(1f, tween(320)) { cur = mix(start, target, value) }
        cur = target
    }
    CompositionLocalProvider(
        LocalColors provides cur,
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}

class ThemeStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)
    fun load(): Accent = Accent.entries.firstOrNull { it.name == p.getString("accent", null) } ?: Accent.Blue
    fun save(a: Accent) = p.edit().putString("accent", a.name).apply()
}
