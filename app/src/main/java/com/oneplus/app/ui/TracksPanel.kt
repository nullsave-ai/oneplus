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
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.player.Opt
import com.oneplus.app.player.Playback
import com.oneplus.app.ui.system.*

internal val Small = OneType.Body.copy(fontSize = 13.sp)
internal val Tiny = OneType.Caption.copy(fontSize = 11.sp)

@Composable
fun TracksPanel(
    pb: Playback, tab: Int, onTab: (Int) -> Unit, style: SubStyle, onStyle: (SubStyle) -> Unit, onStyleDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val rows = when (tab) { 0 -> pb.qualities; 1 -> pb.audios; else -> pb.texts }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(c.bg).glass(4, 20.dp).padding(10.dp), Arrangement.spacedBy(8.dp)) {
        OneSegmented(
            listOf(R.string.tab_quality, R.string.tab_audio, R.string.tab_subs).map { stringResource(it) }, tab, onTab,
            height = 30.dp, textStyle = Small,
        )
        Column(Modifier.weight(1f).tvAutoFocus().verticalScroll(rememberScrollState()), Arrangement.spacedBy(4.dp)) {
            if (tab == 0 && rows.isEmpty()) {
                val h = pb.videoSize.height
                if (h > 0) OneText(stringResource(R.string.quality_now) + ": ${h}p", Small, c.text, Modifier.padding(6.dp))
                OneText(stringResource(R.string.quality_single), Small, c.dim, Modifier.padding(horizontal = 6.dp))
            } else if (rows.isEmpty()) OneText(stringResource(R.string.track_none), Small, c.dim, Modifier.padding(6.dp))
            rows.forEach { OptionRow(it) }
            if (tab == 2) SubtitleStyleEditor(style, onStyle, onStyleDone)
        }
    }
}

@Composable
private fun OptionRow(o: Opt) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().press(o.onSelect).clip(shape)
            .background(if (o.selected) c.accentSoft else c.dim.copy(alpha = 0.10f))
            .border(0.5.dp, if (o.selected) c.accent.copy(alpha = 0.55f) else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            OneText(o.label, Small, if (o.selected) c.accent else c.text, maxLines = 1)
            o.hint?.let { OneText(it, Tiny, c.dim, maxLines = 1) }
        }
        if (o.selected) OneIconView(OneIcon.Check, Modifier.size(16.dp)) { c.accent }
    }
}
