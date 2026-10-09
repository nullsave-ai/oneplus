package com.oneplus.app.ui.system

import android.app.Activity
import android.content.Context
import com.oneplus.app.App
import android.graphics.drawable.ColorDrawable
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.oneplus.app.R
import com.oneplus.app.ui.GlassLook
import com.oneplus.app.ui.lookOf

@Immutable
class OneColors(
    val bg: Color, val ambient: Color, val glass: Color, val glassTint: Color, val bar: Color, val border: Color,
    val text: Color, val dim: Color, val primary: Color, val secondary: Color, val accent: Color,
    val accentSoft: Color, val selection: Color, val active: Color, val focus: Color,
    val success: Color, val warning: Color, val error: Color,
    val onAccent: Color,
)

enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Amoled(R.string.theme_amoled), Graphite(R.string.theme_graphite)
}

@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Amoled, ThemeMode.Graphite -> true
}

enum class Accent(@StringRes val label: Int, val light: Color, val dark: Color) {
    Blue(R.string.color_blue, Color(0xFF2F6FEB), Color(0xFF5B9BFF)),
    Green(R.string.color_green, Color(0xFF1E9E63), Color(0xFF4CC38A)),
    Teal(R.string.color_teal, Color(0xFF0E8F9C), Color(0xFF3CBFCB)),
    Orange(R.string.color_orange, Color(0xFFD9701A), Color(0xFFF29A4B)),
    Red(R.string.color_red, Color(0xFFD0443C), Color(0xFFEF6B63)),
    Purple(R.string.color_purple, Color(0xFF7552D6), Color(0xFFA088F0)),
    Gold(R.string.color_gold, Color(0xFFA9782B), Color(0xFFD6B26A)),
    Black(R.string.color_black, Color(0xFF111114), Color(0xFFF2F2F5)),
    Custom(R.string.color_custom, Color.Unspecified, Color.Unspecified);
}

object GlassRange {
    const val DENSITY_MIN = 0.4f
    const val DENSITY_MAX = 1.6f
    const val DENSITY_DEFAULT = 1.25f
    const val DEPTH_MIN = 0f
    const val DEPTH_MAX = 2f
    const val DEPTH_DEFAULT = 1.05f
}

@Immutable
class GlassStyle(val density: Float = GlassRange.DENSITY_DEFAULT, val depth: Float = GlassRange.DEPTH_DEFAULT)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

object CustomRange {
    const val SAT_MIN = 0.25f
    const val VAL_MIN = 0.45f
}

fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

val Spectrum: List<Color> = List(7) { hsv((it * 60f) % 360f, 1f, 1f) }

@Immutable
data class ThemePrefs(
    val mode: ThemeMode = ThemeMode.Dark,
    val accent: Accent = Accent.Blue,
    val hue: Float = 335f,
    val sat: Float = 0.62f,
    val value: Float = 0.90f,
    val hideStatusBar: Boolean = false,
    val hideNavBar: Boolean = false,
    val glass: Boolean = true,
    val transition: PageTransition = PageTransition.Look,
    val display: DisplayMode = DisplayMode.Auto,
    val glassDensity: Float = GlassRange.DENSITY_DEFAULT,
    val glassDepth: Float = GlassRange.DEPTH_DEFAULT,
    val look: Look = GlassLook,
) {
    fun colorOf(a: Accent, dark: Boolean): Color =
        if (a == Accent.Custom) fit(hsv(hue, sat, value), dark) else a.color(dark)

    fun accentColor(dark: Boolean): Color = colorOf(accent, dark)
}

private fun Accent.color(dark: Boolean): Color = if (dark) this.dark else light

private fun fit(c: Color, dark: Boolean): Color {
    var x = c
    repeat(10) {
        val l = x.luminance()
        if (dark && l < 0.25f) x = lerp(x, Color.White, 0.08f)
        else if (!dark && l > 0.32f) x = lerp(x, Color.Black, 0.08f)
    }
    return x
}

private fun onColor(bg: Color): Color {
    val l = bg.luminance() + 0.05f
    return if (1.05f / l >= l / 0.055f) Color.White else Color(0xFF0B1220)
}

val GraphitePalette = Palette(Color(0xFF141414), Color(0xFF212121), Color(0x26FFFFFF), Color(0xFFF5F5F5), Color(0xFF9B9B9B))

private fun build(p: ThemePrefs, dark: Boolean): OneColors {
    val ac = p.accentColor(dark)
    val amoled = dark && p.mode == ThemeMode.Amoled
    val graphite = p.mode == ThemeMode.Graphite
    val pal = if (graphite) GraphitePalette else p.look.palette(dark, amoled)
    val tint = if (graphite) 0f else 1f
    val surface = lerp(pal.surface, ac, 0.04f * tint)
    val bar = lerp(surface, pal.text(dark), 0.09f)
    val solid = !p.glass
    return OneColors(
        bg = if (amoled) Color.Black else lerp(pal.bg, ac, 0.03f * tint),
        ambient = if (amoled) Color.Black else if (graphite) pal.bg else ac,
        glass = surface, glassTint = lerp(pal.surface, ac, 0.12f * tint), bar = bar,
        border = pal.border(dark), text = pal.text(dark), dim = pal.dim(dark),
        primary = ac, secondary = lerp(ac, pal.dim(dark), 0.5f), accent = ac,
        accentSoft = if (solid) lerp(surface, ac, 0.20f) else ac.copy(alpha = 0.16f),
        selection = if (solid) lerp(bar, ac, 0.34f) else ac.copy(alpha = 0.20f),
        active = ac, focus = ac,
        success = if (dark) Color(0xFF4CC38A) else Color(0xFF2EA66B),
        warning = if (dark) Color(0xFFF0B455) else Color(0xFFD99A2B),
        error = if (dark) Color(0xFFEF6B63) else Color(0xFFD0443C),
        onAccent = onColor(ac),
    )
}

