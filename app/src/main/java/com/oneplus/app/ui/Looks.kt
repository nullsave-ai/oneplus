package com.oneplus.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oneplus.app.R
import com.oneplus.app.ui.system.*

// The registry of phone looks (the architecture is explained in system/Look.kt). The first one is the default.

/** Translucent, tinted by the app colour; a floating pill header and a floating nav island. */
val GlassLook = Look(
    id = "Glass", label = R.string.style_glass, blurb = R.string.style_glass_blurb,
    solid = false, top = 84.dp, bottom = 112.dp, collapseAt = 96.dp,
    palette = { dark, amoled, _ ->
        Palette(if (dark) Color(0xFF0A0E16) else Color(0xFFEEF2F9), if (amoled) Color(0xFF16181D) else if (dark) Color(0xFF1A2333) else Color.White)
    },
    home = { a -> HomeScreen(a.state, a.list, a.wide, a.lib, a.onMovie, a.onChannel, a.onAllMovies, a.onAllChannels, a.onMatches) },
    header = { a, m ->
        FloatingToolbar(
            a.title, a.context, a.hasSearch, a.query, a.onQuery, a.collapse,
            m.widthIn(max = 880.dp).padding(start = 16.dp, end = 16.dp, top = topInset() + 12.dp), onBack = a.onBack,
        )
    },
    nav = { selected, onSelect, m -> LiquidNav(selected, onSelect, m) },
)

/** Editorial: ink black / warm ivory, hairlines, serif headlines; a header that collapses and slides away, a hero carousel. */
val FeedLook = Look(
    id = "Feed", label = R.string.style_feed, blurb = R.string.style_feed_blurb,
    solid = true, top = 68.dp, bottom = 64.dp, collapseAt = 56.dp,
    palette = { dark, amoled, tv ->
        // TV Mode keeps the neutral greys of its own feed; the phone gets the ink / ivory dress
        if (tv) Palette(if (dark) Color(0xFF0F0F0F) else Color.White, if (dark) Color(0xFF212121) else Color(0xFFF1F1F1))
        else Palette(
            if (dark) Color(0xFF0B0B0E) else Color(0xFFF6F3EE), if (amoled) Color(0xFF111114) else if (dark) Color(0xFF16161B) else Color.White,
            border = if (dark) Color(0x1FFFFFFF) else Color(0x24402F14),
            text = if (dark) Color(0xFFF3EFE8) else Color(0xFF16130F), dim = if (dark) Color(0xFF9B968D) else Color(0xFF7A746A),
        )
    },
    home = { a -> LuxHome(a) },
    header = { a, m -> FeedHeader(a, m) },
    nav = { selected, onSelect, m -> FeedBar(selected, onSelect, m) },
)

/** Every look the user can pick, in the order of the picker. */
val Looks = listOf(GlassLook, FeedLook)

/** The look stored under [id]; anything unknown (an old or damaged value) falls back to the first. */
fun lookOf(id: String?): Look = Looks.firstOrNull { it.id == id } ?: Looks[0]
