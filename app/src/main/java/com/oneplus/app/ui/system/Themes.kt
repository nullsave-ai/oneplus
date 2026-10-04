package com.oneplus.app.ui.system

import android.app.Activity
import android.content.Context
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

/** Semantic tokens. Composables read these only; raw colors live in this file. */
@Immutable
class OneColors(
    val bg: Color, val ambient: Color, val glass: Color, val glassTint: Color, val border: Color,
    val text: Color, val dim: Color, val primary: Color, val secondary: Color, val accent: Color,
    val accentSoft: Color, val selection: Color, val active: Color, val focus: Color,
    val success: Color, val warning: Color, val error: Color,
    /** Readable foreground on top of a solid [accent] fill (white or near-black, whichever contrasts more). */
    val onAccent: Color,
)

/**
 * Day / night. The app opens in [Dark]; [System] follows the device; the others are an explicit user choice.
 * [Amoled] is the night theme on pure black (screens that light each pixel themselves keep those pixels off).
 */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Amoled(R.string.theme_amoled)
}

@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Amoled -> true
}

/** Two complete looks. [Glass] = floating translucent surfaces; [Feed] = flat, solid, content-first (cards, flat bars, slide transitions). */
enum class UiStyle(@StringRes val label: Int) { Glass(R.string.style_glass), Feed(R.string.style_feed) }

/** True while the [UiStyle.Feed] look is on: surfaces turn flat, bars turn solid, motion changes (see glass(), press(), OnePlusApp). */
val LocalFeed = staticCompositionLocalOf { false }

/** Color experiences. Presets have their own light and dark accent (muted, not neon); [Custom] is user-defined. */
enum class Accent(@StringRes val label: Int, val light: Color, val dark: Color) {
    Blue(R.string.color_blue, Color(0xFF2F6FEB), Color(0xFF5B9BFF)),
    Green(R.string.color_green, Color(0xFF1E9E63), Color(0xFF4CC38A)),
    Teal(R.string.color_teal, Color(0xFF0E8F9C), Color(0xFF3CBFCB)),
    Orange(R.string.color_orange, Color(0xFFD9701A), Color(0xFFF29A4B)),
    Red(R.string.color_red, Color(0xFFD0443C), Color(0xFFEF6B63)),
    Purple(R.string.color_purple, Color(0xFF7552D6), Color(0xFFA088F0)),
    Gold(R.string.color_gold, Color(0xFFA9782B), Color(0xFFD6B26A)),
    /** Black on the day theme; on the night themes the same colour would vanish into the background, so it turns off-white there. */
    Black(R.string.color_black, Color(0xFF111114), Color(0xFFF2F2F5)),
    Custom(R.string.color_custom, Color.Unspecified, Color.Unspecified);
}

/** Ranges of the glass sliders. The default (1) sits in the middle of both, so "as designed" is the thumb at rest. */
object GlassRange {
    const val DENSITY_MIN = 0.4f
    const val DENSITY_MAX = 1.6f
    const val DEPTH_MIN = 0f
    const val DEPTH_MAX = 2f
}

/** How solid ([density]) and how lifted ([depth]) every glass surface is. Read by [glass]. */
@Immutable
class GlassStyle(val density: Float = 1f, val depth: Float = 1f)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/** Ranges of the phone Feed controls: card size and how round the corners are (1 = as designed). */
object FeedRange {
    const val SIZE_MIN = 0.8f
    const val SIZE_MAX = 1.3f
    const val ROUND_MIN = 0f
    const val ROUND_MAX = 2f
}

/** The phone Feed look: [size] scales the cards, [round] scales their corner radius. */
@Immutable
class FeedLook(val size: Float = 1f, val round: Float = 1f)

val LocalFeedLook = staticCompositionLocalOf { FeedLook() }

/** True only for the phone's Feed style (the luxury look). TV Mode keeps its own feed look untouched. */
val LocalLux = staticCompositionLocalOf { false }

/** Allowed ranges for the custom color (keeps it away from white/black/gray where an accent stops working). */
object CustomRange {
    const val SAT_MIN = 0.25f
    const val VAL_MIN = 0.45f
}

fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/** Full hue circle, for the picker track and the "Custom" swatch ring. */
val Spectrum: List<Color> = List(7) { hsv((it * 60f) % 360f, 1f, 1f) }

