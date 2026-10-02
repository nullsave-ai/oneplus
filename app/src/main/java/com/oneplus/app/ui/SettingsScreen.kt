package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R

@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            ScreenTitle(stringResource(R.string.tab_settings))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_value))
                SettingsRow(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_value))
                SettingsRow(stringResource(R.string.settings_version), "1.0")
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surface).padding(16.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}
