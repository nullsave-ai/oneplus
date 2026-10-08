@file:OptIn(UnstableApi::class)

package com.oneplus.app.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.oneplus.app.R
import java.io.File
import java.util.Locale

data class PlaySource(val url: String, val title: String, val live: Boolean, val cacheable: Boolean = false, val startMs: Long = 0L)

class Opt(val label: String, val hint: String?, val selected: Boolean, val onSelect: () -> Unit)

internal object MediaCache {
    private const val MaxBytes = 64L * 1024 * 1024
    @Volatile private var cache: SimpleCache? = null

    fun get(app: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(File(app.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(MaxBytes), StandaloneDatabaseProvider(app))
            .also { cache = it }
    }
}

@Stable
class Playback(
    private val app: Context, val source: PlaySource, private val res: Resolved, private val cache: SimpleCache?,
) : Player.Listener {

    var wantsPlay by mutableStateOf(true); private set
    var buffering by mutableStateOf(true); private set
    var ended by mutableStateOf(false); private set
    var firstFrame by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN); private set
    var durationMs by mutableLongStateOf(0L); private set
    var live by mutableStateOf(source.live || res.live); private set
    var seekable by mutableStateOf(false); private set
    var caption by mutableStateOf(""); private set
    private var tracks by mutableStateOf(Tracks.EMPTY)
    private var params by mutableStateOf(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT)
    private var variant by mutableIntStateOf(res.defaultVariant)

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(res.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: "OnePlus/1.0")
        .setDefaultRequestProperties(res.headers.filterKeys { !it.equals("user-agent", true) && !it.equals("accept-encoding", true) })
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(10_000)
        .setAllowCrossProtocolRedirects(true)

    private val drmProvider: DrmSessionManagerProvider? = res.drm?.let { d ->
        val callback: MediaDrmCallback = d.local?.let { LocalMediaDrmCallback(it) } ?: HttpMediaDrmCallback(d.licenseUrl, http)
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(d.uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(callback)
        DrmSessionManagerProvider { manager }
    }

    private val kind = kindOf(res.url)
    private var guess = 0

    private val handler = Handler(Looper.getMainLooper())
    private var retries = 0
    private val unstick = Runnable { player.seekToDefaultPosition(); player.prepare() }

    val player: ExoPlayer

    init {
        val lowRam = (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (lowRam) 10_000 else 15_000, if (lowRam) 20_000 else 30_000, 1_500, 3_000)
            .setBackBuffer(if (live) 0 else 10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setLoadControl(load)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        params = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("ar")
            .setPreferredAudioLanguage("ar")
            .build()
        player.trackSelectionParameters = params
        player.addListener(this)
        player.setMediaSource(build(), if (live || source.startMs <= 0L) C.TIME_UNSET else source.startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun kindOf(url: String): Int {
        val u = url.lowercase()
        val h = res.headers["type"]?.lowercase() ?: ""
        return when {
            h in setOf("hls", "m3u8") || u.contains(".m3u8") || u.contains("m3u8") || u.contains("dramacardinal") || u.contains("bluetier.top") || u.contains("checklist") -> C.CONTENT_TYPE_HLS
            h in setOf("dash", "mpd") || u.contains(".mpd") || u.contains("dash") -> C.CONTENT_TYPE_DASH
            h in setOf("ss", "ism") || u.contains(".ism") -> C.CONTENT_TYPE_SS
            u.startsWith("rtsp") -> C.CONTENT_TYPE_RTSP
            else -> Util.inferContentType(Uri.parse(url))
        }
    }

    private fun src(url: String, type: Int): MediaSource {
        val subConfigs = res.subtitles.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(if (s.url.contains(".vtt", true)) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(s.language)
                .setLabel(s.label)
                .setSelectionFlags(if (s.language.startsWith("ar", true)) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        val drm = drmProvider
        val drmConfig = res.drm?.let { d ->
            MediaItem.DrmConfiguration.Builder(d.uuid)
                .apply { d.licenseUrl?.let { setLicenseUri(it) } }
                .build()
        }
        val item = MediaItem.Builder().setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
            .setSubtitleConfigurations(subConfigs)
            .apply { drmConfig?.let { setDrmConfiguration(it) } }
            .build()
        return when (type) {
            C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(http).setAllowChunklessPreparation(true)
                .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(http)
                .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            C.CONTENT_TYPE_SS -> SsMediaSource.Factory(DefaultSsChunkSource.Factory(http), http)
                .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            C.CONTENT_TYPE_RTSP -> RtspMediaSource.Factory()
                .setUserAgent(res.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: "OnePlus/1.0")
                .createMediaSource(item)
            else -> {
                val factory: DataSource.Factory = if (cache != null) {
                    CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                } else http
                ProgressiveMediaSource.Factory(factory).apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            }
        }
    }

    private fun build(forced: Int? = null): MediaSource {
        val url = res.variants.getOrNull(variant)?.url ?: res.url
        return src(url, forced ?: kindOf(url))
    }

    fun toggle() {
        if (ended) { player.seekTo(0); player.play(); return }
        if (player.playWhenReady) player.pause() else {
            if (live) player.seekToDefaultPosition()
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

    fun retry() { failed = false; buffering = true; retries = 0; player.prepare(); player.play() }

    fun resumeLive() { if (live) { player.seekToDefaultPosition(); player.play() } }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(this)
        player.release()
    }

    val position: Long get() = player.currentPosition
    val bufferedPosition: Long get() = player.bufferedPosition

    private fun setParams(change: TrackSelectionParameters.Builder.() -> TrackSelectionParameters.Builder) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().change().build()
    }

    private fun choose(g: Tracks.Group, idx: List<Int>) =
        setParams { setTrackTypeDisabled(g.type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, idx)) }

    private fun pickVariant(i: Int) {
        if (i == variant) return
        val pos = player.currentPosition
        variant = i
        player.setMediaSource(build(), pos)
        player.prepare()
    }

    private fun Format.title(n: Int): String {
        label?.let { return it }
        language?.takeIf { it != C.LANGUAGE_UNDETERMINED }?.let { tag ->
            val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale("ar"))
            if (name.isNotBlank()) return name
        }
        return "${app.getString(R.string.track_n)} ${n + 1}"
    }

    private fun mbps(bitrate: Int) = if (bitrate > 0) String.format(Locale.US, "%.1f", bitrate / 1e6) + " Mbps" else null

    val qualities: List<Opt>
        get() {
            if (res.variants.size > 1) return res.variants.mapIndexed { i, v -> Opt("${v.height}p", null, i == variant) { pickVariant(i) } }
            val best = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) && g.getTrackFormat(it).height > 0 }.map { g to it } }
                .groupBy { (g, i) -> g.getTrackFormat(i).height }
                .values.map { l -> l.maxBy { (g, i) -> g.getTrackFormat(i).bitrate } }
            if (best.isEmpty()) return emptyList()
            val auto = params.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }
            return listOf(Opt(app.getString(R.string.track_auto), null, auto) { setParams { clearOverridesOfType(C.TRACK_TYPE_VIDEO) } }) +
                best.map { (g, i) ->
                    val f = g.getTrackFormat(i)
                    Opt("${f.height}p", mbps(f.bitrate), !auto && g.isTrackSelected(i)) { choose(g, listOf(i)) }
                }
        }

