package com.oneplus.app.ui.system

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.oneplus.app.data.Kind
import com.oneplus.app.data.Library
import com.oneplus.app.ui.GlassLook
import com.oneplus.app.ui.UiState

@Immutable
class Palette(val bg: Color, val surface: Color, val border: Color? = null, val text: Color? = null, val dim: Color? = null) {
    fun border(dark: Boolean) = border ?: if (dark) Color(0x26FFFFFF) else Color(0x2E5B6B8C)
    fun text(dark: Boolean) = text ?: if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    fun dim(dark: Boolean) = dim ?: if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
}

class HomeArgs(
    val state: UiState, val list: LazyListState, val portrait: Boolean, val wide: Boolean, val lib: Library,
    val onMovie: (Int) -> Unit, val onChannel: (Int) -> Unit, val onAll: (Kind) -> Unit, val onAllChannels: () -> Unit,
    val onMatches: (Int) -> Unit,
)

class HeaderArgs(
    val title: String, val context: String?, val hasSearch: Boolean, val query: String, val onQuery: (String) -> Unit,
    val collapse: () -> Float,
    val reveal: () -> Float,
    val onBack: (() -> Unit)? = null,
)

@Immutable
class Look(
    val id: String,
    @StringRes val label: Int,
    @StringRes val blurb: Int,
    val top: Dp,
    val bottom: Dp,
    val collapseAt: Dp,
    val cosmic: Boolean = false,
    val pitch: Boolean = false,
    val anime: Boolean = false,
    val palette: (dark: Boolean, amoled: Boolean) -> Palette,
    val home: @Composable (HomeArgs) -> Unit,
    val header: @Composable (HeaderArgs, Modifier) -> Unit,
    val nav: @Composable (selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) -> Unit,
)

val LocalLook = staticCompositionLocalOf<Look> { GlassLook }
