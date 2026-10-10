@file:OptIn(UnstableApi::class)

package com.oneplus.app.player

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
        .setUserAgent(
            if (res.url.contains("hakunaymatata") || res.url.contains("aoneroom") || res.url.contains("shalltry")) {
                "okhttp/4.12.0"
            } else {
                res.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: "okhttp/4.12.0"
            }
        )
        .setDefaultRequestProperties(
            res.headers.filterKeys { k ->
                !k.equals("user-agent", true) &&
                !k.equals("accept-encoding", true) &&
                !( (res.url.contains("hakunaymatata") || res.url.contains("aoneroom")) && (k.equals("referer", true) || k.equals("origin", true)) )
            }
        )
        .setConnectTimeoutMs(12_000)
        .setReadTimeoutMs(18_000)
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
    private val policy = DefaultLoadErrorHandlingPolicy(3)
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val net = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if (failed) retry() else if (player.playerError != null) { retries = 0; player.prepare() }
            }
        }
    }
    private val stalls = ArrayDeque<Long>()
    private var wasReady = false
    private val stepDown = Runnable { lower() }
    private var retries = 0
    private val unstick = Runnable { player.seekToDefaultPosition(); player.prepare() }

    val player: ExoPlayer

    init {
        val lowRam = (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowRam) 12_000 else 20_000,
                if (lowRam) 25_000 else if (live) 30_000 else 50_000,
                if (lowRam) 2_000 else 2_500,
                if (lowRam) 4_000 else 5_000,
            )
            .setBackBuffer(if (live) 0 else 10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val selector = DefaultTrackSelector(app, AdaptiveTrackSelection.Factory(15_000, 25_000, 25_000, 0.7f))
        player = ExoPlayer.Builder(app, DefaultRenderersFactory(app).setEnableDecoderFallback(true).forceEnableMediaCodecAsynchronousQueueing())
            .setLoadControl(load)
            .setTrackSelector(selector)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        params = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("ar")
            .setSelectUndeterminedTextLanguage(true)
            .build()
        player.trackSelectionParameters = params
        player.addListener(this)
        player.setMediaSource(build(), if (live || source.startMs <= 0L) C.TIME_UNSET else source.startMs)
        player.prepare()
        player.playWhenReady = true
        runCatching { cm?.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), net) }
    }

    private fun kindOf(url: String) = Util.inferContentType(Uri.parse(url))
    private fun src(url: String, type: Int): MediaSource {
        val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
        val effectiveSubs = if (res.subtitles.isNotEmpty()) {
            val arList = res.subtitles.filter { it.lang.equals("ar", true) || it.name.contains("عرب") || it.name.contains("العربية") }
            val enList = res.subtitles.filter { it.lang.equals("en", true) || it.name.contains("English", true) }
            (arList + enList.take(1)).take(2)
        } else if (!res.subtitleUrl.isNullOrBlank()) {
            listOf(SubtitleTrack("العربية [MovieBox]", "ar", res.subtitleUrl))
        } else {
            emptyList()
        }

        for (sub in effectiveSubs) {
            val mime = if (sub.url.contains(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            val isAr = sub.lang.equals("ar", true) || sub.name.contains("عرب") || sub.name.contains("العربية")
            val b = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(mime)
                .setLanguage(if (isAr) "ar" else sub.lang)
                .setLabel(sub.name)
            if (isAr) b.setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            subConfigs.add(b.build())
        }

        val item = MediaItem.Builder().setUri(url)
            .apply { if (subConfigs.isNotEmpty()) setSubtitleConfigurations(subConfigs) }
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build()).build()
        val drm = drmProvider
        return when (type) {
            C.CONTENT_TYPE_HLS -> {
                val base = HlsMediaSource.Factory(http).setAllowChunklessPreparation(true).setLoadErrorHandlingPolicy(policy)
                    .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
                if (subConfigs.isNotEmpty()) {
                    val subSources = subConfigs.take(2).map { subConfig ->
                        SingleSampleMediaSource.Factory(http).setLoadErrorHandlingPolicy(policy).createMediaSource(subConfig, C.TIME_UNSET)
                    }
                    MergingMediaSource(base, *subSources.toTypedArray())
                } else base
            }
            C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(http).setLoadErrorHandlingPolicy(policy)
                .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            C.CONTENT_TYPE_SS -> SsMediaSource.Factory(DefaultSsChunkSource.Factory(http), http).setLoadErrorHandlingPolicy(policy)
                .apply { drm?.let { setDrmSessionManagerProvider(it) } }.createMediaSource(item)
            C.CONTENT_TYPE_RTSP -> RtspMediaSource.Factory()
                .setUserAgent(res.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: "Mozilla/5.0")
                .createMediaSource(item)
            else -> {
                val factory: DataSource.Factory = if (cache != null) {
                    CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                } else http
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(app, androidx.media3.extractor.DefaultExtractorsFactory())
                    .setDataSourceFactory(factory)
                    .setLoadErrorHandlingPolicy(policy)
                    .apply { drm?.let { setDrmSessionManagerProvider(it) } }
                    .createMediaSource(item)
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

    private fun lower() {
        if (!live && res.variants.size > 1 && variant + 1 < res.variants.size) pickVariant(variant + 1)
    }

    fun release() {
        runCatching { cm?.unregisterNetworkCallback(net) }
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
            val name = Locale.forLanguageTag(tag).getDisplayLanguage(Arabic)
            if (name.isNotBlank()) return name
        }
        return "${app.getString(R.string.track_n)} ${n + 1}"
    }

    private fun mbps(bitrate: Int) = if (bitrate > 0) String.format(Locale.US, "%.1f", bitrate / 1e6) + " Mbps" else null

    private val auto: Boolean get() = params.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }

    private val shownHeight: Int get() = if (res.variants.size > 1) res.variants.getOrNull(variant)?.height ?: 0 else videoSize.height

    val qualityLabel: String
        get() {
            val h = shownHeight
            if (h <= 0) return app.getString(R.string.tab_quality)
            return if (res.variants.size <= 1 && auto && qualities.size > 1) "${app.getString(R.string.track_auto)} · ${h}p" else "${h}p"
        }

    val qualities: List<Opt>
        get() {
            if (res.variants.size > 1) return res.variants.mapIndexed { i, v -> Opt("${v.height}p", null, i == variant) { pickVariant(i) } }
            val best = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
                .filter { (g, i) -> g.getTrackFormat(i).let { it.height > 0 || it.bitrate > 0 } }
                .groupBy { (g, i) -> g.getTrackFormat(i).let { if (it.height > 0) it.height else -(it.bitrate / 100_000) } }
                .values.map { l -> l.maxBy { (g, i) -> g.getTrackFormat(i).bitrate } }
                .sortedByDescending { (g, i) -> g.getTrackFormat(i).let { if (it.height > 0) it.height.toLong() * 100_000_000 else it.bitrate.toLong() } }
            if (best.size < 2) return emptyList()
            val a = auto
            val now = videoSize.height.takeIf { it > 0 }?.let { "${it}p" }
            return listOf(Opt(app.getString(R.string.track_auto), now, a) { setParams { clearOverridesOfType(C.TRACK_TYPE_VIDEO) } }) +
                best.map { (g, i) ->
                    val f = g.getTrackFormat(i)
                    Opt(if (f.height > 0) "${f.height}p" else mbps(f.bitrate) ?: "-", if (f.height > 0) mbps(f.bitrate) else null, !a && g.isTrackSelected(i)) { choose(g, listOf(i)) }
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
                g.mapIndexed { n, t ->
                    val f = t.getTrackFormat(0)
                    val title = f.label?.takeIf { it.isNotBlank() } ?: f.title(n)
                    Opt(title, null, on && t.isSelected) { choose(t, listOf(0)) }
                }
        }

    private companion object {
        val Arabic: Locale = Locale.forLanguageTag("ar")
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
        if (state == Player.STATE_BUFFERING && !live && res.variants.size > 1) handler.postDelayed(stepDown, 20_000) else handler.removeCallbacks(stepDown)
        if (state == Player.STATE_BUFFERING && wasReady && player.playWhenReady && !live && res.variants.size > 1) {
            val now = System.currentTimeMillis()
            stalls.addLast(now)
            while (stalls.isNotEmpty() && now - stalls.first() > 60_000) stalls.removeFirst()
            if (stalls.size >= 3) { stalls.clear(); lower() }
        }
        wasReady = state == Player.STATE_READY
        if (state == Player.STATE_READY) retries = 0
        if (state == Player.STATE_READY && !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) firstFrame = true
        refresh()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    override fun onTracksChanged(tracks: Tracks) { this.tracks = tracks }
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) { params = parameters }
    override fun onCues(cueGroup: CueGroup) { caption = if (cueGroup.cues.isEmpty()) "" else cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n") }
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
                if (retries < (if (live) 12 else if (network) 8 else 0)) {
                    val wait = minOf(1_000L shl minOf(retries, 4), 15_000L)
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
