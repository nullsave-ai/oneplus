package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.player.Opt
import com.oneplus.app.player.Playback
import com.oneplus.app.ui.system.*

/**
 * One floating glass panel for everything selectable: tabs (quality / audio / subtitles) over a list of rows.
 * The subtitles tab also hosts the style editor. Rows are plain [Opt]s, so the panel knows nothing about the player.
 */
@Composable
fun TracksPanel(
    pb: Playback, tab: Int, onTab: (Int) -> Unit, style: SubStyle, onStyle: (SubStyle) -> Unit, onStyleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val rows = when (tab) { 0 -> pb.qualities; 1 -> pb.audios; else -> pb.texts }
    // Same surface language as the rest of the app: an opaque base (video must not show through) + glass tint, sheen and hairline.
    Column(modifier.background(c.bg, RoundedCornerShape(28.dp)).glass(4, 28.dp).padding(14.dp), Arrangement.spacedBy(12.dp)) {
        OneSegmented(
            listOf(R.string.tab_quality, R.string.tab_audio, R.string.tab_subs).map { stringResource(it) }, tab, onTab,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
            if (rows.isEmpty()) OneText(stringResource(R.string.track_none), OneType.Body, c.dim, Modifier.padding(8.dp))
            rows.forEach { OptionRow(it) }
            if (tab == 2) SubtitleStyleEditor(style, onStyle, onStyleDone)
        }
    }
}

@Composable
private fun OptionRow(o: Opt) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().press(o.onSelect).clip(shape)
            .background(if (o.selected) c.accentSoft else c.dim.copy(alpha = 0.10f))
            .border(0.5.dp, if (o.selected) c.accent.copy(alpha = 0.55f) else Color.Transparent, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OneText(o.label, OneType.Body, if (o.selected) c.accent else c.text, maxLines = 1)
            o.hint?.let { OneText(it, OneType.Caption, c.dim, maxLines = 1) }
        }
        if (o.selected) OneIconView(OneIcon.Check, Modifier.size(20.dp)) { c.accent }
    }
}
