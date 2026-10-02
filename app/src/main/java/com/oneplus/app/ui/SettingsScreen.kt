package com.oneplus.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

@Composable
fun SettingsScreen(effects: Boolean, onEffects: (Boolean) -> Unit) {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            OneText(stringResource(R.string.tab_settings), OneType.Display, c.text, Modifier.padding(top = 8.dp))
            Column(Modifier.fillMaxWidth().glass(2, 22.dp).padding(vertical = 4.dp)) {
                Row0(stringResource(R.string.settings_effects)) { OneSwitch(effects, onEffects) }
                Row0(stringResource(R.string.settings_theme)) { OneText(stringResource(R.string.settings_theme_value), OneType.Body, c.dim) }
                Row0(stringResource(R.string.settings_version)) { OneText("1.0", OneType.Body, c.dim) }
            }
        }
    }
}

@Composable
private fun Row0(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 32.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        OneText(title, OneType.Body, LocalColors.current.text)
        trailing()
    }
}
