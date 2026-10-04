package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
 * groups on the physical left, the selected [group]'s numbered channels on the right. The page starts playing by itself
 * (OnePlusApp picks the channel); tapping a channel switches in place. Tapping a group only browses it: the choice is
 * remembered and the next visit plays that group's first channel. While searching, the list shows every match.
 * [list] belongs to the caller: its scroll position drives the floating toolbar, like on the other pages.
 */
@Composable
fun ChannelsScreen(
    channels: List<Channel>, searching: Boolean, playingId: Int, group: String, portrait: Boolean, list: LazyListState,
    onSlot: (Rect) -> Unit, onGroup: (String) -> Unit, onChannel: (Int) -> Unit,
) {
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val shown = if (searching) channels else channels.filter { it.group == group }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()

    // Bring the playing channel into view the first time, and again only when the group / search changes;
    // coming back from another tab must not move the list the user left.
    var positioned by rememberSaveable { mutableStateOf("") }
    val key = "$group|$searching"
    LaunchedEffect(key, playingId >= 0) {
        // wait for the autoplay to pick a channel, so the list can scroll to it
        if (positioned != key && playingId >= 0) { list.scrollToItem(shown.indexOfFirst { it.id == playingId }.coerceAtLeast(0)); positioned = key }
    }

    // Rounded, never empty-sized: the player is laid exactly over this box, so it must keep a real size in every layout.
    val slot = @Composable { m: Modifier ->
        Box(m.clip(RoundedCornerShape(20.dp)).background(Color.Black).onGloballyPositioned { onSlot(it.boundsInRoot()) })
    }
    val panes = @Composable { m: Modifier ->
        Row(m) {
            // channels: first child = the right side in RTL
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(), state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { items(shown, key = { it.id }) { ch -> ChannelRow(ch, ch.id == playingId) { onChannel(ch.id) } } }
            // groups: the left side
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
            // Landscape: as wide as the height allows (the floating nav island sits above it), at most half the width.
            val w = minOf(maxWidth * 0.5f, (maxHeight - 20.dp) * (16f / 9f))
            Row(Modifier.fillMaxSize()) {
                slot(Modifier.padding(start = 16.dp, top = 4.dp).width(w).aspectRatio(16f / 9f))
                panes(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Channels page for TV Mode and the Feed style. Landscape / TV: the player with the groups under it in one column, and the channel
 * cards in as many columns as fit next to it; there is no toolbar above, so the whole height is used and nothing is left empty.
 * The cards start clear of the rail but slide under it. Portrait: the player on top, the groups as a row of chips, then the cards.
 * Same contract as [ChannelsScreen]: the one player is drawn over the [onSlot] box; [list] drives the toolbar.
 */
@Composable
fun FeedChannels(
    channels: List<Channel>, searching: Boolean, playingId: Int, group: String, portrait: Boolean, list: LazyListState,
    onSlot: (Rect) -> Unit, onGroup: (String) -> Unit, onChannel: (Int) -> Unit,
) {
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val shown = if (searching) channels else channels.filter { it.group == group }
    val inset = LocalRailInset.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    val slot = @Composable { m: Modifier -> Box(m.clip(RoundedCornerShape(20.dp)).background(Color.Black).onGloballyPositioned { onSlot(it.boundsInRoot()) }) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = toolbarInset())) {
        val w = minOf(maxWidth * 0.44f, maxHeight * 0.62f * (16f / 9f))
        val cols = if (portrait) (maxWidth / 170.dp).toInt().coerceIn(1, 3) else ((maxWidth - w - inset) / 150.dp).toInt().coerceAtLeast(1)
        var positioned by rememberSaveable { mutableStateOf("") }
        val key = "$group|$searching"
        LaunchedEffect(key, playingId >= 0) { // the playing channel is brought into view once per group / search, never when coming back
            if (positioned != key && playingId >= 0) { list.scrollToItem(shown.indexOfFirst { it.id == playingId }.coerceAtLeast(0) / cols); positioned = key }
        }
        val cards = @Composable { m: Modifier ->
            LazyColumn(
                m, state = list, verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues.Absolute(left = 12.dp + inset, right = 12.dp, top = 8.dp, bottom = bottom),
            ) {
                items(shown.chunked(cols), key = { it.first().id }) { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        row.forEach { ch -> Box(Modifier.weight(1f)) { ChannelRow(ch, ch.id == playingId) { onChannel(ch.id) } } }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (portrait) Column(Modifier.fillMaxSize()) {
            slot(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().aspectRatio(16f / 9f))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it }) { g -> OneChip(g, !searching && g == group, { onGroup(g) }) }
            }
            cards(Modifier.weight(1f))
        } else Row(Modifier.fillMaxSize()) {
            // first child = the physical right in RTL: the player over its groups; the cards fill the rest, under the rail
            Column(Modifier.width(w)) {
                slot(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp).fillMaxWidth().aspectRatio(16f / 9f))
                LazyColumn(
                    Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { items(groups, key = { it }) { g -> OneChip(g, !searching && g == group, { onGroup(g) }, Modifier.fillMaxWidth()) } }
            }
            cards(Modifier.weight(1f))
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
