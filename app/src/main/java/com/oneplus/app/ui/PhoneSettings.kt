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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.unit.sp
import com.oneplus.app.R
import com.oneplus.app.data.Movie
import com.oneplus.app.ui.system.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The phone's settings, in the manner of an iOS "Settings" app: a grouped list whose rows OPEN a page for their topic. Nothing
 * on the list changes a setting except the one switch, so a touch while scrolling cannot damage the look. Choices (style, day /
 * night, display) are big tiles with a miniature of the result, on a page of their own. Everything is drawn from the current
 * look's surfaces, so Glass shows frosted groups and Feed shows hairline ones. The header (owned by the look) carries the page
 * title and the back button.
 */

private const val PageStyle = 0
private const val PageMode = 1
private const val PageColor = 2
private const val PageTune = 3
private const val PageDisplay = 4

/** Title of the open sub-page, shown by the header. */
@StringRes
internal fun settingsTitle(page: Int, look: Look): Int = when (page) {
    PageStyle -> R.string.style_mode
    PageMode -> R.string.theme_mode
    PageColor -> R.string.color_row
    PageTune -> if (look.solid) R.string.look_feed else R.string.glass_title
    else -> R.string.display_mode
}

@Composable
fun PhoneSettings(
    effects: Boolean, onEffects: (Boolean) -> Unit, theme: ThemeController, scroll: ScrollState, subScroll: ScrollState,
    page: Int, onPage: (Int) -> Unit, wide: Boolean, saved: List<Movie>, onMovie: (Int) -> Unit,
    onTelegram: () -> Unit, onClearHistory: () -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomNavSpace()
    LaunchedEffect(page) { if (page >= 0) subScroll.scrollTo(0) }
    AnimatedContent(
        page, Modifier.fillMaxSize(),
        transitionSpec = {
            val dir = if (targetState > initialState) -1 else 1 // deeper = in from the start side (RTL: from the left), like a push
            (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 3 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -dir * it / 3 } + fadeOut(tween(160)))
        },
        label = "settings",
    ) { p ->
        Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(if (p < 0) scroll else subScroll)
                    .padding(top = toolbarInset(), bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (p < 0) SettingsList(theme, wide, saved, onMovie, onTelegram, onClearHistory, onPage)
                else Column(Modifier.padding(horizontal = 20.dp), Arrangement.spacedBy(14.dp)) {
                    when (p) {
                        PageStyle -> StylePage(theme)
                        PageMode -> ModePage(theme)
                        PageColor -> Panel { ColorStudio(theme, LocalDarkTheme.current, false) }
                        PageTune -> TunePage(theme, effects, onEffects)
                        else -> DisplayPage(theme)
                    }
                }
            }
        }
    }
}

