package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Channel
import com.oneplus.app.ui.system.*

@Composable
fun ChannelsScreen(channels: List<Channel>, wide: Boolean) {
    val c = LocalColors.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (wide) 200.dp else 150.dp), Modifier.widthIn(max = 880.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OneText(stringResource(R.string.tab_channels), OneType.Display, c.text, Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 12.dp))
            }
            items(channels, key = { it.id }) { ch ->
                Row(Modifier.fillMaxWidth().press { }.glass(1, 18.dp).padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(c.accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), Alignment.Center) {
                        OneText(ch.name.take(1), OneType.Section, c.accent)
                    }
                    OneText(ch.name, OneType.Body, c.text, maxLines = 1)
                }
            }
        }
    }
}
