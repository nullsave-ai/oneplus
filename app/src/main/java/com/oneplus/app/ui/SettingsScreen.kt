package com.oneplus.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlin.math.roundToInt

/** Settings: one page for every screen. TV Mode only swaps the touch-only controls (colour wheel, glass pad) for ones a remote can step. */
@Composable
fun SettingsScreen(
    effects: Boolean, onEffects: (Boolean) -> Unit, theme: ThemeController, scroll: ScrollState, subScroll: ScrollState,
    page: Int, onPage: (Int) -> Unit, wide: Boolean, saved: List<Movie>, onMovie: (Int) -> Unit,
    onTelegram: () -> Unit, onClearHistory: () -> Unit,
) = PhoneSettings(effects, onEffects, theme, scroll, subScroll, page, onPage, wide, saved, onMovie, onTelegram, onClearHistory)

/** Three sliders (hue / saturation / brightness). The whole app recolors live; the value is saved on release. */
@Composable
private fun CustomPicker(theme: ThemeController) {
    val p = theme.prefs
    val hueTrack = remember { Brush.horizontalGradient(Spectrum) }
    val satTrack = remember(p.hue, p.value) { Brush.horizontalGradient(listOf(hsv(p.hue, CustomRange.SAT_MIN, p.value), hsv(p.hue, 1f, p.value))) }
    val valTrack = remember(p.hue, p.sat) { Brush.horizontalGradient(listOf(hsv(p.hue, p.sat, CustomRange.VAL_MIN), hsv(p.hue, p.sat, 1f))) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Arrangement.spacedBy(2.dp)) {
        PickerRow(R.string.color_hue, p.hue / 360f, { f -> theme.update { copy(hue = f * 360f) } }, theme::save, hueTrack)
        PickerRow(
            R.string.color_sat, unlerp(CustomRange.SAT_MIN, 1f, p.sat),
            { f -> theme.update { copy(sat = lerpF(CustomRange.SAT_MIN, 1f, f)) } }, theme::save, satTrack,
        )
        PickerRow(
            R.string.color_val, unlerp(CustomRange.VAL_MIN, 1f, p.value),
            { f -> theme.update { copy(value = lerpF(CustomRange.VAL_MIN, 1f, f)) } }, theme::save, valTrack,
        )
    }
}

/** Density and depth of the glass. Both sit at the middle of their track when they are "as designed"; the app shows the change live. */
@Composable
internal fun GlassSliders(theme: ThemeController) {
    val c = LocalColors.current
    val p = theme.prefs
    val track = remember(c) { Brush.horizontalGradient(listOf(c.dim.copy(alpha = 0.10f), c.accent.copy(alpha = 0.60f))) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Arrangement.spacedBy(2.dp)) {
        PickerRow(
            R.string.glass_density, unlerp(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, p.glassDensity),
            { f -> theme.update { copy(glassDensity = lerpF(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, f)) } }, theme::save, track,
            "${(p.glassDensity * 100f).roundToInt()}%",
        )
        PickerRow(
            R.string.glass_depth, unlerp(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, p.glassDepth),
            { f -> theme.update { copy(glassDepth = lerpF(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, f)) } }, theme::save, track,
            "${(p.glassDepth * 100f).roundToInt()}%",
        )
    }
    GlassReset(theme)
}

/** "Reset" row: only there while the glass differs from the defaults. */
@Composable
private fun GlassReset(theme: ThemeController) {
    val p = theme.prefs
    if (p.glassDensity != GlassRange.DENSITY_DEFAULT || p.glassDepth != GlassRange.DEPTH_DEFAULT) SettingRow(
        stringResource(R.string.glass_reset),
        Modifier.press { theme.update { copy(glassDensity = GlassRange.DENSITY_DEFAULT, glassDepth = GlassRange.DEPTH_DEFAULT) }; theme.save() },
    ) {}
}