// ---- The list --------------------------------------------------------------------------------------------------------------

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
            NavRow(
                OneIcon.Settings, Teal, if (p.look.solid) R.string.look_feed else R.string.glass_title,
                "${((if (p.look.solid) p.feedSize else p.glassDensity) * 100f).roundToInt()}%", last = true,
            ) { onPage(PageTune) }
        }
        Group(R.string.settings_screen) {
            NavRow(OneIcon.Channels, Green, R.string.display_mode, stringResource(p.display.label)) { onPage(PageDisplay) }
            SwitchRow(OneIcon.Expand, Gray, R.string.settings_hide_status, p.hideStatusBar, last = true) {
                theme.update { copy(hideStatusBar = it) }; theme.save()
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

/** A titled group of rows on one surface (frosted in Glass, hairline in Feed). The title is quiet in Glass and serif in Feed. */
@Composable
private fun Group(@StringRes title: Int, rows: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalLux.current) OneText(stringResource(title), OneType.Lux.copy(fontSize = 19.sp), c.text, Modifier.padding(horizontal = 6.dp))
        else OneText(stringResource(title), OneType.Caption, c.dim, Modifier.padding(horizontal = 14.dp))
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

/** Opens a page: the whole row is the target, and tapping it changes nothing by itself. */
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

/** Only the switch itself reacts; a touch on the label does nothing. */
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

// ---- The pages -------------------------------------------------------------------------------------------------------------

/** Every look, as a miniature of its own screen. Picking one repaints the whole app at once. */
@Composable
private fun StylePage(theme: ThemeController) {
    val p = theme.prefs
    val dark = LocalDarkTheme.current
    val accent = LocalColors.current.accent
    Looks.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(14.dp)) {
            pair.forEach { l ->
                Tile(l == p.look, stringResource(l.label), stringResource(l.blurb), { theme.update { copy(look = l) }; theme.save() }, Modifier.weight(1f)) {
                    val pal = l.palette(dark, false, false)
                    Canvas(Modifier.fillMaxSize()) { mini(pal, dark, l.solid, accent, l.cosmic, l.aurora) }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Auto / day / night / Amoled, each drawn in the current look's own colours. Auto is split down the middle. */
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
                        val day = look.palette(false, false, false)
                        val night = look.palette(true, false, false)
                        when (m) {
                            ThemeMode.Light -> mini(day, false, look.solid, accent, look.cosmic, look.aurora)
                            ThemeMode.Dark -> mini(night, true, look.solid, accent, look.cosmic, look.aurora)
                            ThemeMode.Amoled -> mini(look.palette(true, true, false).let { Palette(Color.Black, it.surface, it.border, it.text, it.dim) }, true, look.solid, accent, look.cosmic, look.aurora)
                            ThemeMode.System -> { mini(day, false, look.solid, accent, look.cosmic, look.aurora); clipRect(left = size.width / 2f) { mini(night, true, look.solid, accent, look.cosmic, look.aurora) } }
                        }
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Glass: density and depth on one pad. Feed: card size. (Feed's corner radius is not adjustable: the designed one stays.) */
@Composable
private fun TunePage(theme: ThemeController, effects: Boolean, onEffects: (Boolean) -> Unit) {
    val c = LocalColors.current
    val p = theme.prefs
    if (!p.look.solid) {
        Panel {
            SettingRow(stringResource(R.string.settings_effects)) { OneSwitch(effects, onEffects) }
            LookPad(theme)
        }
        return
    }
    val track = remember(c) { Brush.horizontalGradient(listOf(c.dim.copy(alpha = 0.10f), c.accent.copy(alpha = 0.60f))) }
    Panel {
        Box(Modifier.fillMaxWidth().height(150.dp), Alignment.Center) { // a live sample: it grows and shrinks under the finger
            Box(Modifier.size(84.dp * p.feedSize, 100.dp * p.feedSize).glass(2, 20.dp), Alignment.Center) { OneText("Aa", OneType.Lux, c.text) }
        }
        Column(Modifier.padding(horizontal = 8.dp)) {
            PickerRow(
                R.string.style_size, unlerp(CardRange.MIN, CardRange.MAX, p.feedSize),
                { f -> theme.update { copy(feedSize = lerpF(CardRange.MIN, CardRange.MAX, f)) } }, theme::save, track,
                "${(p.feedSize * 100f).roundToInt()}%",
            )
        }
        if (p.feedSize != 1f) SettingRow(stringResource(R.string.glass_reset), Modifier.press { theme.update { copy(feedSize = 1f) }; theme.save() }) {}
    }
}

/**
 * Auto / phone / TV. TV Mode turns the screen sideways and rescales everything, so choosing it from a phone needs a second tap
 * (the tile asks, and forgets the question after a few seconds).
 */
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

// ---- Pieces ----------------------------------------------------------------------------------------------------------------

/** A choice: a preview over a label. The chosen one gets a ring and a check that spring in. */
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

/**
 * A miniature of the Home screen painted from a look's palette: header, a hero card, two cards, the nav bar. A solid look gets
 * opaque cards with hairlines and a full-width bar; a translucent one gets frosted cards over a colour blob and a floating pill.
 */
private fun DrawScope.mini(pal: Palette, dark: Boolean, solid: Boolean, accent: Color, cosmic: Boolean = false, aurora: Boolean = false) {
    val u = size.width / 100f
    val text = pal.text(dark); val dim = pal.dim(dark); val edge = pal.border(dark)
    val face = pal.surface.copy(alpha = if (solid) 1f else 0.62f)
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) = drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
    fun edged(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(edge, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), Stroke(0.7f * u))
    drawRect(pal.bg)
    if (aurora) { // Aurora: light curtains, a crystal capsule, an arched gate, small gates, a dock with a beam
        drawCircle(AuroraMint.copy(alpha = 0.30f), 24f * u, Offset(24f * u, 30f * u)); drawCircle(AuroraPink.copy(alpha = 0.24f), 24f * u, Offset(80f * u, 60f * u))
        box(22f, 6f, 56f, 8f, 4f, face); edged(22f, 6f, 56f, 8f, 4f); box(40f, 8.8f, 20f, 2.6f, 1.3f, text)
        fun arch(x: Float, y: Float, w: Float, h: Float, a: Float) {
            val p = Path().apply { addRoundRect(RoundRect(x * u, y * u, (x + w) * u, (y + h) * u, CornerRadius(w / 2f * u), CornerRadius(w / 2f * u), CornerRadius(3f * u), CornerRadius(3f * u))) }
            drawPath(p, Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f * a), face), y * u, (y + h) * u)); drawPath(p, AuroraMint.copy(alpha = 0.7f * a), style = Stroke(0.7f * u))
        }
        arch(22f, 20f, 56f, 44f, 1f); box(34f, 55f, 32f, 3.4f, 1.7f, Color.White.copy(alpha = 0.85f))
        for (x in floatArrayOf(10f, 38f, 66f)) arch(x, 70f, 24f, 24f, 0.9f)
        drawPath(Path().apply { moveTo(40f * u, 100f * u); lineTo(60f * u, 100f * u); lineTo(70f * u, 94f * u); lineTo(30f * u, 94f * u); close() }, AuroraMint.copy(alpha = 0.25f))
        box(10f, 100f, 80f, 12f, 6f, face); edged(10f, 100f, 80f, 12f, 6f); drawCircle(AuroraMint, 4f * u, Offset(50f * u, 106f * u), style = Stroke(0.8f * u))
        return
    }
    if (cosmic) { // Orbit: nebula, a few stars, a planet with its ring between two small ones, a dock with a lifted planet
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
    if (!solid) { drawCircle(accent.copy(alpha = 0.55f), 26f * u, Offset(78f * u, 40f * u)); drawCircle(accent.copy(alpha = 0.30f), 18f * u, Offset(22f * u, 78f * u)) }
    box(8f, 8f, 34f, 5.5f, 2.7f, text)
    drawCircle(dim, 4f * u, Offset(88f * u, 10.7f * u), style = Stroke(0.9f * u))
    drawRoundRect(
        Brush.linearGradient(listOf(accent.copy(alpha = if (solid) 0.75f else 0.45f), face), Offset(8f * u, 22f * u), Offset(92f * u, 66f * u)),
        Offset(8f * u, 22f * u), Size(84f * u, 44f * u), CornerRadius(7f * u),
    )
    edged(8f, 22f, 84f, 44f, 7f)
    box(13f, 54f, 30f, 4f, 2f, Color.White.copy(alpha = 0.9f))
    for (x in floatArrayOf(8f, 52f)) {
        box(x, 72f, 40f, 22f, 6f, face); edged(x, 72f, 40f, 22f, 6f)
        box(x + 5f, 78f, 18f, 3f, 1.5f, text); box(x + 5f, 84f, 12f, 2.5f, 1.2f, dim)
    }
    if (solid) {
        drawRect(pal.bg, Offset(0f, 102f * u), Size(100f * u, 13f * u)); drawRect(edge, Offset(0f, 102f * u), Size(100f * u, 0.7f * u))
        box(43f, 102f, 14f, 1.6f, 0.8f, accent)
    } else {
        box(14f, 101f, 72f, 11f, 5.5f, face); edged(14f, 101f, 72f, 11f, 5.5f); box(40f, 104.5f, 20f, 4f, 2f, accent.copy(alpha = 0.6f))
    }
}

/** A device outline for the display tiles: a phone, a TV on its stand, or both for Auto. */
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