/** Everything the user can change about appearance. Persisted by [ThemeStore]. */
@Immutable
data class ThemePrefs(
    val mode: ThemeMode = ThemeMode.Dark,
    val accent: Accent = Accent.Blue,
    val hue: Float = 335f,
    val sat: Float = 0.62f,
    val value: Float = 0.90f,
    /** Hide the phone's status (notification) bar; a swipe from the top edge still reveals it briefly. */
    val hideStatusBar: Boolean = false,
    /** Phone UI or TV UI (see TvMode.kt). [DisplayMode.Auto] lets the device decide; the layout itself never looks at the device. */
    val display: DisplayMode = DisplayMode.Auto,
    /** Glass look, as a multiplier of the built-in one: 1 = exactly as designed. See [GlassRange]. */
    val glassDensity: Float = 1f,
    val glassDepth: Float = 1f,
    val style: UiStyle = UiStyle.Glass,
    /** Phone Feed look. See [FeedRange]. */
    val feedSize: Float = 1f,
    val feedRound: Float = 1f,
) {
    fun colorOf(a: Accent, dark: Boolean): Color =
        if (a == Accent.Custom) fit(hsv(hue, sat, value), dark) else a.color(dark)

    fun accentColor(dark: Boolean): Color = colorOf(accent, dark)
}

private fun Accent.color(dark: Boolean): Color = if (dark) this.dark else light

/** A user color can be anything; nudge it until it stays readable on the current surface. */
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

private fun build(p: ThemePrefs, dark: Boolean, tv: Boolean): OneColors {
    val ac = p.accentColor(dark)
    val amoled = dark && p.mode == ThemeMode.Amoled
    val feed = p.style == UiStyle.Feed
    val lux = feed && !tv // the phone's Feed: ink black / warm ivory, hairline borders (TV keeps the neutral feed greys)
    // Feed: neutral, untinted greys (the accent only colours actions), like a video-feed app
    val bg = if (lux) (if (dark) Color(0xFF0B0B0E) else Color(0xFFF6F3EE))
        else if (feed) (if (dark) Color(0xFF0F0F0F) else Color.White) else if (dark) Color(0xFF0A0E16) else Color(0xFFEEF2F9)
    val glass = if (lux) (if (amoled) Color(0xFF111114) else if (dark) Color(0xFF16161B) else Color.White)
        else if (feed) (if (dark) Color(0xFF212121) else Color(0xFFF1F1F1)) else if (amoled) Color(0xFF16181D) else if (dark) Color(0xFF1A2333) else Color.White
    val dim = if (lux) (if (dark) Color(0xFF9B968D) else Color(0xFF7A746A)) else if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    return OneColors(
        // Amoled: the background is exactly black (no accent tint, no ambient glow) so those pixels stay off
        bg = if (amoled) Color.Black else if (feed) bg else lerp(bg, ac, 0.03f), ambient = if (amoled || feed) Color.Black else ac,
        glass = if (feed) glass else lerp(glass, ac, 0.04f), glassTint = if (feed) glass else lerp(glass, ac, 0.12f),
        border = if (lux) (if (dark) Color(0x1FFFFFFF) else Color(0x24402F14)) else if (dark) Color(0x26FFFFFF) else Color(0x2E5B6B8C),
        text = if (lux) (if (dark) Color(0xFFF3EFE8) else Color(0xFF16130F)) else if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A), dim = dim,
        primary = ac, secondary = lerp(ac, dim, 0.5f), accent = ac, accentSoft = ac.copy(alpha = 0.16f),
        selection = ac.copy(alpha = 0.20f), active = ac, focus = ac,
        success = if (dark) Color(0xFF4CC38A) else Color(0xFF2EA66B),
        warning = if (dark) Color(0xFFF0B455) else Color(0xFFD99A2B),
        error = if (dark) Color(0xFFEF6B63) else Color(0xFFD0443C),
        onAccent = onColor(ac),
    )
}

private fun mix(a: OneColors, b: OneColors, f: Float) = OneColors(
    lerp(a.bg, b.bg, f), lerp(a.ambient, b.ambient, f), lerp(a.glass, b.glass, f), lerp(a.glassTint, b.glassTint, f),
    lerp(a.border, b.border, f), lerp(a.text, b.text, f), lerp(a.dim, b.dim, f), lerp(a.primary, b.primary, f),
    lerp(a.secondary, b.secondary, f), lerp(a.accent, b.accent, f), lerp(a.accentSoft, b.accentSoft, f),
    lerp(a.selection, b.selection, f), lerp(a.active, b.active, f), lerp(a.focus, b.focus, f),
    lerp(a.success, b.success, f), lerp(a.warning, b.warning, f), lerp(a.error, b.error, f),
    lerp(a.onAccent, b.onAccent, f),
)

val LocalColors = compositionLocalOf { build(ThemePrefs(), true, false) }

