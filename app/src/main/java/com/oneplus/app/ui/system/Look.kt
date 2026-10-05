package com.oneplus.app.ui.system

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.oneplus.app.data.Library
import com.oneplus.app.ui.GlassLook
import com.oneplus.app.ui.UiState

/*
 * A LOOK is one complete visual identity of the phone UI. The pages never ask "which look is on?"; they read what the look
 * hands them (colours, surface type, bar sizes) or call the slots it owns (header, nav bar, home).
 *
 * To add a look (say "Mono"):
 *   1. write its header / nav / home composables (copy the closest ones in FeedHeader.kt, OnePlusApp.kt, LuxHome.kt);
 *   2. declare `val MonoLook = Look(...)` in ui/Looks.kt and add it to `Looks`;
 *   3. add its name (and blurb) to strings.xml.
 * The style picker, the stored choice, the colours and the surfaces (glass() / ambient()) follow by themselves.
 * Pages shared by every look (details, lists, settings) read `solid` and the palette, so they already wear the new look.
 */

/** Colours a look brings. A null [border] / [text] / [dim] means the standard one (see the accessors). */
@Immutable
class Palette(val bg: Color, val surface: Color, val border: Color? = null, val text: Color? = null, val dim: Color? = null) {
    fun border(dark: Boolean) = border ?: if (dark) Color(0x26FFFFFF) else Color(0x2E5B6B8C)
    fun text(dark: Boolean) = text ?: if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    fun dim(dark: Boolean) = dim ?: if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
}

/** What a look's Home page receives. */
class HomeArgs(
    val state: UiState, val list: LazyListState, val portrait: Boolean, val wide: Boolean, val lib: Library,
    val onMovie: (Int) -> Unit, val onChannel: (Int) -> Unit, val onAllMovies: () -> Unit, val onAllChannels: () -> Unit,
    val onMatches: (Int) -> Unit,
)

/** What a look's header receives. [collapse] and [reveal] are read in draw / layout only, so scrolling never recomposes. */
class HeaderArgs(
    val title: String, val context: String?, val hasSearch: Boolean, val query: String, val onQuery: (String) -> Unit,
    /** 0 = the page is at its top, 1 = scrolled past [Look.collapseAt]. */
    val collapse: () -> Float,
    /** 1 = the bar is in view, 0 = it has slid away (the page is being scrolled down). */
    val reveal: () -> Float,
    /** Not null on an inner page (a settings sub-page): the bar shows a back button. */
    val onBack: (() -> Unit)? = null,
)

@Immutable
class Look(
    /** Persisted. Never change the id of a look that has shipped. */
    val id: String,
    @StringRes val label: Int,
    @StringRes val blurb: Int,
    /** Surfaces: false = translucent glass tinted by the app colour; true = flat, solid, hairline borders, pure palette. */
    val solid: Boolean,
    /** Room the header takes above the pages, and the nav bar below them (without the system insets). */
    val top: Dp,
    val bottom: Dp,
    /** How far the page scrolls before [HeaderArgs.collapse] reaches 1. */
    val collapseAt: Dp,
    /** Deep-space dress: a star field behind the pages and a luminous rim on glass surfaces. */
    val cosmic: Boolean = false,
    /** Northern-lights dress: drifting curtains of light behind the pages and a lit rim on glass surfaces. */
    val aurora: Boolean = false,
    /** Football dress: night pitch with floodlights, squarer chalk-lined surfaces. */
    val pitch: Boolean = false,
    val palette: (dark: Boolean, amoled: Boolean, tv: Boolean) -> Palette,
    val home: @Composable (HomeArgs) -> Unit,
    val header: @Composable (HeaderArgs, Modifier) -> Unit,
    val nav: @Composable (selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) -> Unit,
)

val LocalLook = staticCompositionLocalOf<Look> { GlassLook }
