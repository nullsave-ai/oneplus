@file:OptIn(UnstableApi::class)

package com.oneplus.app.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

/** What to play. [live] = channel (no cache, no seeking); otherwise a movie (cache + seeking). */
data class PlaySource(val url: String, val title: String, val live: Boolean)

/**
 * The only way a URL reaches the player. Catalogue data is untrusted: anything other than http(s) with a host
 * (file://, content://, data:, android.resource:// ...) is rejected, so a poisoned entry can't make the app read local content.
 */
fun PlaySource.toUriOrNull(): Uri? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) uri else null
}

/** One small LRU disk cache, used for progressive (MP4) VODs only. Live streams and HLS/DASH never touch it. */
internal object MediaCache {
    private const val MaxBytes = 64L * 1024 * 1024
    @Volatile private var cache: SimpleCache? = null

    /** Does disk IO: call off the main thread. */
    fun get(app: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(File(app.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(MaxBytes), StandaloneDatabaseProvider(app))
            .also { cache = it }
    }
}

/**
 * Owns one ExoPlayer and exposes its state as Compose state. Created when the player screen opens and
 * [release]d the moment it closes, so nothing (decoders, sockets, buffers) outlives the screen.
 */
@Stable
class Playback(app: Context, val source: PlaySource, private val uri: Uri, private val cache: SimpleCache?) : Player.Listener {

    var wantsPlay by mutableStateOf(true); private set
    var buffering by mutableStateOf(true); private set
    var ended by mutableStateOf(false); private set
    var firstFrame by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN); private set
    var durationMs by mutableLongStateOf(0L); private set
    var live by mutableStateOf(source.live); private set
    var seekable by mutableStateOf(false); private set

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent("OnePlus/1.0")
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(10_000)
        .setAllowCrossProtocolRedirects(true) // IPTV links commonly bounce http -> https

    private val kind = Util.inferContentType(uri)
    private var triedHls = false

    val player: ExoPlayer

    init {
        val lowRam = (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        // Modest buffers: enough to ride out jitter, small enough not to burn data/RAM. ABR (default selector) adapts quality.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (lowRam) 10_000 else 15_000, if (lowRam) 20_000 else 30_000, 1_500, 3_000)
            .setBackBuffer(if (source.live) 0 else 10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(this)
        player.setMediaSource(mediaSource(kind))
        player.prepare()
        player.playWhenReady = true
    }

    private fun mediaSource(type: Int): MediaSource {
        val item = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build()).build()
        return when (type) {
            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(http).setAllowChunklessPreparation(true).createMediaSource(item)
            C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(http).createMediaSource(item)
            else -> {
                val factory: DataSource.Factory = if (cache != null) {
                    CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                } else http
                ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            }
        }
    }

    // ---- controls ----
    fun toggle() {
        if (ended) { player.seekTo(0); player.play(); return }
        if (player.playWhenReady) player.pause() else {
            if (live) player.seekToDefaultPosition() // resuming a paused live stream jumps back to the live edge
            player.play()
        }
    }

    fun pause() = player.pause()

    fun seekBy(deltaMs: Long) {
        if (!seekable) return
        val end = if (durationMs > 0) durationMs else Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, end))
    }

    fun seekTo(ms: Long) { if (seekable) player.seekTo(ms.coerceAtLeast(0L)) }

    fun retry() { failed = false; buffering = true; player.prepare(); player.play() }

    fun release() {
        player.removeListener(this)
        player.release()
    }

    val position: Long get() = player.currentPosition
    val bufferedPosition: Long get() = player.bufferedPosition

    // ---- Player.Listener ----
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { wantsPlay = playWhenReady }

    override fun onPlaybackStateChanged(state: Int) {
        buffering = state == Player.STATE_BUFFERING
        ended = state == Player.STATE_ENDED
        if (state == Player.STATE_READY && !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) firstFrame = true // audio-only
        refresh()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    override fun onVideoSizeChanged(videoSize: VideoSize) { this.videoSize = videoSize }
    override fun onRenderedFirstFrame() { firstFrame = true }

    override fun onPlayerError(error: PlaybackException) {
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
            // A link without an extension that is really an HLS playlist: sniff once, then retry as HLS.
            !triedHls && kind == C.CONTENT_TYPE_OTHER && (
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                ) -> {
                triedHls = true
                player.setMediaSource(mediaSource(C.CONTENT_TYPE_HLS))
                player.prepare()
            }
            else -> { failed = true; buffering = false }
        }
    }

    private fun refresh() {
        live = source.live || player.isCurrentMediaItemLive
        val d = player.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        seekable = !live && player.isCurrentMediaItemSeekable && durationMs > 0
    }
}
