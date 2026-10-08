package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PageStyle = 0
private const val PageMode = 1
private const val PageColor = 2
private const val PageTune = 3
private const val PageDisplay = 4
private const val PageMotion = 5

@StringRes
internal fun settingsTitle(page: Int): Int = when (page) {
    PageStyle -> R.string.style_mode
    PageMode -> R.string.theme_mode
    PageColor -> R.string.color_row
    PageTune -> R.string.glass_title
    PageMotion -> R.string.settings_transition
    else -> R.string.display_mode
}

@Composable
fun PhoneSettings(
    theme: ThemeController, scroll: ScrollState, subScroll: ScrollState,
    page: Int, onPage: (Int) -> Unit, wide: Boolean, saved: List<Movie>, onMovie: (Int) -> Unit,
    onTelegram: () -> Unit, onClearHistory: () -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LaunchedEffect(page) { if (page >= 0) subScroll.scrollTo(0) }
    val tv = LocalTvMode.current
    val first = remember { page }
    val moved = remember { booleanArrayOf(false) }
    if (page != first) moved[0] = true
    AnimatedContent(
        page, Modifier.fillMaxSize(),
        transitionSpec = {
            val dir = if (targetState > initialState) -1 else 1
            pageTransition(theme.prefs.transition, dir) {
                (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 3 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -dir * it / 3 } + fadeOut(tween(160)))
            }
        },
        label = "settings",
    ) { p ->
        val into = remember { FocusRequester() }
        if (tv && moved[0]) LaunchedEffect(Unit) { delay(450); runCatching { into.requestFocus() } }
        Box(Modifier.fillMaxSize().then(pageBackdrop(theme.prefs.transition)), Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().focusRequester(into).focusGroup().verticalScroll(if (p < 0) scroll else subScroll)
                    .padding(top = toolbarInset(), bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (p < 0) SettingsList(theme, wide, saved, onMovie, onTelegram, onClearHistory, onPage)
                else Column(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(14.dp)) {
                    when (p) {
                        PageStyle -> StylePage(theme)
                        PageMode -> ModePage(theme)
                        PageColor -> Panel { ColorStudio(theme, LocalDarkTheme.current) }
                        PageTune -> TunePage(theme)
                        PageMotion -> MotionPage(theme)
                        else -> DisplayPage(theme)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsList(
    theme: ThemeController, wide: Boolean, saved: List<Movie>, onMovie: (Int) -> Unit,
    onTelegram: () -> Unit, onClearHistory: () -> Unit, onPage: (Int) -> Unit,
) {
    val c = LocalColors.current
    val p = theme.prefs
    if (saved.isNotEmpty()) SavedShelf(saved, wide, onMovie)
    Column(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(22.dp)) {
        Row(
            Modifier.fillMaxWidth().press(onTelegram).glass(2, 22.dp).padding(16.dp),
            Arrangement.spacedBy(14.dp), Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(c.accentSoft, CircleShape), Alignment.Center) { OneIconView(OneIcon.Send) { c.accent } }
            Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                OneText(stringResource(R.string.settings_tg), OneType.Section, c.text, maxLines = 1)
                OneText(stringResource(R.string.settings_tg_sub), OneType.Caption, c.dim, maxLines = 1)
            }
            OneIconView(OneIcon.Next) { c.dim }
        }
        Group(R.string.color_title) {
            NavRow(OneIcon.Fill, Violet, R.string.style_mode, stringResource(p.look.label)) { onPage(PageStyle) }
            NavRow(OneIcon.Sun, Indigo, R.string.theme_mode, stringResource(p.mode.label)) { onPage(PageMode) }
            NavRow(OneIcon.Star, Pink, R.string.color_row, stringResource(p.accent.label), dot = c.accent) { onPage(PageColor) }
            NavRow(OneIcon.Settings, Teal, R.string.glass_title, if (p.glass) "${(p.glassDensity * 100f).roundToInt()}%" else stringResource(R.string.value_off)) { onPage(PageTune) }
            NavRow(OneIcon.Forward, Orange, R.string.settings_transition, stringResource(p.transition.label), last = true) { onPage(PageMotion) }
        }
        Group(R.string.settings_screen) {
            NavRow(OneIcon.Channels, Green, R.string.display_mode, stringResource(p.display.label)) { onPage(PageDisplay) }
            SwitchRow(OneIcon.Expand, Gray, R.string.settings_hide_status, p.hideStatusBar) {
                theme.update { copy(hideStatusBar = it) }; theme.save()
            }
            SwitchRow(OneIcon.Shrink, Gray, R.string.settings_hide_nav, p.hideNavBar, last = true) {
                theme.update { copy(hideNavBar = it) }; theme.save()
            }
        }
        Group(R.string.settings_general) {
            Row(
                Modifier.fillMaxWidth().press(onClearHistory).padding(horizontal = 16.dp, vertical = 12.dp),
                Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
            ) {
                Badge(OneIcon.Replay, c.error)
                OneText(stringResource(R.string.settings_clear), OneType.Body, c.error, Modifier.weight(1f), 1)
            }
            Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.SpaceBetween) {
                OneText(stringResource(R.string.settings_version), OneType.Body, c.text)
                OneText("1.0", OneType.Body, c.dim)
            }
        }
    }
}

private val Violet = Color(0xFF7C5CFA)
private val Indigo = Color(0xFF5E6AD2)
private val Pink = Color(0xFFE5547A)
private val Teal = Color(0xFF2FA4B8)
private val Green = Color(0xFF3DB26B)
private val Gray = Color(0xFF8A8D96)
private val Orange = Color(0xFFE8833A)

@Composable
private fun Group(@StringRes title: Int, rows: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OneText(stringResource(title), OneType.Caption, c.dim, Modifier.padding(horizontal = 14.dp))
        Column(Modifier.fillMaxWidth().glass(2, 22.dp), content = rows)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass(2, 22.dp).padding(vertical = 4.dp), content = content)
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(0.5.dp).background(LocalColors.current.border))
}

