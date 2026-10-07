package com.oneplus.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

const val TelegramUrl = "https://t.me/oneplusnet"

fun openTelegram(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TelegramUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("telegram", Context.MODE_PRIVATE)

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

@Composable
fun TelegramDialog(onSkip: () -> Unit, onJoin: () -> Unit) {
    val c = LocalColors.current
    OneDialog(onSkip) { leave ->
        Box(Modifier.size(64.dp).background(c.accentSoft, CircleShape), Alignment.Center) {
            OneIconView(OneIcon.Send, Modifier.size(32.dp)) { c.accent }
        }
        OneText(stringResource(R.string.tg_title), OneType.Title, c.text, Modifier.padding(top = 4.dp))
        OneText(stringResource(R.string.tg_body), OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
        OneButton(stringResource(R.string.tg_join), OneIcon.Send, { leave(onJoin) }, Modifier.padding(top = 8.dp).fillMaxWidth().tvAutoFocus())
        OneButton(stringResource(R.string.tg_skip), null, { leave(onSkip) }, Modifier.fillMaxWidth(), primary = false)
    }
}

@Composable
fun ClearHistoryDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalColors.current
    OneDialog(onCancel) { leave ->
        OneText(stringResource(R.string.clear_title), OneType.Title.copy(textAlign = TextAlign.Center), c.text)
        OneText(stringResource(R.string.clear_body), OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
        OneButton(stringResource(R.string.clear_yes), null, { leave(onConfirm) }, Modifier.padding(top = 8.dp).fillMaxWidth())
        OneButton(stringResource(R.string.clear_no), null, { leave(onCancel) }, Modifier.fillMaxWidth().tvAutoFocus(), primary = false)
    }
}
