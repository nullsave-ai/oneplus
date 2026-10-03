@file:OptIn(UnstableApi::class)

package com.oneplus.app.player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
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
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File
import java.util.Locale
import java.util.UUID

/** What to play. [live] = channel (no cache, no seeking); otherwise a movie (cache + seeking). */
data class PlaySource(val url: String, val title: String, val live: Boolean)

/** DRM for one stream: [url] = license server, [keys] = ready-made ClearKey license (when the link carries kid:key pairs). */
class Drm(val uuid: UUID, val url: String?, val keys: ByteArray?)

/** A validated, ready-to-play link: address + request headers + DRM + external subtitles. */
class Stream(val uri: Uri, val ua: String, val headers: Map<String, String>, val drm: Drm?, val subs: List<Uri>)

private const val DefaultUa = "OnePlus/1.0"
private val Schemes = setOf("http", "https", "rtsp")
private val HeaderName = Regex("[A-Za-z0-9-]{1,40}")
private val BlockedHeaders = setOf("host", "content-length", "connection", "transfer-encoding", "upgrade", "te")
private val Hex32 = Regex("[0-9a-fA-F]{32}")

private fun webUri(s: String): Uri? = runCatching { Uri.parse(s.trim()) }.getOrNull()
    ?.takeIf { (it.scheme.equals("http", true) || it.scheme.equals("https", true)) && !it.host.isNullOrBlank() }

/** "kid:key,kid:key" (hex) -> ClearKey license JSON, or null when malformed. */
private fun clearKeyJson(spec: String): ByteArray? {
    val pairs = spec.split(',').map { it.trim().split(':') }
    if (pairs.isEmpty() || pairs.any { it.size != 2 || !Hex32.matches(it[0].replace("-", "")) || !Hex32.matches(it[1]) }) return null
    fun b64(hex: String) = Base64.encodeToString(
        hex.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
    )
    val keys = pairs.joinToString(",") { "{\"kty\":\"oct\",\"k\":\"${b64(it[1])}\",\"kid\":\"${b64(it[0])}\"}" }
    return "{\"type\":\"temporary\",\"keys\":[$keys]}".toByteArray()
}

/**
 * The only way a URL reaches the player. Catalogue data is untrusted: only http(s)/rtsp with a host is accepted
 * (file://, content://, data:, android.resource:// ... are rejected), header names/values are sanitised, license
 * servers and subtitles must be http(s).
 *
 * Extra keys ride on the link, Kodi style: `url|User-Agent=..|Referer=..|Origin=..|Cookie=..|Authorization=..`
 * (any other `Name=value` becomes a request header; `&` also separates; percent-encode `&` and `|` inside values), plus
 *   drm=widevine|playready|clearkey   license_key=<license url | kid:key[,kid:key]>   sub=<subtitle url> (repeatable)
 */