    val audios: List<Opt>
        get() = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }.mapIndexed { n, g ->
            val f = g.getTrackFormat(0)
            Opt(f.title(n), if (f.channelCount > 2) "5.1" else null, g.isSelected) {
                choose(g, (0 until g.length).filter { g.isTrackSupported(it) })
            }
        }

    val texts: List<Opt>
        get() {
            val g = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            if (g.isEmpty()) return emptyList()
            val on = !params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && g.any { it.isSelected }
            return listOf(Opt(app.getString(R.string.track_off), null, !on) { setParams { setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) } }) +
                g.mapIndexed { n, t -> Opt(t.getTrackFormat(0).title(n), null, on && t.isSelected) { choose(t, listOf(0)) } }
        }

    private companion object {
        val Guesses = listOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_SS)
        val Parsing = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        wantsPlay = playWhenReady
        if (playWhenReady && live && player.playbackState == Player.STATE_READY) player.seekToDefaultPosition()
    }

    override fun onPlaybackStateChanged(state: Int) {
        buffering = state == Player.STATE_BUFFERING
        ended = state == Player.STATE_ENDED
        if (state == Player.STATE_BUFFERING && live) handler.postDelayed(unstick, 15_000) else handler.removeCallbacks(unstick)
        if (state == Player.STATE_READY) retries = 0
        if (state == Player.STATE_READY && !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) firstFrame = true
        refresh()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    override fun onTracksChanged(tracks: Tracks) { this.tracks = tracks }
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) { params = parameters }
    override fun onCues(cueGroup: CueGroup) { caption = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n") }
    override fun onVideoSizeChanged(videoSize: VideoSize) { this.videoSize = videoSize }
    override fun onRenderedFirstFrame() { firstFrame = true }

    override fun onPlayerError(error: PlaybackException) {
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
            guess < Guesses.size && kind == C.CONTENT_TYPE_OTHER && res.variants.isEmpty() && error.errorCode in Parsing -> {
                player.setMediaSource(build(Guesses[guess++]))
                player.prepare()
            }
            else -> {
                val network = error.errorCode / 1000 == 2
                if (retries < (if (live) 6 else if (network) 3 else 0)) {
                    val wait = minOf(1_000L shl retries, 15_000L)
                    retries++
                    buffering = true
                    handler.postDelayed({ if (live) player.seekToDefaultPosition(); player.prepare() }, wait)
                } else { failed = true; buffering = false }
            }
        }
    }

    private fun refresh() {
        live = source.live || res.live || player.isCurrentMediaItemLive
        val d = player.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        seekable = !live && player.isCurrentMediaItemSeekable && durationMs > 0
    }
}