/** The resolved day/night state (after applying the user's [ThemeMode]). */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** Whether the user chose to hide the phone's status bar (the fullscreen player must give it back in the same state). */
val LocalHideStatusBar = staticCompositionLocalOf { false }

/**
 * Accent changes (the colour sliders) glide over 320ms. A change of LOOK (day / night / Amoled / style / TV) is applied in the same
 * frame, never interpolated: a blend between two looks passes through colours that belong to neither (a grey halfway between day
 * and night, a blue glow fading out of Amoled), and for a few frames the colours lagged behind the flags that had already flipped.
 * The window background follows too, so nothing from another theme shows behind the first frame or while rotating.
 */
@Composable
fun OnePlusTheme(prefs: ThemePrefs, dark: Boolean, content: @Composable () -> Unit) {
    val tv = LocalTvMode.current
    val target = remember(prefs, dark, tv) { build(prefs, dark, tv) }
    val look = remember(prefs.mode, prefs.style, dark, tv) { Any() } // a new object = a different look
    var cur by remember(look) { mutableStateOf(target) }               // re-created in the same composition: no frame of old colours
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
        LocalHideStatusBar provides prefs.hideStatusBar,
        LocalFeed provides (prefs.style == UiStyle.Feed),
        LocalLux provides (prefs.style == UiStyle.Feed && !tv),
        LocalGlassStyle provides remember(prefs.glassDensity, prefs.glassDepth) { GlassStyle(prefs.glassDensity, prefs.glassDepth) },
        LocalFeedLook provides remember(prefs.feedSize, prefs.feedRound) { FeedLook(prefs.feedSize, prefs.feedRound) },
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}

/** Holds the live preferences. [update] is instant (live preview while dragging a slider); [save] persists. */
@Stable
class ThemeController(private val store: ThemeStore) {
    var prefs by mutableStateOf(store.load())
        private set

    fun update(change: ThemePrefs.() -> ThemePrefs) { prefs = prefs.change() }
    fun save() = store.save(prefs)
}

/**
 * Stored values are treated as untrusted input: unknown names, wrong types, NaN/Infinity or out-of-range
 * numbers all fall back to defaults instead of reaching the UI.
 */
class ThemeStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun load(): ThemePrefs = runCatching {
        val d = ThemePrefs()
        ThemePrefs(
            mode = ThemeMode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: d.mode,
            accent = Accent.entries.firstOrNull { it.name == p.getString("accent", null) } ?: d.accent,
            hue = p.getFloat("hue", d.hue).clean(0f, 360f, d.hue),
            sat = p.getFloat("sat", d.sat).clean(CustomRange.SAT_MIN, 1f, d.sat),
            value = p.getFloat("val", d.value).clean(CustomRange.VAL_MIN, 1f, d.value),
            hideStatusBar = p.getBoolean("hide_status", d.hideStatusBar),
            // "tv_mode" is what earlier builds stored: an explicit TV choice stays TV, everything else becomes Auto
            display = DisplayMode.entries.firstOrNull { it.name == p.getString("display", null) }
                ?: if (p.getBoolean("tv_mode", false)) DisplayMode.Tv else d.display,
            glassDensity = p.getFloat("glass_density", d.glassDensity).clean(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, d.glassDensity),
            glassDepth = p.getFloat("glass_depth", d.glassDepth).clean(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, d.glassDepth),
            style = UiStyle.entries.firstOrNull { it.name == p.getString("style", null) } ?: d.style,
            feedSize = p.getFloat("feed_size", d.feedSize).clean(FeedRange.SIZE_MIN, FeedRange.SIZE_MAX, d.feedSize),
            feedRound = p.getFloat("feed_round", d.feedRound).clean(FeedRange.ROUND_MIN, FeedRange.ROUND_MAX, d.feedRound),
        )
    }.getOrDefault(ThemePrefs())

    fun save(t: ThemePrefs) {
        p.edit().putString("mode", t.mode.name).putString("accent", t.accent.name)
            .putFloat("hue", t.hue).putFloat("sat", t.sat).putFloat("val", t.value).putBoolean("hide_status", t.hideStatusBar).putString("display", t.display.name)
            .putFloat("glass_density", t.glassDensity).putFloat("glass_depth", t.glassDepth).putString("style", t.style.name)
            .putFloat("feed_size", t.feedSize).putFloat("feed_round", t.feedRound).apply()
    }

    private fun Float.clean(lo: Float, hi: Float, fallback: Float) =
        if (isNaN() || isInfinite()) fallback else coerceIn(lo, hi)
}