private fun mix(a: OneColors, b: OneColors, f: Float) = OneColors(
    lerp(a.bg, b.bg, f), lerp(a.ambient, b.ambient, f), lerp(a.glass, b.glass, f), lerp(a.glassTint, b.glassTint, f), lerp(a.bar, b.bar, f),
    lerp(a.border, b.border, f), lerp(a.text, b.text, f), lerp(a.dim, b.dim, f), lerp(a.primary, b.primary, f),
    lerp(a.secondary, b.secondary, f), lerp(a.accent, b.accent, f), lerp(a.accentSoft, b.accentSoft, f),
    lerp(a.selection, b.selection, f), lerp(a.active, b.active, f), lerp(a.focus, b.focus, f),
    lerp(a.success, b.success, f), lerp(a.warning, b.warning, f), lerp(a.error, b.error, f),
    lerp(a.onAccent, b.onAccent, f),
)

val LocalColors = compositionLocalOf { build(ThemePrefs(), true) }

val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun OnePlusTheme(prefs: ThemePrefs, dark: Boolean, content: @Composable () -> Unit) {
    val tv = LocalTvMode.current
    val target = remember(prefs, dark) { build(prefs, dark) }
    val look = remember(prefs.mode, prefs.look, prefs.glass, dark, tv) { Any() }
    var cur by remember(look) { mutableStateOf(target) }
    LaunchedEffect(target) {
        val start = cur
        if (start === target) return@LaunchedEffect
        Animatable(0f).animateTo(1f, tween(320)) { cur = mix(start, target, value) }
        cur = target
    }
    val view = LocalView.current
    SideEffect { (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(target.bg.toArgb())) }
    CompositionLocalProvider(
        LocalColors provides cur,
        LocalDarkTheme provides dark,
        LocalLook provides prefs.look,
        LocalSolid provides (!prefs.glass || WeakDevice),
        LocalTransition provides prefs.transition,
        LocalGlassStyle provides remember(prefs.glassDensity, prefs.glassDepth) { GlassStyle(prefs.glassDensity, prefs.glassDepth) },
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}

@Stable
class ThemeController(private val store: ThemeStore) {
    var prefs by mutableStateOf(store.load())
        private set

    fun update(change: ThemePrefs.() -> ThemePrefs) { prefs = prefs.change() }
    fun save() = store.save(prefs)
}

class ThemeStore(ctx: Context) {
    private val p = (ctx.applicationContext as App).prefs

    fun load(): ThemePrefs = runCatching {
        val d = ThemePrefs()
        ThemePrefs(
            mode = ThemeMode.entries.firstOrNull { it.name == p.string("mode") } ?: d.mode,
            accent = Accent.entries.firstOrNull { it.name == p.string("accent") } ?: d.accent,
            hue = (p.float("hue") ?: d.hue).clean(0f, 360f, d.hue),
            sat = (p.float("sat") ?: d.sat).clean(CustomRange.SAT_MIN, 1f, d.sat),
            value = (p.float("val") ?: d.value).clean(CustomRange.VAL_MIN, 1f, d.value),
            hideStatusBar = p.bool("hide_status") ?: d.hideStatusBar,
            hideNavBar = p.bool("hide_nav") ?: d.hideNavBar,
            glass = p.bool("glass") ?: d.glass,
            transition = PageTransition.entries.firstOrNull { it.name == p.string("transition") } ?: d.transition,
            display = DisplayMode.entries.firstOrNull { it.name == p.string("display") } ?: d.display,
            glassDensity = (p.float("glass_density") ?: d.glassDensity).clean(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, d.glassDensity),
            glassDepth = (p.float("glass_depth") ?: d.glassDepth).clean(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, d.glassDepth),
            look = lookOf(p.string("style")),
        )
    }.getOrDefault(ThemePrefs())

    fun save(t: ThemePrefs) {
        p.put(
            "mode" to t.mode.name, "accent" to t.accent.name, "hue" to t.hue, "sat" to t.sat, "val" to t.value,
            "hide_status" to t.hideStatusBar, "hide_nav" to t.hideNavBar, "glass" to t.glass, "transition" to t.transition.name,
            "display" to t.display.name, "glass_density" to t.glassDensity, "glass_depth" to t.glassDepth, "style" to t.look.id,
        )
    }

    private fun Float.clean(lo: Float, hi: Float, fallback: Float) =
        if (isNaN() || isInfinite()) fallback else coerceIn(lo, hi)
}
