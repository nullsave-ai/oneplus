package com.oneplus.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.oneplus.app.R
import com.oneplus.app.player.MediaCache
import com.oneplus.app.player.PlaySource
import com.oneplus.app.player.Playback
import com.oneplus.app.player.toUriOrNull
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Transient on-screen feedback for gestures. */
private sealed interface Hud {
    data class Level(val brightness: Boolean, val fraction: Float) : Hud
    data class Seek(val targetMs: Long, val deltaMs: Long) : Hud
    data class Skip(val forward: Boolean, val seconds: Int) : Hud
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen player. Video is a plain SurfaceView (cheapest path, no media3-ui); every control is drawn here.
 *
 * Gestures: tap = show/hide controls · double-tap left/right third = -10s/+10s (repeat to accumulate), middle = play/pause ·
 * drag horizontally = scrub (VOD) · drag vertically on the left half = brightness, right half = volume.
 * Controls are floating dark glass and hide themselves 3.5s after the last interaction while playing.
 */
@Composable
fun PlayerScreen(source: PlaySource, onClose: () -> Unit) {
    val c = LocalColors.current
    val view = LocalView.current
    val ctx = LocalContext.current
    val app = remember(ctx) { ctx.applicationContext }
    val activity = remember(view) { view.context.findActivity() }
    val window = activity?.window
    val audio = remember(app) { app.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Immersive landscape + screen on while the player is open; everything is restored on exit.
    DisposableEffect(activity) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        view.keepScreenOn = true
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = oldOrientation
            window?.let { w -> val lp = w.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = lp }
        }
    }

