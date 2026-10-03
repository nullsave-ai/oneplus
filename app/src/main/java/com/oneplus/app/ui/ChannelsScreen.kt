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

/**
 * TV-style page: the player sits at the top (it is the app's one player, drawn over the empty [slot] below — see OnePlusApp),
 * groups on the physical left, the selected group's numbered channels on the right. Tapping a channel plays it in place.
 * The page opens on [current] (the channel picked this session, else the first one): its group is shown and the list is
 * scrolled to it. While searching, the list shows every match regardless of group.
 * [list] belongs to the caller: its scroll position drives the floating toolbar, like on the other pages.
 */
@Composable
fun ChannelsScreen(
    channels: List<Channel>, searching: Boolean, playingId: Int, current: Int, portrait: Boolean, list: LazyListState,
    onSlot: (Rect) -> Unit, onChannel: (Int) -> Unit,
) {
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    var picked by rememberSaveable(current) { mutableStateOf("") } // a tapped group lasts until the current channel changes
    val selected = channels.firstOrNull { it.id == current } ?: channels.firstOrNull()
    val group = if (picked in groups) picked else selected?.group ?: groups.firstOrNull().orEmpty()
    val shown = if (searching) channels else channels.filter { it.group == group }
    val active = if (playingId >= 0) playingId else selected?.id ?: -1
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp

    // Bring the current channel into view the first time, and again only when the group / search changes;
    // coming back from another tab must not move the list the user left.
    var positioned by rememberSaveable { mutableStateOf("") }
    val key = "$group|$searching"
    LaunchedEffect(key) {
        if (positioned != key) { list.scrollToItem(shown.indexOfFirst { it.id == active }.coerceAtLeast(0)); positioned = key }
    }

    // Rounded, never empty-sized: the player is laid exactly over this box, so it must keep a real size in every layout.
    val slot = @Composable { m: Modifier ->
        Box(
            m.then(if (playingId < 0 && selected != null) Modifier.press { onChannel(selected.id) } else Modifier)
                .clip(RoundedCornerShape(20.dp)).background(Color.Black).onGloballyPositioned { onSlot(it.boundsInRoot()) },
            Alignment.Center,
        ) {
            if (playingId < 0) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OneIconView(OneIcon.Play, Modifier.size(36.dp)) { Color.White.copy(alpha = 0.55f) }
                if (selected != null) OneText(selected.name, OneType.Caption, Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
    val panes = @Composable { m: Modifier ->
        Row(m) {
            // channels: first child = the right side in RTL
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(shown, key = { it.id }) { ch -> ChannelRow(ch, ch.id == active) { onChannel(ch.id) } } }
            // groups: the left side
            LazyColumn(
                Modifier.width(112.dp).fillMaxHeight(),
                contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(groups, key = { it }) { g -> OneChip(g, !searching && g == group, { picked = g }, Modifier.fillMaxWidth()) } }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = toolbarInset())) {
        if (portrait) Column(Modifier.fillMaxSize()) {
            slot(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().aspectRatio(16f / 9f))
            panes(Modifier.weight(1f))
        } else {
            // Landscape: the player is as wide as the height allows (never taller than the page), at most half the width.
            val w = minOf(maxWidth * 0.5f, (maxHeight - 20.dp) * (16f / 9f))
            Row(Modifier.fillMaxSize()) {
                slot(Modifier.padding(start = 16.dp, top = 4.dp).width(w).aspectRatio(16f / 9f))
                panes(Modifier.weight(1f))
            }
        }
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
