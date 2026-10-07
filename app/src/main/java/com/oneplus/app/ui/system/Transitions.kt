package com.oneplus.app.ui.system

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oneplus.app.R

enum class PageTransition(@StringRes val label: Int, val opaque: Boolean) {
    Look(R.string.trans_look, false),
    Ios(R.string.trans_ios, true),
    Google(R.string.trans_google, false),
    Slide(R.string.trans_slide, false),
    Zoom(R.string.trans_zoom, true),
    Cover(R.string.trans_cover, true),
    None(R.string.trans_none, false),
}

private val Smooth = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private fun ContentTransform.above() = apply { targetContentZIndex = 1f }

fun pageTransition(kind: PageTransition, dir: Int, own: () -> ContentTransform): ContentTransform = when (kind) {
    PageTransition.Look -> own()
    PageTransition.Ios ->
        (slideInHorizontally(tween(380, easing = Smooth)) { dir * it } togetherWith
            slideOutHorizontally(tween(380, easing = Smooth)) { -dir * it / 3 }).above()
    PageTransition.Google ->
        (fadeIn(tween(210, 90, FastOutSlowInEasing)) + scaleIn(tween(300, 90, FastOutSlowInEasing), 0.92f)) togetherWith
            fadeOut(tween(90))
    PageTransition.Slide ->
        slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it } togetherWith
            slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -dir * it }
    PageTransition.Zoom -> (scaleIn(tween(320, easing = Smooth), 0.86f) togetherWith ExitTransition.None).above()
    PageTransition.Cover -> (slideInHorizontally(tween(340, easing = Smooth)) { dir * it } togetherWith ExitTransition.None).above()
    PageTransition.None -> EnterTransition.None togetherWith ExitTransition.None
}

@Composable
fun AnimatedContentScope.pageBackdrop(kind: PageTransition): Modifier {
    val moving = transition.currentState != transition.targetState
    return if (kind.opaque && moving) Modifier.ambient() else Modifier
}
