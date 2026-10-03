package com.oneplus.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.TestStreamUrl
import com.oneplus.app.player.buildLink
import com.oneplus.app.ui.system.*

private const val SwatchesPerRow = 4

@Composable
fun SettingsScreen(effects: Boolean, onEffects: (Boolean) -> Unit, theme: ThemeController, scroll: ScrollState, onLink: (String) -> Unit) {
    val c = LocalColors.current
    val dark = LocalDarkTheme.current
    val p = theme.prefs
    var open by rememberSaveable { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(scroll)
                .padding(start = 20.dp, end = 20.dp, top = toolbarInset(), bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OneText(stringResource(R.string.color_title), OneType.Section, c.text, Modifier.padding(start = 4.dp))
            Column(Modifier.fillMaxWidth().glass(2, 22.dp).animateContentSize()) {
                SettingRow(stringResource(R.string.theme_mode)) {
                    OneSegmented(
                        ThemeMode.entries.map { stringResource(it.label) }, p.mode.ordinal,
                        { i -> theme.update { copy(mode = ThemeMode.entries[i]) }; theme.save() },
                        Modifier.width(216.dp),
                    )
                }
                SettingRow(stringResource(R.string.color_row), Modifier.press { open = !open }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OneText(stringResource(p.accent.label), OneType.Body, c.dim)
                        Box(Modifier.size(20.dp).background(c.accent, CircleShape))
                    }
                }
                if (open) Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp), Arrangement.spacedBy(8.dp)) {
                    Accent.entries.chunked(SwatchesPerRow).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { a ->
                                Swatch(a, p, a == p.accent, dark, { theme.update { copy(accent = a) }; theme.save() }, Modifier.weight(1f))
                            }
                            repeat(SwatchesPerRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (p.accent == Accent.Custom) CustomPicker(theme)
                }
                SettingRow(stringResource(R.string.settings_effects)) { OneSwitch(effects, onEffects) }
            }
            OneText(stringResource(R.string.link_title), OneType.Section, c.text, Modifier.padding(start = 4.dp, top = 8.dp))
            TestPlayerCard(onLink)
            Column(Modifier.fillMaxWidth().glass(2, 22.dp)) {
                SettingRow(stringResource(R.string.settings_version)) { OneText("1.0", OneType.Body, c.dim) }
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

@Composable
private fun PickerRow(label: Int, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, track: Brush) {
    Column {
        OneText(stringResource(label), OneType.Caption, LocalColors.current.dim, Modifier.padding(start = 4.dp, top = 6.dp))
        OneSlider(value, onChange, onDone, track)
    }
}

private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun unlerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

@Composable
private fun Swatch(a: Accent, p: ThemePrefs, selected: Boolean, dark: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "ring")
    val col = p.colorOf(a, dark)
    val custom = a == Accent.Custom
    val spectrum = remember { Brush.sweepGradient(Spectrum) }
    Column(modifier.press(onClick), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).drawBehind {
            drawCircle(col, size.minDimension / 2f - 7.dp.toPx())
            if (custom) drawCircle(spectrum, size.minDimension / 2f - 2.dp.toPx(), style = Stroke(2.5.dp.toPx()), alpha = 0.55f + 0.45f * ring)
            else drawCircle(col, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()), alpha = ring)
        })
        OneText(stringResource(a.label), OneType.Caption, if (selected) c.text else c.dim, maxLines = 1)
    }
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

/**
 * Test bench for the player: a link plus everything a stream may need (User-Agent, Referer, DRM scheme, license / keys).
 * The fields are folded into one `URL|option=value&...` string (see [buildLink]); the player parses it back, so this is the
 * same path real catalogue links take.
 */
@Composable
private fun TestPlayerCard(onPlay: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf("") }
    var ref by rememberSaveable { mutableStateOf("") }
    var drm by rememberSaveable { mutableIntStateOf(0) } // 0 none · 1 widevine · 2 playready · 3 clearkey
    var lic by rememberSaveable { mutableStateOf("") }
    val schemes = listOf(null, "widevine", "playready", "clearkey")
    val names = listOf(stringResource(R.string.drm_none), "Widevine", "PlayReady", "ClearKey")
    Column(Modifier.fillMaxWidth().glass(2, 22.dp).padding(12.dp), Arrangement.spacedBy(10.dp)) {
        Field(stringResource(R.string.link_url), url) { url = it }
        Field("User-Agent", ua) { ua = it }
        Field("Referer", ref) { ref = it }
        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n -> OneChip(n, drm == i, { drm = i }) }
        }
        if (drm > 0) Field(stringResource(if (drm == 3) R.string.link_keys else R.string.link_license), lic) { lic = it }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            OneButton(stringResource(R.string.link_sample), null, { url = TestStreamUrl }, Modifier.weight(1f), primary = false)
            OneButton(
                stringResource(R.string.link_play), OneIcon.Play,
                { if (url.isNotBlank()) onPlay(buildLink(url, ua, ref, schemes[drm], lic)) }, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Field(hint: String, value: String, onChange: (String) -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.dim.copy(alpha = 0.12f)).padding(horizontal = 12.dp),
        Alignment.CenterStart,
    ) {
        if (value.isEmpty()) OneText(hint, OneType.Body, c.dim, maxLines = 1)
        BasicTextField(
            value, { onChange(it.take(4096)) }, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = OneType.Body.copy(color = c.text, textDirection = TextDirection.Ltr),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
    }
}