@Composable
internal fun PickerRow(label: Int, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, track: Brush, hint: String? = null) {
    val c = LocalColors.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 6.dp), Arrangement.SpaceBetween) {
            OneText(stringResource(label), OneType.Caption, c.dim)
            if (hint != null) OneText(hint, OneType.Caption, c.dim)
        }
        OneSlider(value, onChange, onDone, track)
    }
}

internal fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
internal fun unlerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

/** Colour presets as one row of dots; "Custom" opens the wheel (touch) or the three sliders (TV Mode, steppable with a remote). */
@Composable
internal fun ColorStudio(theme: ThemeController, dark: Boolean, tv: Boolean = LocalTvMode.current) {
    val p = theme.prefs
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            Accent.entries.forEach { a -> Dot(a, p, a == p.accent, dark, { theme.update { copy(accent = a) }; theme.save() }, Modifier.weight(1f)) }
        }
        if (p.accent == Accent.Custom) {
            if (tv) CustomPicker(theme) else OneWheel(
                p.hue, unlerp(CustomRange.SAT_MIN, 1f, p.sat), unlerp(CustomRange.VAL_MIN, 1f, p.value),
                { h, sa, br -> theme.update { copy(hue = h, sat = lerpF(CustomRange.SAT_MIN, 1f, sa), value = lerpF(CustomRange.VAL_MIN, 1f, br)) } }, theme::save,
            )
        }
    }
}

@Composable
private fun Dot(a: Accent, p: ThemePrefs, selected: Boolean, dark: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val on by animateFloatAsState(if (selected) 1f else 0f, spring(0.6f, 500f), label = "dot")
    val col = if (a == Accent.Black) Color(0xFF0B0B0D) else p.colorOf(a, dark)
    val spectrum = remember { Brush.sweepGradient(Spectrum) }
    Box(modifier.height(40.dp).press(onClick), Alignment.Center) {
        Box(Modifier.size(26.dp).graphicsLayer { val k = 1f + 0.12f * on; scaleX = k; scaleY = k }.drawBehind {
            val r = size.minDimension / 2f
            if (a == Accent.Custom) { drawCircle(spectrum, r); drawCircle(col, r * 0.5f) } else drawCircle(col, r)
            if (a == Accent.Black) drawCircle(c.dim, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()), alpha = 0.6f) // visible on a black screen too
            if (on > 0.01f) drawCircle(c.text, r + 3.5.dp.toPx(), style = Stroke(1.75.dp.toPx()), alpha = on)
        })
    }
}

/**
 * One pad instead of two sliders: glass density (x) and depth (y), with a live sample inside it. The whole app answers while the
 * puck moves; the value is saved on release.
 */
@Composable
internal fun LookPad(theme: ThemeController) {
    val c = LocalColors.current
    val p = theme.prefs
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.spacedBy(10.dp)) {
        OnePad(
            unlerp(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, p.glassDensity), unlerp(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, p.glassDepth),
            { nx, ny -> theme.update { copy(glassDensity = lerpF(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, nx), glassDepth = lerpF(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, ny)) } },
            theme::save, Modifier.height(200.dp),
        ) {
            // colourful shapes behind the sample, so translucency and depth are visible
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.dim.copy(alpha = 0.15f)))))
            Box(Modifier.offset(x = (-56).dp, y = (-26).dp).size(84.dp).background(c.accent.copy(alpha = 0.85f), CircleShape))
            Box(Modifier.size(132.dp, 78.dp).glass(2, 22.dp), Alignment.Center) { OneText("Aa", OneType.Title, c.text) }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            OneText("${stringResource(R.string.glass_density)}  ${(p.glassDensity * 100f).roundToInt()}%", OneType.Caption, c.dim)
            OneText("${stringResource(R.string.glass_depth)}  ${(p.glassDepth * 100f).roundToInt()}%", OneType.Caption, c.dim)
        }
    }
    GlassReset(theme)
}

@Composable
internal fun SettingRow(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 32.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        OneText(title, OneType.Body, LocalColors.current.text)
        trailing()
    }
}
