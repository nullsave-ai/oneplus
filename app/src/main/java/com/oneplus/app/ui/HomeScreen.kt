package com.oneplus.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R

@Composable
fun HomeScreen(state: UiState, onQuery: (String) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        HomeHeader(searching, state.query, onQuery) { searching = !searching; if (!searching) onQuery("") }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { items(state.channels, key = { it.id }) { ChannelCard(it) } }
    }
}

@Composable
private fun HomeHeader(searching: Boolean, query: String, onQuery: (String) -> Unit, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.10f), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            IconButton(onToggle) {
                Icon(Icons.Rounded.Search, stringResource(R.string.search), tint = if (searching) cs.primary else cs.onSurface)
            }
        }
        AnimatedVisibility(searching) {
            TextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}
