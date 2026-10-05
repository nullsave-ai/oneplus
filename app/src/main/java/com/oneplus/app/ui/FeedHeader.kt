package com.oneplus.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.oneplus.app.ui.system.*
import kotlin.math.max

/**
 * The Feed look's header. At the top of a page it is a large serif title on the page itself (no bar). While the page scrolls it
 * shrinks to a compact title and turns solid; scrolling further down slides it away, the first scroll up brings it back.
 * The strip behind the status bar (or the camera cut-out, when the status bar is hidden) is painted with the page colour as soon
 * as the page moves, so content never shows through behind the clock.
 */
@Composable
fun FeedHeader(a: HeaderArgs, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val s = rememberSearch(a.hasSearch, false, a.onQuery) {}
    val morph = s.morph
    val inset = topInset()
    Box(modifier.fillMaxWidth()) {
        // The bar. It is declared first, so when it slides away it passes UNDER the strip below.
        Box(
            Modifier.padding(top = inset).fillMaxWidth()
                .layout { m, cs ->
                    val h = lerp(68.dp.roundToPx(), 52.dp.roundToPx(), a.collapse().coerceIn(0f, 1f))
                    val pl = m.measure(Constraints.fixed(cs.maxWidth, h))
                    layout(cs.maxWidth, h) { pl.place(0, 0) }
                }
                .graphicsLayer { translationY = -(1f - max(a.reveal(), morph)) * size.height }
                .drawBehind {
                    val e = max(a.collapse(), morph)
                    drawRect(c.bg.copy(alpha = e))
                    drawRect(c.border.copy(alpha = c.border.alpha * e), Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
                }
        ) {
            if (morph < 1f) Row(
                Modifier.fillMaxSize().padding(start = if (a.onBack != null) 12.dp else 20.dp, end = 16.dp).graphicsLayer { alpha = 1f - morph },
                Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    a.onBack?.let { back ->
                        Box(
                            Modifier.size(40.dp).press { if (a.reveal() > 0.6f) back() }.border(0.5.dp, c.border, CircleShape),
                            Alignment.Center,
                        ) { OneIconView(OneIcon.Back) { c.text } }
                    }
                    Crossfade(a.context ?: a.title, animationSpec = tween(220), label = "title") { t ->
                        OneText(t, OneType.LuxHero, c.text, Modifier.graphicsLayer {
                            val k = lerp(1f, 0.68f, a.collapse()); scaleX = k; scaleY = k
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        }, 1)
                    }
                }
                if (a.hasSearch) Box(
                    Modifier.size(44.dp).press { if (a.reveal() > 0.6f) s.show() }.border(0.5.dp, c.border, CircleShape),
                    Alignment.Center,
                ) { OneIconView(OneIcon.Search) { c.text } }
            }
            if (s.open || morph > 0f) SearchRow(
                a.query, a.onQuery, s.focus, s.close,
                Modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp).graphicsLayer { alpha = morph },
            )
        }
        // The status-bar strip: transparent at the top of the page, solid from the first scroll on.
        Box(Modifier.fillMaxWidth().height(inset).graphicsLayer { alpha = max(a.collapse(), morph) }.background(c.bg))
    }
}
