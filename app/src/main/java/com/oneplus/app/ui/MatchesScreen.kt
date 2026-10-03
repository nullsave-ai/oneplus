package com.oneplus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Match
import com.oneplus.app.ui.system.*

/**
 * Full-screen schedule, opened by a long press on the Home table. [p] (0..1) is owned by OnePlusApp: it also drives the
 * "page steps back" effect of the layer underneath, so opening feels like moving closer to the page rather than a popup.
 */
@Composable
fun MatchesHost(open: Boolean, p: Animatable<Float, AnimationVector1D>, matches: List<Match>, wide: Boolean, onClose: () -> Unit) {
    var height by remember { mutableFloatStateOf(1f) }
    val visible by remember { derivedStateOf { p.value > 0.001f } }
    LaunchedEffect(open) { p.animateTo(if (open) 1f else 0f, spring(1f, 340f)) }
    BackHandler(open, onClose)
    if (!open && !visible) return

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { height = it.height.toFloat() }
            .graphicsLayer {
                val v = p.value
                translationY = (1f - v) * height * 0.14f
                val s = 0.96f + 0.04f * v
                scaleX = s; scaleY = s
                alpha = (v * 2.2f).coerceIn(0f, 1f)
            }
            .ambient()
            .pointerInput(Unit) { detectTapGestures { } } // the page underneath must not receive touches
    ) { MatchesScreen(matches, wide, onClose) }
}

@Composable
private fun MatchesScreen(matches: List<Match>, wide: Boolean, onClose: () -> Unit) {
    val c = LocalColors.current
    var comp by rememberSaveable { mutableStateOf<String?>(null) }
    val competitions = remember(matches) { matches.map { it.competition }.distinct() }
    val days = remember(matches, comp) { matches.filter { comp == null || it.competition == comp }.groupBy { it.day }.toSortedMap() }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // header: back · title + count
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).press(onClose).glass(3, 22.dp), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
            Box(Modifier.weight(1f).height(44.dp).glass(3, 22.dp), Alignment.Center) {
                Row(Modifier.padding(horizontal = 16.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    OneText(stringResource(R.string.sec_matches), OneType.Section, c.text, maxLines = 1)
                    OneDot(c.dim)
                    OneText("${days.values.sumOf { it.size }}", OneType.Section, c.dim)
                }
            }
        }
        // competition filter
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") { OneChip(stringResource(R.string.filter_all), comp == null, { comp = null }) }
            items(competitions, key = { it }) { n -> OneChip(n, comp == n, { comp = n }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 880.dp).fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                days.forEach { (day, list) ->
                    item(key = "h$day") {
                        OneText(stringResource(if (day == 0) R.string.day_today else R.string.day_tomorrow), OneType.Section, c.text, Modifier.padding(horizontal = 24.dp))
                    }
                    item(key = "d$day") { MatchSchedule(list, wide, null) }
                }
            }
        }
    }
}
