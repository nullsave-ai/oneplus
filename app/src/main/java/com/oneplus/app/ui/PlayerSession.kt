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

/**
 * The player's engine, owned by the app root instead of by [PlayerScreen]. The screen is only a window onto it, so leaving
 * the Channels page and coming back does not rebuild anything (no link lookup, no new ExoPlayer, same track choices): the
 * page is "parked" (paused, nothing drawn) while another tab is shown and continues at the live edge when it returns.
 */
@Stable
class PlayerSession internal constructor(val source: PlaySource) {
    val link: Link? = parseLink(source.url)
    var attempt by mutableIntStateOf(if (link != null && Resolver.needsExtractor(link.url)) 1 else 0); internal set // 0 direct · 1 X lookup
    var retryKey by mutableIntStateOf(0); internal set
    var resolveFailed by mutableStateOf(false); internal set
    var playback by mutableStateOf<Playback?>(null); internal set
    internal var wanted by mutableStateOf(false) // the engine is only built once the player is actually going to be shown
    internal var wasParked = false

    val failed: Boolean get() = link == null || resolveFailed || playback?.failed == true

    fun retry() { val p = playback; if (p == null || resolveFailed) retryKey++ else p.retry() }
}

/**
 * Creates the session for [source] (null = nothing to play) and keeps it alive for as long as [source] stays the same.
 * [parked] = the player is not on screen right now (another tab): it stays loaded but silent.
 * [onProgress] = (position, duration) in ms, VOD only: every 10 s and when the player goes away.
 */
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

    // Parked -> shown: build on first show, and for a live stream continue at the live edge (never from the past).
    LaunchedEffect(parked) {
        if (parked) s.wasParked = true
        else {
            s.wanted = true
            if (s.wasParked) { s.wasParked = false; s.playback?.resumeLive() }
        }
    }
    // A parked player is silent, also when its engine finishes building while the user is already on another tab.
    LaunchedEffect(parked, s.playback) { if (parked) s.playback?.pause() }

    // Every link is played as is (headers / DRM from its |options|); only an X post needs one lookup first.
    LaunchedEffect(s, s.attempt, s.retryKey, s.wanted) {
        val link = s.link
        if (link == null || !s.wanted) return@LaunchedEffect
        s.playback = null
        s.resolveFailed = false
        val res = if (s.attempt == 0) Resolved(link.url, s.source.live, link.headers, link.drm) else Resolver.resolve(link.url)?.copy(headers = link.headers)
        if (res == null) { s.resolveFailed = true; return@LaunchedEffect }
        val cache = if (s.attempt == 0 && s.source.cacheable) withContext(Dispatchers.IO) { MediaCache.get(app) } else null
        s.playback = Playback(app, s.source, res, cache)
    }

    // The engine is released as soon as the session goes away (or is rebuilt); VOD reports where it stopped.
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
