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

/** 0 = top of page, 1 = fully scrolled. Continuous in the scroll offset. */
fun scrollTarget(index: Int, offset: Int, range: Float): Float =
    if (index > 0) 1f else (offset / range).coerceIn(0f, 1f)

/**
 * Smooths the scroll target with a spring. Each new target re-aims the running animation with the
 * current velocity, so the morph follows the finger and then settles naturally (no threshold switch).
 * Returns a lambda: read it only in layout/draw phases to avoid recomposition.
 */
@Composable
fun rememberToolbarProgress(target: () -> Float): () -> Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { snapshotFlow(target).collectLatest { a.animateTo(it, spring(stiffness = 300f)) } }
    return { a.value }
}

/** The top edge the app keeps clear of: the status bar, or the camera cut-out once the status bar is hidden. */
@Composable
fun topInset(): Dp = maxOf(
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
)

/** Top inset screens should reserve so content starts below the toolbar (none to speak of in TV Mode: its toolbar only exists while searching). */
@Composable
fun toolbarInset(): Dp = topInset() + if (LocalTvMode.current) 12.dp else if (LocalFeed.current) 64.dp else 84.dp

/**
 * Drop shadow painted only OUTSIDE the pill. A platform elevation shadow is drawn underneath the whole outline and
 * shows through translucent glass as a dark rectangle; clipping the pill out of the shadow avoids that entirely.
 */
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

/** One floating island: width/height/opacity/border/shadow/title scale are all driven by progress and search morph. */
@Composable
fun FloatingToolbar(
    title: String, context: String?, hasSearch: Boolean,
    query: String, onQuery: (String) -> Unit, progress: () -> Float, modifier: Modifier = Modifier,
    startOpen: Boolean = false, onClosed: () -> Unit = {},
) {
    val c = LocalColors.current
    val fx = LocalGlassEffects.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flat = LocalFeed.current // Feed: a full-width solid bar that never shrinks into a pill
    var searching by rememberSaveable { mutableStateOf(startOpen) }
    val morph by animateFloatAsState(if (searching) 1f else 0f, spring(0.82f, 500f), label = "search")
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val close = { fm.clearFocus(); onQuery(""); searching = false; onClosed() }
    BackHandler(searching, close)
    LaunchedEffect(hasSearch) { if (!hasSearch && searching) close() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    val tint = c.glassTint

    Box(
        modifier
            .layout { m, cs ->
                val p = if (flat) 0f else progress() // the flat bar keeps its size while scrolling
                val full = cs.maxWidth
                val compact = if (flat) full else minOf(full, 232.dp.roundToPx())
                val w = lerp(lerp(full, compact, p), full, morph)
                val h = lerp(lerp(56.dp.roundToPx(), 48.dp.roundToPx(), p), 52.dp.roundToPx(), morph)
                val pl = m.measure(Constraints.fixed(w, h))
                layout(full, h) { pl.place((full - w) / 2, 0) }
            }
            .drawBehind {
                if (flat) {
                    drawRect(c.bg)
                    drawRect(c.border.copy(alpha = c.border.alpha * max(progress(), morph)), Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                    return@drawBehind
                }
                val e = max(progress(), morph)
                val r = CornerRadius(24.dp.toPx())
                // Fully transparent at rest; the glass only exists once the page has scrolled / search is open.
                softShadow(r, e)
                drawRoundRect(tint.copy(alpha = e * (if (fx) 0.72f else 0.94f)), cornerRadius = r)
                if (fx) drawRoundRect(Sheen, cornerRadius = r, alpha = e * 0.9f)
                drawRoundRect(c.border.copy(alpha = c.border.alpha * e), cornerRadius = r, style = Stroke(0.5.dp.toPx()))
            }
    ) {
        if (morph < 1f) Row(
            Modifier.fillMaxSize().padding(start = 20.dp, end = if (hasSearch) 8.dp else 20.dp).graphicsLayer { alpha = 1f - morph },
            if (hasSearch || flat) Arrangement.SpaceBetween else Arrangement.Center, Alignment.CenterVertically,
        ) {
            Crossfade(context ?: title, animationSpec = tween(220), label = "title") { t ->
                OneText(t, OneType.Title, c.text, Modifier.graphicsLayer {
                    val s = lerp(1f, 0.85f, progress()); scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                })
            }
            if (hasSearch) Box(
                Modifier.size(44.dp).graphicsLayer { val s = lerp(1f, 0.92f, progress()); scaleX = s; scaleY = s }.press { searching = true },
                Alignment.Center,
            ) { OneIconView(OneIcon.Search) { c.text } }
        }
        if (searching || morph > 0f) Row(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp).graphicsLayer { alpha = morph },
            Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
        ) {
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
            Box(Modifier.size(44.dp).press(close), Alignment.Center) { OneIconView(OneIcon.Close) { c.dim } }
        }
    }
}
