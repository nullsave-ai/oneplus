package com.oneplus.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.launch

const val TelegramUrl = "https://t.me/oneplusnet"

fun openTelegram(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TelegramUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---- when to show it: every launch until skipped; after a skip it rests for 1 launch, then 2 launches on every later skip.
// "Subscribe" ends it for good (membership cannot be checked from here, so tapping it is taken as joining).
private fun prefs(ctx: Context) = ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE)

/** Call once per launch: true = show the dialog now. Counts the resting launches down. */
fun telegramDue(ctx: Context): Boolean {
    val sp = prefs(ctx)
    if (sp.getBoolean("joined", false)) return false
    val wait = sp.getInt("wait", 0)
    if (wait > 0) { sp.edit().putInt("wait", wait - 1).apply(); return false }
    return true
}

fun telegramSkipped(ctx: Context) {
    val sp = prefs(ctx)
    val skips = sp.getInt("skips", 0) + 1
    sp.edit().putInt("skips", skips).putInt("wait", if (skips == 1) 1 else 2).apply()
}

fun telegramJoined(ctx: Context) { prefs(ctx).edit().putBoolean("joined", true).apply() }

/**
 * App-style dialog: dimmed scrim, one card that springs in. Back or a tap outside = skip.
 * The card is a SOLID surface (not glass): a dialog is read on top of whatever page happens to be behind it, and any
 * translucency lets that page show through the text.
 */
@Composable
fun TelegramDialog(onSkip: () -> Unit, onJoin: () -> Unit) {
    val c = LocalColors.current
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val card = RoundedCornerShape(28.dp)
    LaunchedEffect(Unit) { p.animateTo(1f, spring(0.8f, 380f)) }
    val leave: (() -> Unit) -> Unit = { then -> scope.launch { p.animateTo(0f, tween(160)); then() }; Unit }
    BackHandler { leave(onSkip) }

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = p.value }.background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { leave(onSkip) } },
        Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth()
                .graphicsLayer { val s = 0.9f + 0.1f * p.value; scaleX = s; scaleY = s }
                .pointerInput(Unit) { detectTapGestures { } } // taps on the card itself must not dismiss it
                .clip(card).background(c.glass).border(0.5.dp, c.border, card).padding(24.dp),
            Arrangement.spacedBy(12.dp), Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).background(c.accentSoft, CircleShape), Alignment.Center) {
                OneIconView(OneIcon.Send, Modifier.size(32.dp)) { c.accent }
            }
            OneText(stringResource(R.string.tg_title), OneType.Title, c.text, Modifier.padding(top = 4.dp))
            OneText(stringResource(R.string.tg_body), OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
            OneButton(stringResource(R.string.tg_join), OneIcon.Send, { leave(onJoin) }, Modifier.padding(top = 8.dp).fillMaxWidth())
            OneButton(stringResource(R.string.tg_skip), null, { leave(onSkip) }, Modifier.fillMaxWidth(), primary = false)
        }
    }
}