    // The player is created off the first frame (cache init does disk IO) and released the moment the screen leaves composition.
    val uri = remember(source.url) { source.toUriOrNull() }
    var playback by remember { mutableStateOf<Playback?>(null) }
    LaunchedEffect(source) {
        if (uri != null) {
            val cache = if (source.live) null else withContext(Dispatchers.IO) { MediaCache.get(app) }
            playback = Playback(app, source, uri, cache)
        }
    }
    DisposableEffect(playback) { val p = playback; onDispose { p?.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback?.pause() }
    BackHandler { onClose() }

    val pb = playback
    val failed = uri == null || pb?.failed == true
    var show by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) } // bumped on every interaction to restart the auto-hide timer
    var scrub by remember { mutableStateOf<Float?>(null) }
    var fit by rememberSaveable { mutableStateOf(true) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var lastHud by remember { mutableStateOf<Hud?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var buf by remember { mutableLongStateOf(0L) }

    // Position is only polled while the controls are visible.
    LaunchedEffect(pb, show) {
        if (pb == null) return@LaunchedEffect
        while (show) { pos = pb.position; buf = pb.bufferedPosition; delay(400) }
    }
    LaunchedEffect(show, tick, pb?.wantsPlay, scrub != null, failed) {
        if (show && pb?.wantsPlay == true && scrub == null && !failed) { delay(3500); show = false }
    }
    LaunchedEffect(pb?.ended) { if (pb?.ended == true) show = true }
    LaunchedEffect(hud) { if (hud != null) { lastHud = hud; delay(900); hud = null } }

    fun readBrightness(): Float {
        val b = window?.attributes?.screenBrightness ?: -1f
        if (b >= 0f) return b
        return runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }
    fun setBrightness(f: Float) {
        window?.let { w -> val lp = w.attributes; lp.screenBrightness = f.coerceIn(0.02f, 1f); w.attributes = lp }
    }
    fun readVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat() else 0f
    }
    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt().coerceIn(0, max), 0) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 1) video
        if (pb != null) key(pb) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), Alignment.Center) {
                val vs = pb.videoSize
                val ratio = if (vs.width > 0 && vs.height > 0) vs.width * vs.pixelWidthHeightRatio / vs.height else 16f / 9f
                val containerWider = maxWidth / maxHeight > ratio
                val w: Dp
                val h: Dp
                if (fit == containerWider) { h = maxHeight; w = maxHeight * ratio } else { w = maxWidth; h = maxWidth / ratio }
                AndroidView(
                    factory = { SurfaceView(it).also { sv -> pb.player.setVideoSurfaceView(sv) } },
                    modifier = Modifier.requiredSize(w, h),
                )
            }
        }

        // 2) shutter: hides the black/first-frame jump and fades the picture in
        val shutter by animateFloatAsState(if (pb?.firstFrame == true || failed) 0f else 1f, tween(280), label = "shutter")
        if (shutter > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutter }.background(Color.Black))

        // 3) gestures
        Box(
            Modifier.fillMaxSize()
                .pointerInput(playback) {
                    detectTapGestures(
                        onTap = { show = !show; tick++ },
                        onDoubleTap = { o ->
                            val p = playback ?: return@detectTapGestures
                            val side = when { o.x < size.width / 3f -> -1; o.x > size.width * 2f / 3f -> 1; else -> 0 }
                            if (side == 0) { p.toggle(); tick++ }
                            else if (p.seekable) {
                                p.seekBy(side * 10_000L)
                                val h = hud
                                val acc = if (h is Hud.Skip && h.forward == (side > 0)) h.seconds + 10 else 10
                                hud = Hud.Skip(side > 0, acc)
                            }
                        },
                    )
                }
                .pointerInput(playback) {
                    var axis = 0 // 0 undecided · 1 horizontal seek · 2 vertical level · 3 ignored
                    var accX = 0f
                    var startX = 0f
                    var startPos = 0L
                    var target = 0L
                    var level = 0f
                    var isBrightness = false
                    detectDragGestures(
                        onDragStart = { o -> axis = 0; accX = 0f; startX = o.x },
                        onDragEnd = { if (axis == 1) playback?.seekTo(target); axis = 0 },
                        onDragCancel = { axis = 0 },
                    ) { change, drag ->
                        val p = playback ?: return@detectDragGestures
                        change.consume()
                        if (axis == 0) {
                            axis = if (abs(drag.x) > abs(drag.y)) { if (p.seekable) 1 else 3 } else 2
                            if (axis == 1) { startPos = p.position; target = startPos }
                            if (axis == 2) { isBrightness = startX < size.width / 2f; level = if (isBrightness) readBrightness() else readVolume() }
                        }
                        when (axis) {
                            1 -> {
                                accX += drag.x
                                target = (startPos + (accX / size.width * 120_000f).toLong()).coerceIn(0L, p.durationMs)
                                hud = Hud.Seek(target, target - startPos)
                            }
                            2 -> {
                                level = (level - drag.y / size.height * 1.3f).coerceIn(0f, 1f)
                                if (isBrightness) setBrightness(level) else setVolume(level)
                                hud = Hud.Level(isBrightness, level)
                            }
                        }
                    }
                }
        )

        // 4) gesture feedback
        val hudAlign = when (val h = lastHud) {
            is Hud.Level -> Alignment.TopCenter
            is Hud.Skip -> if (h.forward) Alignment.CenterEnd else Alignment.CenterStart
            else -> Alignment.Center
        }
        AnimatedVisibility(
            hud != null,
            Modifier.align(hudAlign).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 56.dp, vertical = 24.dp),
            enter = fadeIn(tween(100)), exit = fadeOut(tween(260)),
        ) {
            val h = lastHud
            if (h != null) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { HudView(h, c.accent) }
        }

        // 5) loading / error
        val controlsA by animateFloatAsState(if (show) 1f else 0f, tween(220), label = "controls")
        if ((pb == null || pb.buffering || !pb.firstFrame) && !failed && controlsA < 0.5f) {
            Spinner(c.accent, Modifier.align(Alignment.Center).size(44.dp))
        }
        if (failed) Column(Modifier.align(Alignment.Center), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
            OneText(stringResource(if (uri == null) R.string.player_bad_link else R.string.player_error), OneType.Section, Color.White)
            if (uri != null) OneButton(stringResource(R.string.player_retry), OneIcon.Forward, { playback?.retry() }, Modifier.width(220.dp))
        }

        // 6) controls
        if (controlsA > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = controlsA }) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            )
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp)) {
                // top: back · title (+ live) · aspect
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).press { onClose() }.vGlass(22.dp), Alignment.Center) { OneIconView(OneIcon.Back) { Color.White } }
                    Row(
                        Modifier.weight(1f).height(44.dp).vGlass(22.dp).padding(horizontal = 16.dp),
                        Arrangement.spacedBy(10.dp), Alignment.CenterVertically,
                    ) {
                        OneText(source.title, OneType.Section, Color.White, Modifier.weight(1f), 1)
                        if (pb?.live ?: source.live) LiveChip(c.accent)
                    }
                    Box(Modifier.size(44.dp).press { fit = !fit; tick++ }.vGlass(22.dp), Alignment.Center) {
                        OneIconView(if (fit) OneIcon.Fit else OneIcon.Fill) { Color.White }
                    }
                }

                if (pb != null && !failed) {
                    // center transport (physical order: back · play · forward, independent of RTL)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.align(Alignment.Center), Arrangement.spacedBy(28.dp), Alignment.CenterVertically) {
                            if (pb.seekable) SkipButton(false) { pb.seekBy(-10_000); tick++ }
                            Box(Modifier.size(72.dp).press { pb.toggle(); tick++ }.clip(CircleShape).background(c.accent), Alignment.Center) {
                                if (pb.buffering && pb.wantsPlay) Spinner(c.onAccent, Modifier.size(30.dp))
                                else OneIconView(
                                    if (pb.ended) OneIcon.Replay else if (pb.wantsPlay) OneIcon.Pause else OneIcon.Play,
                                    Modifier.size(34.dp),
                                ) { c.onAccent }
                            }
                            if (pb.seekable) SkipButton(true) { pb.seekBy(10_000); tick++ }
                        }
                    }

                    // bottom: seek island (VOD only; a live stream has nothing to scrub)
                    if (pb.seekable) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val dur = pb.durationMs
                        val shown = scrub?.let { (it * dur).toLong() } ?: pos
                        Row(
                            Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().vGlass(24.dp)
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
                        ) {
                            OneText(fmt(shown), OneType.Caption, Color.White)
                            SeekBar(
                                fraction = scrub ?: if (dur > 0) pos.toFloat() / dur else 0f,
                                buffered = if (dur > 0) buf.toFloat() / dur else 0f,
                                active = scrub != null, accent = c.accent,
                                onScrub = { scrub = it; tick++ },
                                onCommit = { f -> pb.seekTo((f * dur).toLong()); pos = (f * dur).toLong(); scrub = null; tick++ },
                                modifier = Modifier.weight(1f),
                            )
                            OneText(fmt(dur), OneType.Caption, Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ---- pieces ----------------------------------------------------------------------------------------------------

/** Dark floating glass: reads on any video regardless of the app's day/night theme. */
@Composable
private fun Modifier.vGlass(radius: Dp): Modifier {
    val fx = LocalGlassEffects.current
    val shape = RoundedCornerShape(radius)
    return clip(shape).background(Color.Black.copy(alpha = if (fx) 0.42f else 0.72f)).border(0.5.dp, Color.White.copy(alpha = 0.16f), shape)
}

@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).press(onClick).vGlass(28.dp), Alignment.Center) {
        OneIconView(if (forward) OneIcon.Forward else OneIcon.Replay, Modifier.size(32.dp)) { Color.White }
        OneText("10", OneType.Caption, Color.White)
    }
}

