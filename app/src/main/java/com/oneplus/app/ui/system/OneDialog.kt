package com.oneplus.app.ui.system

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * App-style dialog: dimmed scrim, one card that springs in. Back or a tap outside = [onDismiss].
 * The card is a SOLID surface (not glass): a dialog is read on top of whatever page happens to be behind it, and any
 * translucency lets that page show through the text.
 *
 * [content] gets `leave`: run any action through it (`leave(onConfirm)`) so the card animates out before the action fires.
 */
@Composable
fun OneDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.(leave: (() -> Unit) -> Unit) -> Unit) {
    val c = LocalColors.current
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val card = RoundedCornerShape(28.dp)
    LaunchedEffect(Unit) { p.animateTo(1f, spring(0.8f, 380f)) }
    val leave: (() -> Unit) -> Unit = { then -> scope.launch { p.animateTo(0f, tween(160)); then() }; Unit }
    BackHandler { leave(onDismiss) }

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = p.value }.background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { leave(onDismiss) } },
        Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth()
                .graphicsLayer { val s = 0.9f + 0.1f * p.value; scaleX = s; scaleY = s }
                .pointerInput(Unit) { detectTapGestures { } } // taps on the card itself must not dismiss it
                .clip(card).background(c.glass).border(0.5.dp, c.border, card).padding(24.dp),
            Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally,
        ) { content(leave) }
    }
}
