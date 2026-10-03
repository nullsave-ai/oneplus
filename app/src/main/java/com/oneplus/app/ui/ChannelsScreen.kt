package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.oneplus.app.data.Channel
import com.oneplus.app.ui.system.*

/**
 * TV-style page: the player sits at the top (it is the app's one player, drawn over the empty [slot] below — see OnePlusApp),
 * groups on the physical left, the selected group's numbered channels on the right. Tapping a channel plays it in place.
 * While searching, the list shows every match regardless of group.
 */
@Composable
fun ChannelsScreen(
    channels: List<Channel>, searching: Boolean, playingId: Int, portrait: Boolean,
    onSlot: (Rect) -> Unit, onChannel: (Int) -> Unit,
) {
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    var picked by rememberSaveable { mutableStateOf("") }
    val group = if (picked in groups) picked else groups.firstOrNull().orEmpty()
    val shown = if (searching) channels else channels.filter { it.group == group }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp

    val slot = @Composable { m: Modifier ->
        Box(m.background(Color.Black).onGloballyPositioned { onSlot(it.boundsInRoot()) }, Alignment.Center) {
            if (playingId < 0) OneIconView(OneIcon.Play, Modifier.size(36.dp)) { Color.White.copy(alpha = 0.35f) }
        }
    }
    val panes = @Composable { m: Modifier ->
        Row(m) {
            // channels: first child = the right side in RTL
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(shown, key = { it.id }) { ch -> ChannelRow(ch, ch.id == playingId) { onChannel(ch.id) } } }
            // groups: the left side
            LazyColumn(
                Modifier.width(112.dp).fillMaxHeight(),
                contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(groups, key = { it }) { g -> OneChip(g, !searching && g == group, { picked = g }, Modifier.fillMaxWidth()) } }
        }
    }
    if (portrait) Column(Modifier.fillMaxSize().padding(top = toolbarInset())) {
        slot(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        panes(Modifier.weight(1f))
    } else Row(Modifier.fillMaxSize().padding(top = toolbarInset())) {
        slot(Modifier.weight(1.4f).aspectRatio(16f / 9f))
        panes(Modifier.weight(1f))
    }
}

/** Number badge + name. The playing channel is tinted with the app accent. */
@Composable
private fun ChannelRow(ch: Channel, playing: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().press(onClick).glass(1, 16.dp)
            .then(if (playing) Modifier.background(c.accentSoft).border(0.5.dp, c.accent.copy(alpha = 0.5f), shape) else Modifier)
            .padding(8.dp),
        Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(if (playing) c.accent else c.accentSoft, RoundedCornerShape(11.dp)), Alignment.Center,
        ) { OneText("${ch.number}", OneType.Section, if (playing) c.onAccent else c.accent) }
        OneText(ch.name, OneType.Body, if (playing) c.accent else c.text, Modifier.weight(1f), 1)
    }
}

/** One channel cell (Home section). */
@Composable
internal fun ChannelCard(ch: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(modifier.fillMaxWidth().press(onClick).glass(1, 18.dp).padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(c.accentSoft, RoundedCornerShape(14.dp)), Alignment.Center) {
            OneText("${ch.number}", OneType.Section, c.accent)
        }
        OneText(ch.name, OneType.Body, c.text, maxLines = 1)
    }
}