@Composable
private fun LiveChip(accent: Color) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(accent, alpha = a) }
        OneText(stringResource(R.string.player_live), OneType.Caption, Color.White)
    }
}

@Composable
private fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(modifier) {
        val w = 3.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.16f), size.minDimension / 2f - w / 2f, style = Stroke(w))
        drawArc(color, a, 90f, false, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Thin track that thickens while dragging: buffered (dim) + played (accent) + thumb. */
@Composable
private fun SeekBar(
    fraction: Float, buffered: Float, active: Boolean, accent: Color,
    onScrub: (Float) -> Unit, onCommit: (Float) -> Unit, modifier: Modifier = Modifier,
) {
    val grow by animateFloatAsState(if (active) 1f else 0f, spring(0.7f, 600f), label = "grow")
    val scrubCb by rememberUpdatedState(onScrub)
    val commitCb by rememberUpdatedState(onCommit)
    var last by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier.height(32.dp)
            .pointerInput(Unit) { detectTapGestures { o -> commitCb((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> last = (o.x / size.width).coerceIn(0f, 1f); scrubCb(last) },
                    onDragEnd = { commitCb(last) },
                    onDragCancel = { commitCb(last) },
                ) { ch, _ ->
                    ch.consume()
                    last = (ch.position.x / size.width).coerceIn(0f, 1f)
                    scrubCb(last)
                }
            }
    ) {
        val th = (4f + 3f * grow).dp.toPx()
        val cy = size.height / 2f
        val r = CornerRadius(th / 2f)
        val top = Offset(0f, cy - th / 2f)
        val px = size.width * fraction.coerceIn(0f, 1f)
        drawRoundRect(Color.White.copy(alpha = 0.22f), top, Size(size.width, th), r)
        drawRoundRect(Color.White.copy(alpha = 0.30f), top, Size(size.width * buffered.coerceIn(0f, 1f), th), r)
        drawRoundRect(accent, top, Size(px, th), r)
        drawCircle(Color.White, (5f + 3f * grow).dp.toPx(), Offset(px, cy))
    }
}

@Composable
private fun HudView(h: Hud, accent: Color) {
    when (h) {
        is Hud.Level -> Row(
            Modifier.vGlass(20.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
        ) {
            OneIconView(if (h.brightness) OneIcon.Sun else if (h.fraction <= 0.01f) OneIcon.Mute else OneIcon.Volume) { Color.White }
            Canvas(Modifier.size(120.dp, 4.dp)) {
                drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = CornerRadius(size.height / 2f))
                drawRoundRect(accent, size = Size(size.width * h.fraction, size.height), cornerRadius = CornerRadius(size.height / 2f))
            }
            OneText("${(h.fraction * 100).roundToInt()}", OneType.Caption, Color.White)
        }
        is Hud.Seek -> Column(
            Modifier.vGlass(24.dp).padding(horizontal = 28.dp, vertical = 12.dp),
            Arrangement.spacedBy(2.dp), Alignment.CenterHorizontally,
        ) {
            OneText(fmt(h.targetMs), OneType.Title, Color.White)
            OneText((if (h.deltaMs >= 0) "+" else "-") + fmt(abs(h.deltaMs)), OneType.Caption, accent)
        }
        is Hud.Skip -> Column(
            Modifier.vGlass(28.dp).padding(horizontal = 20.dp, vertical = 14.dp),
            Arrangement.spacedBy(4.dp), Alignment.CenterHorizontally,
        ) {
            OneIconView(if (h.forward) OneIcon.Forward else OneIcon.Replay, Modifier.size(32.dp)) { Color.White }
            OneText((if (h.forward) "+" else "-") + h.seconds + " " + stringResource(R.string.unit_sec), OneType.Caption, Color.White)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun two(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
}