@Composable
private fun Badge(icon: OneIcon, tone: Color) {
    Box(Modifier.size(30.dp).background(tone, RoundedCornerShape(9.dp)), Alignment.Center) { OneIconView(icon, Modifier.size(18.dp)) { Color.White } }
}

@Composable
private fun NavRow(icon: OneIcon, tone: Color, @StringRes title: Int, value: String, dot: Color? = null, last: Boolean = false, onClick: () -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier.fillMaxWidth().press(onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
    ) {
        Badge(icon, tone)
        OneText(stringResource(title), OneType.Body, c.text, Modifier.weight(1f), 1)
        if (dot != null) Box(Modifier.size(14.dp).background(dot, CircleShape))
        OneText(value, OneType.Body, c.dim, maxLines = 1)
        OneIconView(OneIcon.Next, Modifier.size(18.dp)) { c.dim }
    }
    if (!last) Divider()
}

@Composable
private fun SwitchRow(icon: OneIcon, tone: Color, @StringRes title: Int, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
    ) {
        Badge(icon, tone)
        OneText(stringResource(title), OneType.Body, LocalColors.current.text, Modifier.weight(1f), 1)
        OneSwitch(checked, onChange)
    }
    if (!last) Divider()
}

@Composable
private fun StylePage(theme: ThemeController) {
    val p = theme.prefs
    val dark = LocalDarkTheme.current
    val accent = LocalColors.current.accent
    Looks.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(14.dp)) {
            pair.forEach { l ->
                Tile(l == p.look, stringResource(l.label), stringResource(l.blurb), { theme.update { copy(look = l) }; theme.save() }, Modifier.weight(1f)) {
                    val pal = l.palette(dark, false)
                    Canvas(Modifier.fillMaxSize()) { mini(pal, dark, accent, l.cosmic, l.pitch, l.anime) }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModePage(theme: ThemeController) {
    val p = theme.prefs
    val look = p.look
    val accent = LocalColors.current.accent
    ThemeMode.entries.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(14.dp)) {
            pair.forEach { m ->
                Tile(m == p.mode, stringResource(m.label), null, { theme.update { copy(mode = m) }; theme.save() }, Modifier.weight(1f)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val day = look.palette(false, false)
                        val night = look.palette(true, false)
                        when (m) {
                            ThemeMode.Light -> mini(day, false, accent, look.cosmic, look.pitch, look.anime)
                            ThemeMode.Dark -> mini(night, true, accent, look.cosmic, look.pitch, look.anime)
                            ThemeMode.Amoled -> mini(look.palette(true, true).let { Palette(Color.Black, it.surface, it.border, it.text, it.dim) }, true, accent, look.cosmic, look.pitch, look.anime)
                            ThemeMode.Graphite -> mini(GraphitePalette, true, accent, look.cosmic, look.pitch, look.anime)
                            ThemeMode.System -> { mini(day, false, accent, look.cosmic, look.pitch, look.anime); clipRect(left = size.width / 2f) { mini(night, true, accent, look.cosmic, look.pitch, look.anime) } }
                        }
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TunePage(theme: ThemeController) {
    val p = theme.prefs
    Panel {
        SettingRow(stringResource(R.string.settings_effects)) { OneSwitch(p.glass) { theme.update { copy(glass = it) }; theme.save() } }
        if (p.glass) { if (LocalTvMode.current) Column(Modifier.padding(vertical = 8.dp)) { GlassSliders(theme) } else LookPad(theme) }
    }
}

@Composable
private fun MotionPage(theme: ThemeController) {
    val c = LocalColors.current
    val p = theme.prefs
    Panel {
        PageTransition.entries.forEachIndexed { i, t ->
            Row(
                Modifier.fillMaxWidth().press { theme.update { copy(transition = t) }; theme.save() }.padding(horizontal = 16.dp, vertical = 14.dp),
                Arrangement.spacedBy(12.dp), Alignment.CenterVertically,
            ) {
                OneText(stringResource(t.label), OneType.Body, if (t == p.transition) c.accent else c.text, Modifier.weight(1f), 1)
                if (t == p.transition) OneIconView(OneIcon.Check, Modifier.size(18.dp)) { c.accent }
            }
            if (i < PageTransition.entries.lastIndex) Divider()
        }
    }
}

@Composable
private fun DisplayPage(theme: ThemeController) {
    val p = theme.prefs
    val ink = LocalColors.current.text
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        DisplayMode.entries.forEach { m ->
            val ask = m == DisplayMode.Tv && p.display != m
            Tile(
                m == p.display, stringResource(m.label), if (ask && armed) stringResource(R.string.display_confirm) else null,
                { if (ask && !armed) armed = true else { armed = false; theme.update { copy(display = m) }; theme.save() } },
                Modifier.weight(1f),
            ) { Canvas(Modifier.fillMaxSize()) { device(m, ink) } }
        }
    }
}

@Composable
private fun Tile(selected: Boolean, title: String, sub: String?, onClick: () -> Unit, modifier: Modifier, preview: @Composable BoxScope.() -> Unit) {
    val c = LocalColors.current
    val on by animateFloatAsState(if (selected) 1f else 0f, spring(0.7f, 500f), label = "tile")
    Column(modifier.press(onClick), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.87f).glass(2, 22.dp)
                .border((2f * on).dp, c.accent.copy(alpha = on), RoundedCornerShape(22.dp)),
        ) {
            preview()
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).graphicsLayer { scaleX = on; scaleY = on }.background(c.accent, CircleShape),
                Alignment.Center,
            ) { OneIconView(OneIcon.Check, Modifier.size(14.dp)) { c.onAccent } }
        }
        OneText(title, OneType.Body, lerp(c.dim, c.text, on), maxLines = 1)
        if (sub != null) OneText(sub, OneType.Caption, c.dim, maxLines = 2)
    }
}

private fun DrawScope.mini(pal: Palette, dark: Boolean, accent: Color, cosmic: Boolean = false, pitch: Boolean = false, anime: Boolean = false) {
    val u = size.width / 100f
    val text = pal.text(dark); val dim = pal.dim(dark); val edge = pal.border(dark)
    val face = pal.surface.copy(alpha = 0.62f)
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) = drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
    fun edged(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(edge, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), Stroke(0.7f * u))
    drawRect(pal.bg)
    if (anime) {
        drawRect(Brush.verticalGradient(listOf(pal.bg, lerp(pal.bg, accent, 0.18f))))
        for (gx in 0..7) for (gy in 0..4) { val r = (1.6f - (gy + (7 - gx)) * 0.16f).coerceAtLeast(0f); if (r > 0.2f) drawCircle(accent.copy(alpha = 0.4f), r * u, Offset((50f + gx * 7f) * u, (4f + gy * 7f) * u)) }
        fun plate(x: Float, y: Float, w: Float, h: Float, fill: Color, shadow: Color) {
            val k = h * 0.28f
            fun path(dx: Float) = Path().apply { moveTo((x + k + dx) * u, (y + dx) * u); lineTo((x + w + dx) * u, (y + dx) * u); lineTo((x + w - k + dx) * u, (y + h + dx) * u); lineTo((x + dx) * u, (y + h + dx) * u); close() }
            drawPath(path(1.4f), shadow); drawPath(path(0f), fill); drawPath(path(0f), edge, style = Stroke(1f * u))
        }
        plate(8f, 6f, 62f, 11f, face.copy(alpha = 1f), accent.copy(alpha = 0.75f)); box(16f, 9.6f, 28f, 3.6f, 1.8f, text); drawCircle(accent, 1.6f * u, Offset(62f * u, 11.5f * u))
        drawCircle(Color(0xFFFFD84D), 3.6f * u, Offset(88f * u, 11.5f * u))
        drawRoundRect(accent.copy(alpha = 0.7f), Offset(11f * u, 27f * u), Size(84f * u, 36f * u), CornerRadius(7f * u))
        drawRoundRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), face), Offset(8f * u, 24f * u), Offset(92f * u, 60f * u)), Offset(8f * u, 24f * u), Size(84f * u, 36f * u), CornerRadius(7f * u))
        drawRoundRect(edge, Offset(8f * u, 24f * u), Size(84f * u, 36f * u), CornerRadius(7f * u), Stroke(1.1f * u))
        drawCircle(Color(0xFFFFD84D), 6f * u, Offset(82f * u, 34f * u)); box(14f, 50f, 30f, 4f, 2f, Color.White.copy(alpha = 0.9f))
        for (i in 0..2) { val x = 8f + i * 31f; drawRoundRect(accent.copy(alpha = 0.7f), Offset((x + 1.2f) * u, 69.2f * u), Size(26f * u, 24f * u), CornerRadius(3f * u)); drawRoundRect(face.copy(alpha = 1f), Offset(x * u, 68f * u), Size(26f * u, 24f * u), CornerRadius(3f * u)); drawRoundRect(edge, Offset(x * u, 68f * u), Size(26f * u, 24f * u), CornerRadius(3f * u), Stroke(1f * u)) }
        plate(8f, 98f, 26f, 13f, face.copy(alpha = 1f), accent.copy(alpha = 0.75f)); plate(37f, 94f, 26f, 13f, accent, Color(0xFFFFD84D)); plate(66f, 98f, 26f, 13f, face.copy(alpha = 1f), accent.copy(alpha = 0.75f))
        for ((px, py) in listOf(22f to 70f, 70f to 66f, 92f to 80f)) drawOval(Color(0xFFFFB7D5), Offset(px * u, py * u), Size(4.5f * u, 2.6f * u))
        return
    }
    if (pitch) {
        for (i in 0 until 8 step 2) drawRect(text.copy(alpha = 0.05f), Offset(0f, i * 14f * u), Size(100f * u, 14f * u))
        drawRect(face, Offset(0f, 0f), Size(100f * u, 17f * u)); drawRect(PitchGold, Offset(38f * u, 16f * u), Size(24f * u, 1f * u))
        box(8f, 6f, 30f, 5f, 1.5f, text); box(72f, 5.5f, 20f, 6f, 1.5f, Color.Black.copy(alpha = 0.45f)); box(75f, 7.4f, 14f, 2.4f, 1f, PitchGold)
        val big = Path().apply { moveTo(16f * u, 22f * u); lineTo(92f * u, 22f * u); lineTo(92f * u, 52f * u); lineTo(84f * u, 60f * u); lineTo(8f * u, 60f * u); lineTo(8f * u, 30f * u); close() }
        drawPath(big, Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), face), Offset(8f * u, 22f * u), Offset(92f * u, 60f * u))); drawPath(big, edge, style = Stroke(0.7f * u))
        box(14f, 50f, 26f, 4f, 2f, Color.White.copy(alpha = 0.9f)); box(70f, 25f, 16f, 6f, 1.5f, PitchGold)
        val tiers = listOf(Color(0xFFF7E08A) to Color(0xFF9A6C1B), Color(0xFFEEF3F6) to Color(0xFF59656E), Color(0xFFE9B98A) to Color(0xFF55331A))
        tiers.forEachIndexed { i, (a, b) -> val x = 8f + i * 31f; drawRoundRect(Brush.verticalGradient(listOf(a, b), 66f * u, 92f * u), Offset(x * u, 66f * u), Size(27f * u, 26f * u), CornerRadius(4f * u)) }
        drawRoundRect(Color(0xFF0F4A30), Offset(8f * u, 97f * u), Size(84f * u, 14f * u), CornerRadius(5f * u)); drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(10f * u, 99f * u), Size(80f * u, 10f * u), CornerRadius(3f * u), Stroke(0.6f * u))
        drawCircle(Color.White, 3.6f * u, Offset(50f * u, 95f * u)); drawCircle(Color(0xFF14181A), 1.3f * u, Offset(50f * u, 95f * u))
        return
    }
    if (cosmic) {
        drawCircle(accent.copy(alpha = 0.35f), 30f * u, Offset(86f * u, 6f * u))
        drawCircle(Color(0xFFB36BFF).copy(alpha = 0.22f), 26f * u, Offset(8f * u, 100f * u))
        for ((x, y) in listOf(14f to 24f, 30f to 12f, 72f to 20f, 90f to 46f, 8f to 56f, 60f to 8f, 84f to 70f, 20f to 80f)) drawCircle(text.copy(alpha = 0.55f), 0.6f * u, Offset(x * u, y * u))
        drawCircle(accent, 1.8f * u, Offset(10f * u, 11f * u)); box(16f, 9f, 26f, 4.5f, 2.2f, text)
        fun planet(cx: Float, cy: Float, r: Float, a: Float) {
            drawCircle(accent.copy(alpha = 0.25f * a), r * 1.5f * u, Offset(cx * u, cy * u))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * a), accent.copy(alpha = a), lerp(accent, Color.Black, 0.6f).copy(alpha = a)),
                Offset((cx - r * 0.3f) * u, (cy - r * 0.35f) * u), r * 1.5f * u), r * u, Offset(cx * u, cy * u))
        }
        planet(22f, 50f, 9f, 0.5f); planet(78f, 50f, 9f, 0.5f)
        planet(50f, 46f, 17f, 1f)
        drawOval(accent.copy(alpha = 0.85f), Offset(27f * u, 43f * u), Size(46f * u, 8f * u), style = Stroke(0.8f * u))
        box(30f, 70f, 40f, 4f, 2f, text); box(38f, 77f, 24f, 2.6f, 1.3f, dim)
        box(14f, 99f, 72f, 12f, 6f, pal.surface.copy(alpha = 0.7f)); edged(14f, 99f, 72f, 12f, 6f)
        planet(50f, 98f, 4.5f, 1f)
        return
    }
    drawCircle(accent.copy(alpha = 0.55f), 26f * u, Offset(78f * u, 40f * u)); drawCircle(accent.copy(alpha = 0.30f), 18f * u, Offset(22f * u, 78f * u))
    box(8f, 8f, 34f, 5.5f, 2.7f, text)
    drawCircle(dim, 4f * u, Offset(88f * u, 10.7f * u), style = Stroke(0.9f * u))
    drawRoundRect(
        Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), face), Offset(8f * u, 22f * u), Offset(92f * u, 66f * u)),
        Offset(8f * u, 22f * u), Size(84f * u, 44f * u), CornerRadius(7f * u),
    )
    edged(8f, 22f, 84f, 44f, 7f)
    box(13f, 54f, 30f, 4f, 2f, Color.White.copy(alpha = 0.9f))
    for (x in floatArrayOf(8f, 52f)) {
        box(x, 72f, 40f, 22f, 6f, face); edged(x, 72f, 40f, 22f, 6f)
        box(x + 5f, 78f, 18f, 3f, 1.5f, text); box(x + 5f, 84f, 12f, 2.5f, 1.2f, dim)
    }
    box(14f, 101f, 72f, 11f, 5.5f, face); edged(14f, 101f, 72f, 11f, 5.5f); box(40f, 104.5f, 20f, 4f, 2f, accent.copy(alpha = 0.6f))
}

private fun DrawScope.device(m: DisplayMode, ink: Color) {
    val u = size.width / 100f
    val st = Stroke(2.6f * u, cap = StrokeCap.Round)
    fun phone(cx: Float, w: Float, h: Float) = drawRoundRect(ink, Offset((cx - w / 2) * u, (57f - h / 2) * u), Size(w * u, h * u), CornerRadius(5f * u), st)
    fun tv(cx: Float, w: Float, h: Float) {
        drawRoundRect(ink, Offset((cx - w / 2) * u, (52f - h / 2) * u), Size(w * u, h * u), CornerRadius(4f * u), st)
        drawLine(ink, Offset((cx - 8f) * u, (52f + h / 2 + 7f) * u), Offset((cx + 8f) * u, (52f + h / 2 + 7f) * u), 2.6f * u, StrokeCap.Round)
    }
    when (m) {
        DisplayMode.Phone -> phone(50f, 34f, 62f)
        DisplayMode.Tv -> tv(50f, 74f, 44f)
        DisplayMode.Auto -> { phone(28f, 24f, 46f); tv(68f, 46f, 30f) }
    }
}
