package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Channel

@Composable
fun ChannelsScreen(channels: List<Channel>) {
    val cs = MaterialTheme.colorScheme
    val grouped = remember(channels) { channels.groupBy { it.category }.toList() }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            ScreenTitle(stringResource(R.string.tab_channels))
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                grouped.forEach { (category, list) ->
                    item(key = "h_$category") {
                        Text(category, style = MaterialTheme.typography.titleSmall, color = cs.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
                    }
                    items(list, key = { it.id }) { ch ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(14.dp)).background(cs.surface).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(ch.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface) }
                    }
                }
            }
        }
    }
}
