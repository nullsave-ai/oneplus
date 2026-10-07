package com.oneplus.app.ui.system

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import com.oneplus.app.R
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

fun scrollTarget(index: Int, offset: Int, range: Float): Float =
    if (index > 0) 1f else (offset / range).coerceIn(0f, 1f)

@Composable
fun rememberToolbarProgress(target: () -> Float): () -> Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { snapshotFlow(target).collectLatest { a.animateTo(it, spring(stiffness = 300f)) } }
    return { a.value }
}

@Composable
fun topInset(): Dp = maxOf(
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
)

@Composable
fun rememberToolbarReveal(key: Any, pos: () -> Int): () -> Float {
    var shown by remember { mutableStateOf(true) }
    val a = animateFloatAsState(if (shown) 1f else 0f, spring(0.9f, 420f), label = "reveal")
    LaunchedEffect(key) {
        shown = true
        var anchor = pos()
        snapshotFlow(pos).collect { p ->
            if (p < 48) { shown = true; anchor = p }
            else if (p - anchor > 24) { shown = false; anchor = p }
            else if (anchor - p > 24) { shown = true; anchor = p }
        }
    }
    return { a.value }
}

@Composable
fun toolbarInset(): Dp = topInset() + LocalLook.current.top + LocalTvNavHeight.current

class Search(val open: Boolean, val morph: Float, val focus: FocusRequester, val show: () -> Unit, val close: () -> Unit)

@Composable
fun rememberSearch(hasSearch: Boolean, onQuery: (String) -> Unit): Search {
    var searching by rememberSaveable { mutableStateOf(false) }
    val morph by animateFloatAsState(if (searching) 1f else 0f, spring(0.82f, 500f), label = "search")
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val close = { fm.clearFocus(); onQuery(""); searching = false }
    BackHandler(searching, close)
    LaunchedEffect(hasSearch) { if (!hasSearch && searching) close() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    return Search(searching, morph, focus, { searching = true }, close)
}

@Composable
fun SearchRow(query: String, onQuery: (String) -> Unit, focus: FocusRequester, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier, Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
        OneIconView(OneIcon.Search) { c.accent }
        BasicTextField(
            query, onQuery, Modifier.weight(1f).focusRequester(focus), singleLine = true,
            textStyle = OneType.Body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) OneText(stringResource(R.string.search_hint), OneType.Body, c.dim)
                    inner()
                }
            },
        )
        Box(Modifier.size(44.dp).press(onClose), Alignment.Center) { OneIconView(OneIcon.Close) { c.dim } }
    }
}

internal fun DrawScope.softShadow(r: CornerRadius, e: Float) {
    if (e < 0.01f) return
    val hole = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
    clipPath(hole, ClipOp.Difference) {
        val layers = 6
        for (i in 1..layers) {
            val grow = i * 1.6.dp.toPx()
            drawRoundRect(
                Color.Black.copy(alpha = 0.06f * e * (1f - i / (layers + 1f))),
                Offset(-grow, -grow + 4.dp.toPx()), Size(size.width + grow * 2f, size.height + grow * 2f),
                CornerRadius(r.x + grow),
            )
        }
    }
}

@Composable
fun FloatingToolbar(
    title: String, context: String?, hasSearch: Boolean,
    query: String, onQuery: (String) -> Unit, progress: () -> Float, modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val c = LocalColors.current
    val solid = LocalSolid.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val s = rememberSearch(hasSearch, onQuery)
    val morph = s.morph
    val tint = if (solid) c.bar else c.glassTint

    Box(
        modifier
            .layout { m, cs ->
                val p = progress()
                val full = cs.maxWidth
                val compact = minOf(full, 232.dp.roundToPx())
                val w = lerp(lerp(full, compact, p), full, morph)
                val h = lerp(lerp(56.dp.roundToPx(), 48.dp.roundToPx(), p), 52.dp.roundToPx(), morph)
                val pl = m.measure(Constraints.fixed(w, h))
                layout(full, h) { pl.place((full - w) / 2, 0) }
            }
            .drawBehind {
                val e = if (solid) 1f else max(progress(), morph)
                val r = CornerRadius(24.dp.toPx())
                if (!solid) softShadow(r, e)
                drawRoundRect(tint.copy(alpha = e * (if (solid) 1f else 0.72f)), cornerRadius = r)
                if (!solid) drawRoundRect(Sheen, cornerRadius = r, alpha = e * 0.9f)
                drawRoundRect(c.border.copy(alpha = c.border.alpha * e), cornerRadius = r, style = Stroke(0.5.dp.toPx()))
            }
    ) {
        if (morph < 1f) Row(
            Modifier.fillMaxSize().padding(start = if (onBack != null) 6.dp else 20.dp, end = if (hasSearch) 8.dp else 20.dp).graphicsLayer { alpha = 1f - morph },
            if (hasSearch) Arrangement.SpaceBetween else Arrangement.Center, Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) Box(Modifier.size(44.dp).press(onBack), Alignment.Center) { OneIconView(OneIcon.Back) { c.text } }
                Crossfade(context ?: title, animationSpec = tween(220), label = "title") { t ->
                    OneText(t, OneType.Title, c.text, Modifier.graphicsLayer {
                        val k = lerp(1f, 0.85f, progress()); scaleX = k; scaleY = k
                        transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                    })
                }
            }
            if (hasSearch) Box(
                Modifier.size(44.dp).graphicsLayer { val k = lerp(1f, 0.92f, progress()); scaleX = k; scaleY = k }.press(s.show),
                Alignment.Center,
            ) { OneIconView(OneIcon.Search) { c.text } }
        }
        if (s.open || morph > 0f) SearchRow(query, onQuery, s.focus, s.close, Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp).graphicsLayer { alpha = morph })
    }
}
