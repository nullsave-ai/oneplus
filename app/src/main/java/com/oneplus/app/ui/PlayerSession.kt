package com.oneplus.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.oneplus.app.player.Link
import com.oneplus.app.player.MediaCache
import com.oneplus.app.player.PlaySource
import com.oneplus.app.player.Playback
import com.oneplus.app.player.Resolved
import com.oneplus.app.player.Resolver
import com.oneplus.app.player.parseLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Stable
class PlayerSession internal constructor(val source: PlaySource) {
    val link: Link? = parseLink(source.url)
    var attempt by mutableIntStateOf(if (link != null && Resolver.needsExtractor(link.url)) 1 else 0); internal set
    var retryKey by mutableIntStateOf(0); internal set
    var resolveFailed by mutableStateOf(false); internal set
    var playback by mutableStateOf<Playback?>(null); internal set
    internal var wanted by mutableStateOf(false)
    internal var wasParked = false

    val failed: Boolean get() = link == null || resolveFailed || playback?.failed == true

    fun retry() { val p = playback; if (p == null || resolveFailed) retryKey++ else p.retry() }
}

@Composable
fun rememberPlayerSession(source: PlaySource?, parked: Boolean, onProgress: ((Long, Long) -> Unit)?): PlayerSession? {
    val session = remember(source) { source?.let { PlayerSession(it) } }
    if (session != null) key(session) { SessionEffects(session, parked, onProgress) }
    return session
}

@Composable
private fun SessionEffects(s: PlayerSession, parked: Boolean, onProgress: ((Long, Long) -> Unit)?) {
    val app = LocalContext.current.applicationContext
    val report by rememberUpdatedState(onProgress)

    LaunchedEffect(parked) {
        if (parked) s.wasParked = true
        else {
            s.wanted = true
            if (s.wasParked) { s.wasParked = false; s.playback?.resumeLive() }
        }
    }
    LaunchedEffect(parked, s.playback) { if (parked) s.playback?.pause() }

    LaunchedEffect(s, s.attempt, s.retryKey, s.wanted) {
        val link = s.link
        if (link == null || !s.wanted) return@LaunchedEffect
        s.playback = null
        s.resolveFailed = false
        val resolved = if (s.attempt == 0) null else Resolver.resolve(link.url)
        val res = if (s.attempt == 0) {
            Resolved(link.url, s.source.live, link.headers, link.drm, subtitleUrl = link.subtitleUrl)
        } else {
            resolved?.copy(
                headers = resolved.headers + link.headers,
                subtitleUrl = link.subtitleUrl ?: resolved.subtitleUrl,
                live = s.source.live || resolved.live
            )
        }
        if (res == null) { s.resolveFailed = true; return@LaunchedEffect }
        val cache = if (s.attempt == 0 && s.source.cacheable) withContext(Dispatchers.IO) { MediaCache.get(app) } else null
        s.playback = Playback(app, s.source, res, cache)
    }

    val pb = s.playback
    DisposableEffect(pb) {
        onDispose {
            if (pb != null && !pb.live && pb.durationMs > 0) report?.invoke(pb.position, pb.durationMs)
            pb?.release()
        }
    }
    LaunchedEffect(pb) {
        if (pb == null) return@LaunchedEffect
        while (true) { delay(10_000); if (!pb.live && pb.durationMs > 0) report?.invoke(pb.position, pb.durationMs) }
    }
}
