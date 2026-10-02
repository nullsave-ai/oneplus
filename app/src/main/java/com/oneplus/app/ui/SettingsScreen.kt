package com.oneplus.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

@Composable
fun SettingsScreen(effects: Boolean, onEffects: (Boolean) -> Unit, accent: Accent, onAccent: (Accent) -> Unit) {
    val c = LocalColors.current
    val dark = isSystemInDarkTheme()
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().padding(top = toolbarInset(), start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OneText(stringResource(R.string.color_title), OneType.Section, c.text, Modifier.padding(start = 4.dp))
            Column(Modifier.fillMaxWidth().glass(2, 22.dp).animateContentSize()) {
                SettingRow(stringResource(R.string.color_row), Modifier.press { open = !open }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OneText(stringResource(accent.label), OneType.Body, c.dim)
                        Box(Modifier.size(20.dp).background(c.accent, CircleShape))
                    }
                }
                if (open) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                    Accent.entries.forEach { a -> Swatch(a, a == accent, dark, { onAccent(a) }, Modifier.weight(1f)) }
                }
                SettingRow(stringResource(R.string.settings_effects)) { OneSwitch(effects, onEffects) }
            }
            Column(Modifier.fillMaxWidth().glass(2, 22.dp)) {
                SettingRow(stringResource(R.string.settings_version)) { OneText("1.0", OneType.Body, c.dim) }
            }
        }
    }
}

@Composable
private fun Swatch(a: Accent, selected: Boolean, dark: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = LocalColors.current
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "ring")
    val col = a.color(dark)
    Column(modifier.press(onClick), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).drawBehind {
            drawCircle(col, size.minDimension / 2f - 7.dp.toPx())
            drawCircle(col, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()), alpha = ring)
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