fun PlaySource.parse(): Stream? {
    val parts = url.trim().split('|')
    val uri = runCatching { Uri.parse(parts[0].trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme !in Schemes || uri.host.isNullOrBlank()) return null

    var ua = DefaultUa
    var drmType: String? = null
    var license: String? = null
    val headers = linkedMapOf<String, String>()
    val subs = mutableListOf<Uri>()
    for (kv in parts.drop(1).flatMap { it.split('&') }) {
        val i = kv.indexOf('=')
        if (i <= 0) continue
        val k = kv.substring(0, i).trim()
        val v = Uri.decode(kv.substring(i + 1)).trim()
        when (k.lowercase().replace('_', '-')) {
            "user-agent", "useragent", "ua" -> if (v.none { it < ' ' }) ua = v
            "drm", "drm-scheme", "license-type" -> drmType = v.lowercase()
            "license-key", "drm-license", "license" -> license = v
            "sub", "subtitle" -> webUri(v)?.let { if (subs.size < 8) subs += it }
            else -> {
                val name = if (k.equals("referrer", true)) "Referer" else k
                if (HeaderName.matches(name) && name.lowercase() !in BlockedHeaders && v.length <= 2048 &&
                    v.none { it < ' ' } && headers.size < 16
                ) headers[name] = v
            }
        }
    }

    val lic = license
    val drm = if (drmType == null && lic == null) null else {
        val t = drmType.orEmpty()
        val uuid = when {
            "clear" in t -> C.CLEARKEY_UUID
            "play" in t -> C.PLAYREADY_UUID
            "wide" in t -> C.WIDEVINE_UUID
            lic?.startsWith("http", true) == true -> C.WIDEVINE_UUID
            else -> C.CLEARKEY_UUID
        }
        when {
            lic == null -> Drm(uuid, null, null)
            lic.startsWith("http", true) -> Drm(uuid, (webUri(lic) ?: return null).toString(), null)
            uuid == C.CLEARKEY_UUID -> Drm(uuid, null, clearKeyJson(lic) ?: return null)
            else -> return null
        }
    }
    return Stream(uri, ua, headers, drm, subs)
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

/** One selectable track entry. [label] null = the "automatic" (video) / "off" (text) entry. */
class TrackOpt(val label: String?, val on: Boolean, val pick: () -> Unit)

private val Sniffable = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
)

private class T(val g: Tracks.Group, val i: Int) {
    val f: Format get() = g.getTrackFormat(i)
    val sel: Boolean get() = g.isTrackSelected(i)
}

/**
 * Owns one ExoPlayer and exposes its state as Compose state. Created when the player screen opens and
 * [release]d the moment it closes, so nothing (decoders, sockets, buffers) outlives the screen.
 */
@Stable
class Playback(app: Context, val source: PlaySource, private val stream: Stream, private val cache: SimpleCache?) : Player.Listener {

    var wantsPlay by mutableStateOf(true); private set
    var buffering by mutableStateOf(true); private set
    var ended by mutableStateOf(false); private set
    var firstFrame by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN); private set
    var durationMs by mutableLongStateOf(0L); private set
    var live by mutableStateOf(source.live); private set
    var seekable by mutableStateOf(false); private set
    var tracks by mutableStateOf(Tracks.EMPTY); private set
    var params by mutableStateOf(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT); private set
    /** Text of the subtitle cues currently on screen (empty = none). */
    var cues by mutableStateOf(""); private set

    // One factory for everything: UA + custom headers apply to manifests, segments, keys and DRM license requests.
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(stream.ua)
        .setDefaultRequestProperties(stream.headers)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(10_000)
        .setAllowCrossProtocolRedirects(true) // IPTV links commonly bounce http -> https

    private val drm: DrmSessionManagerProvider = stream.drm?.keys?.let { json ->
        val manager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(true)
            .build(LocalMediaDrmCallback(json))
        DrmSessionManagerProvider { manager }
    } ?: DefaultDrmSessionManagerProvider().also { it.setDrmHttpDataSourceFactory(http) }

    private val uri = stream.uri

    /** Declared type from the address, or sniffed from the text of an extension-less / query-style link (Xtream etc.). */
    private val kind = Util.inferContentType(uri).let { k ->
        if (k != C.CONTENT_TYPE_OTHER) k else uri.toString().lowercase().let { u ->
            when {
                "m3u8" in u -> C.CONTENT_TYPE_HLS
                ".mpd" in u -> C.CONTENT_TYPE_DASH
                ".ism" in u -> C.CONTENT_TYPE_SS
                else -> k
            }
        }
    }
    /** Unknown links that fail to parse are retried as these formats, one by one. */
    private val sniff = ArrayDeque(listOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_SS))

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

    /** HLS / DASH / SmoothStreaming / RTSP / progressive (mp4, mkv, ts, webm, mp3, aac, ogg, flac ...) all go through one factory. */
    private fun mediaSource(type: Int): MediaSource {
        val item = MediaItem.Builder().setUri(uri)
            .setMimeType(when (type) {
                C.CONTENT_TYPE_HLS -> MimeTypes.APPLICATION_M3U8
                C.CONTENT_TYPE_DASH -> MimeTypes.APPLICATION_MPD
                C.CONTENT_TYPE_SS -> MimeTypes.APPLICATION_SS
                else -> null
            })
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
            .setSubtitleConfigurations(stream.subs.map {
                val path = it.path.orEmpty().lowercase()
                MediaItem.SubtitleConfiguration.Builder(it).setMimeType(when {
                    path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                    path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                    path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
                    else -> MimeTypes.APPLICATION_SUBRIP
                }).build()
            })
            .apply {
                // Widevine / PlayReady / ClearKey-by-URL. (ClearKey with kid:key is answered locally by [drm].)
                stream.drm?.takeIf { it.keys == null }?.let { d ->
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(d.uuid).setLicenseUri(d.url)
                            .setForceDefaultLicenseUri(d.url != null).setMultiSession(true).build(),
                    )
                }
            }.build()
        // Only progressive VODs use the disk cache: manifests and live segments must never be served stale.
        val data: DataSource.Factory = if (type == C.CONTENT_TYPE_OTHER && cache != null) {
            CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else http
        return DefaultMediaSourceFactory(data).setDrmSessionManagerProvider(drm).createMediaSource(item)
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

    // ---- tracks: quality / audio / subtitles ----
    private fun select(type: Int, t: T? = null, off: Boolean = false) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type).setTrackTypeDisabled(type, off)
            .apply { if (t != null) addOverride(TrackSelectionOverride(t.g.mediaTrackGroup, t.i)) }
            .build()
    }

    private fun Format.title(n: Int): String {
        val lang = language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
        return label ?: lang ?: "${n + 1}"
    }

    /** Entries for [C.TRACK_TYPE_VIDEO] (auto + heights), [C.TRACK_TYPE_AUDIO] or [C.TRACK_TYPE_TEXT] (off + tracks); empty = nothing to choose. */
    fun options(type: Int): List<TrackOpt> {
        val all = tracks.groups.filter { it.type == type }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { T(g, it) } }
        return when (type) {
            C.TRACK_TYPE_VIDEO -> {
                val manual = params.overrides.keys.any { it.type == type }
                fun name(t: T) = if (t.f.height > 0) "${t.f.height}p" else "${t.f.bitrate / 1000} kbps"
                val best = all.sortedWith(compareByDescending<T> { it.f.height }.thenByDescending { it.f.bitrate }).distinctBy { name(it) }
                if (best.size < 2) emptyList()
                else listOf(TrackOpt(null, !manual) { select(type) }) +
                    best.map { t -> TrackOpt(name(t), manual && all.any { it.sel && name(it) == name(t) }) { select(type, t) } }
            }
            C.TRACK_TYPE_AUDIO -> if (all.size < 2) emptyList()
                else all.mapIndexed { n, t -> TrackOpt(t.f.title(n), t.sel) { select(type, t) } }
            else -> if (all.isEmpty()) emptyList()
                else listOf(TrackOpt(null, all.none { it.sel }) { select(type, off = true) }) +
                    all.mapIndexed { n, t -> TrackOpt(t.f.title(n), t.sel) { select(type, t) } }
        }
    }

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
    override fun onTracksChanged(tracks: Tracks) { this.tracks = tracks }
    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) { params = parameters }
    override fun onCues(cueGroup: CueGroup) { cues = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n") }

    override fun onPlayerError(error: PlaybackException) {
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
            // A link with no recognisable extension that is really a playlist / manifest: retry as HLS, DASH, then Smooth.
            kind == C.CONTENT_TYPE_OTHER && error.errorCode in Sniffable && sniff.isNotEmpty() -> {
                player.setMediaSource(mediaSource(sniff.removeFirst()))
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
