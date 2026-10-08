package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.oneplus.app.data.Channel
import com.oneplus.app.ui.system.*

@Composable
fun ChannelsScreen(
    channels: List<Channel>, searching: Boolean, playingId: Int, group: String, portrait: Boolean, list: LazyListState,
    onSlot: (Rect) -> Unit, onGroup: (String) -> Unit, onChannel: (Int) -> Unit,
) {
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val shown = remember(channels, searching, group) { if (searching) channels else channels.filter { it.group == group } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()

    var positioned by rememberSaveable { mutableStateOf("") }
    val key = "$group|$searching"
    LaunchedEffect(key, playingId >= 0) {
        if (positioned != key && playingId >= 0) { list.scrollToItem(shown.indexOfFirst { it.id == playingId }.coerceAtLeast(0)); positioned = key }
    }

    val slot = @Composable { m: Modifier ->
        Box(m.clip(RoundedCornerShape(20.dp)).background(Color.Black).onGloballyPositioned { onSlot(it.boundsInRoot()) })
    }
    val panes = @Composable { m: Modifier ->
        Row(m) {
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(shown, key = { it.id }) { ch -> ChannelRow(ch, ch.id == playingId) { onChannel(ch.id) } } }
            LazyColumn(
                Modifier.width(112.dp).fillMaxHeight(),
                contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(groups, key = { it }) { g -> OneChip(g, !searching && g == group, { onGroup(g) }, Modifier.fillMaxWidth()) } }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = toolbarInset())) {
        if (portrait) Column(Modifier.fillMaxSize()) {
            slot(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().aspectRatio(16f / 9f))
            panes(Modifier.weight(1f))
        } else {
            val w = minOf(maxWidth * 0.5f, (maxHeight - 20.dp) * (16f / 9f))
            Row(Modifier.fillMaxSize()) {
                slot(Modifier.padding(start = 16.dp, top = 4.dp).width(w).aspectRatio(16f / 9f))
                panes(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChannelRow(ch: Channel, playing: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(16.dp)
    val logo = ch.logo.ifBlank { smartChannelLogo(ch.name) }
    Row(
        Modifier.fillMaxWidth().press(onClick).glass(1, 16.dp)
            .then(if (playing) Modifier.background(c.accentSoft).border(0.5.dp, c.accent.copy(alpha = 0.5f), shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(c.surfaceHigh, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)),
            Alignment.Center,
        ) {
            if (logo.isNotBlank()) {
                RemoteImage(
                    url = logo,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    fallback = {
                        OneText("${ch.number}", OneType.Section, if (playing) c.accent else c.textMuted)
                    }
                )
            } else {
                OneText("${ch.number}", OneType.Section, if (playing) c.accent else c.textMuted)
            }
        }
        Column(Modifier.weight(1f)) {
            OneText(ch.name, OneType.Body, if (playing) c.accent else c.text, maxLines = 1)
            if (ch.group.isNotBlank()) {
                OneText(ch.group, OneType.Caption, c.textMuted, maxLines = 1)
            }
        }
        if (playing) {
            Box(Modifier.size(8.dp).background(c.accent, androidx.compose.foundation.shape.CircleShape))
        }
    }
}

private fun smartChannelLogo(name: String): String {
    val n = name.lowercase()
    return when {
        n.contains("bein") && n.contains("1") -> "https://i.imgur.com/Vtk2cGI.png"
        n.contains("bein") && n.contains("2") -> "https://i.imgur.com/vUJZSvs.png"
        n.contains("bein") && n.contains("3") -> "https://i.imgur.com/UYSMao3.png"
        n.contains("bein") && n.contains("4") -> "https://i.imgur.com/vwAgJNi.png"
        n.contains("bein") && n.contains("5") -> "https://i.imgur.com/2Rha5aY.png"
        n.contains("bein") && n.contains("6") -> "https://i.imgur.com/0wBdLYb.png"
        n.contains("bein") && n.contains("7") -> "https://i.imgur.com/iODFwZi.png"
        n.contains("bein") && n.contains("8") -> "https://i.imgur.com/CaFEyVn.png"
        n.contains("bein") && n.contains("news") -> "https://assets.bein.com/mena/sites/3/2015/06/NEWS_DIGITAL_Mono.png"
        n.contains("bein") && n.contains("4k") -> "https://assets.bein.com/mena/sites/4/2015/06/4k_DIGITAL_Mono.png"
        n.contains("bein") -> "https://i.imgur.com/Vtk2cGI.png"
        n.contains("alkass") || n.contains("كأس") || n.contains("الكاس") -> "https://apklive.web.app/logos/alkass.png"
        n.contains("mbc") && n.contains("1") -> "https://apklive.web.app/logos/mbc1.png"
        n.contains("mbc") && n.contains("4") -> "https://apklive.web.app/logos/mbc4.png"
        n.contains("mbc") && (n.contains("مصر 2") || n.contains("masr 2")) -> "https://apklive.web.app/logos/mbc_masr.png"
        n.contains("mbc") && (n.contains("مصر") || n.contains("masr")) -> "https://apklive.web.app/logos/mbc_masr.png"
        n.contains("mbc") && (n.contains("دراما") || n.contains("drama")) -> "https://apklive.web.app/logos/mbc_drama.png"
        n.contains("mbc") && n.contains("5") -> "https://apklive.web.app/logos/mbc5.png"
        n.contains("mbc") && (n.contains("عراق") || n.contains("iraq")) -> "https://apklive.web.app/logos/mbc_iraq.png"
        n.contains("جزيرة") || n.contains("jazeera") -> "https://apklive.web.app/logos/jazeera.png"
        n.contains("عربية") || n.contains("arabiya") -> "https://apklive.web.app/logos/arabiya.png"
        n.contains("سكاي") || n.contains("sky") -> "https://apklive.web.app/logos/sky.png"
        n.contains("كويت") || n.contains("kuwait") || n.contains("kwt") -> "https://apklive.web.app/logos/kuwait.png"
        n.contains("عمان") || n.contains("oman") -> "https://apklive.web.app/logos/oman.png"
        n.contains("أكشن") || n.contains("action") -> "https://apklive.web.app/logos/aflam_action.png"
        n.contains("أفلام") || n.contains("aflam") -> "https://apklive.web.app/logos/aflam.png"
        n.contains("باب الحارة") -> "https://apklive.web.app/logos/bab_alhara.png"
        n.contains("مرايا") -> "https://apklive.web.app/logos/maraya.png"
        n.contains("وثائقية") -> "https://apklive.web.app/logos/asharq_doc.png"
        n.contains("شرق") || n.contains("asharq") -> "https://apklive.web.app/logos/asharq.png"
        else -> ""
    }
}
