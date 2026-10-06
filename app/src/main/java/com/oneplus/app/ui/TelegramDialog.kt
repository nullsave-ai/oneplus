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
import androidx.compose.runtime.Composable
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

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

/** Join-the-channel dialog. Back or a tap outside = skip. */
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

/** Asks before wiping the watch history. Back, a tap outside or "إلغاء" all leave the history untouched. */
@Composable
fun ClearHistoryDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalColors.current
    OneDialog(onCancel) { leave ->
        OneText(stringResource(R.string.clear_title), OneType.Title.copy(textAlign = TextAlign.Center), c.text)
        OneText(stringResource(R.string.clear_body), OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
        OneButton(stringResource(R.string.clear_yes), null, { leave(onConfirm) }, Modifier.padding(top = 8.dp).fillMaxWidth())
        OneButton(stringResource(R.string.clear_no), null, { leave(onCancel) }, Modifier.fillMaxWidth().tvAutoFocus(), primary = false) // the remote starts on the safe choice
    }
}

/**
 * Force update modal dialog. Back press is blocked so older versions remain locked until updated.
 */
@Composable
fun ForceUpdateDialog(config: com.oneplus.app.data.AppConfig) {
    androidx.activity.compose.BackHandler(enabled = true) {}
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current

    OneDialog(onDismiss = {}) { _ ->
        Box(Modifier.size(64.dp).background(c.accentSoft, CircleShape), Alignment.Center) {
            OneIconView(OneIcon.Send, Modifier.size(32.dp)) { c.accent }
        }
        OneText(config.updateTitle, OneType.Title.copy(textAlign = TextAlign.Center), c.text, Modifier.padding(top = 4.dp))
        OneText(config.updateMessage, OneType.Body.copy(textAlign = TextAlign.Center), c.dim)
        OneButton(
            text = "تحديث الآن (تحميل أحدث إصدار)",
            icon = OneIcon.Send,
            onClick = {
                val url = config.updateUrl.ifBlank { "https://github.com/nullsave-ai/oneplus/releases/latest" }
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().tvAutoFocus()
        )
    }
}

