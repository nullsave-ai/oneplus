package com.oneplus.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
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

@Composable
fun SettingsScreen(
    effects: Boolean, onEffects: (Boolean) -> Unit, theme: ThemeController, scroll: ScrollState, wide: Boolean,
    saved: List<Movie>, onMovie: (Int) -> Unit,
    onTelegram: () -> Unit, onClearHistory: () -> Unit,
) {
    val c = LocalColors.current
    val dark = LocalDarkTheme.current
    val feed = LocalFeed.current
    val tv = LocalTvMode.current
    val p = theme.prefs
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    // The three blocks of the page. The phone stacks them in one scroll; TV Mode shows one at a time (see below).
    val telegram = @Composable {
        // channel card: icon + two lines + chevron, like a feed entry
        Row(
            Modifier.fillMaxWidth().press(onTelegram).glass(2, 22.dp).padding(16.dp),
            Arrangement.spacedBy(14.dp), Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(c.accentSoft, CircleShape), Alignment.Center) { OneIconView(OneIcon.Send) { c.accent } }
            Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                OneText(stringResource(R.string.settings_tg), OneType.Section, c.text, maxLines = 1)
                OneText(stringResource(R.string.settings_tg_sub), OneType.Caption, c.dim, maxLines = 1)
            }
            OneIconView(OneIcon.Next) { c.dim }
        }
    }
    val appearance = @Composable {
        Column(Modifier.fillMaxWidth().glass(2, 22.dp).animateContentSize()) {
            SettingRow(stringResource(R.string.style_mode)) {
                OneSegmented(
                    UiStyle.entries.map { stringResource(it.label) }, p.style.ordinal,
                    { i -> theme.update { copy(style = UiStyle.entries[i]) }; theme.save() }, Modifier.width(168.dp),
                )
            }
            SettingRow(stringResource(R.string.theme_mode)) {
                OneSegmented(
                    ThemeMode.entries.map { stringResource(it.label) }, p.mode.ordinal,
                    { i -> theme.update { copy(mode = ThemeMode.entries[i]) }; theme.save() },
                    Modifier.width(248.dp), textStyle = OneType.Caption,
                )
            }
            SettingRow(stringResource(R.string.display_mode)) {
                OneSegmented(
                    DisplayMode.entries.map { stringResource(it.label) }, p.display.ordinal,
                    { i -> theme.update { copy(display = DisplayMode.entries[i]) }; theme.save() },
                    Modifier.width(216.dp),
                )
            }
            SettingRow(stringResource(R.string.color_row)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OneText(stringResource(p.accent.label), OneType.Body, c.dim)
                    Box(Modifier.size(20.dp).background(c.accent, CircleShape))
                }
            }
            ColorStudio(theme, dark, tv)
            if (!feed) SettingRow(stringResource(R.string.settings_effects)) { OneSwitch(effects, onEffects) } // the glass effects mean nothing on the flat Feed surfaces
            if (tv) { if (!feed) GlassSliders(theme) } // TV: a remote cannot drag a pad, so it keeps the sliders it can step with the D-pad
            else LookPad(theme, feed)
            SettingRow(stringResource(R.string.settings_hide_status)) {
                OneSwitch(p.hideStatusBar) { theme.update { copy(hideStatusBar = it) }; theme.save() }
            }
        }
    }
    val general = @Composable {
        Column(Modifier.fillMaxWidth().glass(2, 22.dp)) {
            SettingRow(stringResource(R.string.settings_clear), Modifier.press(onClearHistory)) {}
            SettingRow(stringResource(R.string.settings_version)) { OneText("1.0", OneType.Body, c.dim) }
        }
    }
    if (tv) {
        // TV Mode: categories on the start side (moving the remote over one opens it), the chosen block beside it.
        // The block scrolls on its own and slides under the rail; the categories stay put.
        val panes = buildList<Pair<Int, @Composable () -> Unit>> {
            if (saved.isNotEmpty()) add(Pair<Int, @Composable () -> Unit>(R.string.sec_list, { SavedShelf(saved, wide, onMovie) }))
            add(Pair<Int, @Composable () -> Unit>(R.string.color_title, appearance))
            add(Pair<Int, @Composable () -> Unit>(R.string.settings_general, { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { telegram(); general() } }))
        }
        var cat by rememberSaveable { mutableIntStateOf(0) }
        val at = cat.coerceIn(0, panes.lastIndex)
        Row(Modifier.fillMaxSize().padding(top = toolbarInset(), bottom = bottom)) {
            Column(Modifier.width(200.dp).padding(start = 16.dp, end = 8.dp), Arrangement.spacedBy(8.dp)) {
                panes.forEachIndexed { i, (label, _) ->
                    OneChip(stringResource(label), i == at, { cat = i }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) cat = i })
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll)
                    .absolutePadding(left = LocalRailInset.current + 8.dp, right = 16.dp),
            ) { panes[at].second() }
        }
    } else Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(scroll).padding(top = toolbarInset(), bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // the viewer's saved movies come first and scroll sideways (edge to edge); everything below is the settings proper
            if (saved.isNotEmpty()) SavedShelf(saved, wide, onMovie)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                telegram()
                OneText(stringResource(R.string.color_title), OneType.Section, c.text, Modifier.padding(start = 4.dp, top = 8.dp))
                appearance()
                general()
            }
        }
    }
}

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
private fun GlassSliders(theme: ThemeController) {
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
    if (p.glassDensity != 1f || p.glassDepth != 1f) {
        SettingRow(stringResource(R.string.glass_reset), Modifier.press { theme.update { copy(glassDensity = 1f, glassDepth = 1f) }; theme.save() }) {}
    }
}

