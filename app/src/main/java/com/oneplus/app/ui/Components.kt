package com.oneplus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oneplus.app.data.Channel

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun ScreenTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
fun ChannelCard(channel: Channel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val brush = remember(cs.primary, cs.surfaceVariant) {
        Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.28f), cs.surfaceVariant))
    }
    Column(modifier.clip(CardShape).background(cs.surface).border(0.5.dp, cs.outlineVariant, CardShape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(brush), Alignment.Center) {
            Text(channel.name.take(1), style = MaterialTheme.typography.titleLarge, color = cs.primary)
        }
        Column(Modifier.padding(12.dp)) {
            Text(channel.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.category, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