@Composable
private fun PickerRow(label: Int, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, track: Brush, hint: String? = null) {
    val c = LocalColors.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 6.dp), Arrangement.SpaceBetween) {
            OneText(stringResource(label), OneType.Caption, c.dim)
            if (hint != null) OneText(hint, OneType.Caption, c.dim)
        }
        OneSlider(value, onChange, onDone, track)
    }
}

private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun unlerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

/** Colour presets as one row of dots; "Custom" opens the wheel (phone) or the three sliders (TV, steppable with a remote). */
@Composable
private fun ColorStudio(theme: ThemeController, dark: Boolean, tv: Boolean) {
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
 * One pad instead of two sliders. Glass: density (x) and depth (y). Feed: card size (x) and corner roundness (y).
 * A live sample sits inside the pad, and the whole app answers while the puck moves; the value is saved on release.
 */
@Composable
private fun LookPad(theme: ThemeController, feed: Boolean) {
    val c = LocalColors.current
    val p = theme.prefs
    val x = if (feed) unlerp(FeedRange.SIZE_MIN, FeedRange.SIZE_MAX, p.feedSize) else unlerp(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, p.glassDensity)
    val y = if (feed) unlerp(FeedRange.ROUND_MIN, FeedRange.ROUND_MAX, p.feedRound) else unlerp(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, p.glassDepth)
    val vx = if (feed) p.feedSize else p.glassDensity
    val vy = if (feed) p.feedRound else p.glassDepth
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.spacedBy(10.dp)) {
        OneText(stringResource(if (feed) R.string.look_feed else R.string.glass_title), OneType.Caption, c.dim)
        OnePad(
            x, y,
            { nx, ny ->
                theme.update {
                    if (feed) copy(feedSize = lerpF(FeedRange.SIZE_MIN, FeedRange.SIZE_MAX, nx), feedRound = lerpF(FeedRange.ROUND_MIN, FeedRange.ROUND_MAX, ny))
                    else copy(glassDensity = lerpF(GlassRange.DENSITY_MIN, GlassRange.DENSITY_MAX, nx), glassDepth = lerpF(GlassRange.DEPTH_MIN, GlassRange.DEPTH_MAX, ny))
                }
            },
            theme::save, Modifier.height(200.dp),
        ) {
            // colourful shapes behind the sample, so translucency and depth are visible
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.dim.copy(alpha = 0.15f)))))
            Box(Modifier.offset(x = (-56).dp, y = (-26).dp).size(84.dp).background(c.accent.copy(alpha = 0.85f), CircleShape))
            val k = if (feed) p.feedSize else 1f
            Box(Modifier.size(132.dp * k, 78.dp * k).glass(2, 22.dp), Alignment.Center) { OneText("Aa", OneType.Title, c.text) }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            OneText("${stringResource(if (feed) R.string.style_size else R.string.glass_density)}  ${(vx * 100f).roundToInt()}%", OneType.Caption, c.dim)
            OneText("${stringResource(if (feed) R.string.look_round else R.string.glass_depth)}  ${(vy * 100f).roundToInt()}%", OneType.Caption, c.dim)
        }
    }
    if (vx != 1f || vy != 1f) SettingRow(
        stringResource(R.string.glass_reset),
        Modifier.press { theme.update { if (feed) copy(feedSize = 1f, feedRound = 1f) else copy(glassDensity = 1f, glassDepth = 1f) }; theme.save() },
    ) {}
}

@Composable
private fun SettingRow(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 32.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        OneText(title, OneType.Body, LocalColors.current.text)
        trailing()
    }
}
