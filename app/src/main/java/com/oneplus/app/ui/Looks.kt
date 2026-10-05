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

/** Deep space: planets instead of posters, a star field, orbiting nav, 3D coverflow scrolling, a warp between pages. */
val OrbitLook = Look(
    id = "Orbit", label = R.string.style_orbit, blurb = R.string.style_orbit_blurb,
    solid = false, top = 72.dp, bottom = 112.dp, collapseAt = 48.dp, cosmic = true,
    palette = { dark, _, _ ->
        if (dark) Palette(
            Color(0xFF04050E), Color(0xFF0D1124),
            border = Color(0x33AFC4FF), text = Color(0xFFEAF0FF), dim = Color(0xFF8A93B8),
        ) else Palette(
            Color(0xFFEEF0FC), Color.White,
            border = Color(0x2E3B4BA0), text = Color(0xFF0B1030), dim = Color(0xFF5C6490),
        )
    },
    home = { a -> OrbitHome(a) },
    header = { a, m -> OrbitHeader(a, m) },
    nav = { selected, onSelect, m -> OrbitNav(selected, onSelect, m) },
)

/** Northern lights: a light you walk through. Gates on a turning cube, drifting rows, a dock that opens portals and shoots beams. */
val AuroraLook = Look(
    id = "Aurora", label = R.string.style_aurora, blurb = R.string.style_aurora_blurb,
    solid = false, top = 72.dp, bottom = 120.dp, collapseAt = 40.dp, aurora = true,
    palette = { dark, _, _ ->
        if (dark) Palette(
            Color(0xFF040B0D), Color(0xFF0B1A1E),
            border = Color(0x3366FFD1), text = Color(0xFFE8FFF7), dim = Color(0xFF7FA79C),
        ) else Palette(
            Color(0xFFEBF7F3), Color.White,
            border = Color(0x2E1F7A66), text = Color(0xFF0A2420), dim = Color(0xFF4F7A70),
        )
    },
    home = { a -> AuroraHome(a) },
    header = { a, m -> AuroraHeader(a, m) },
    nav = { selected, onSelect, m -> AuroraNav(selected, onSelect, m) },
)

/** Every look the user can pick, in the order of the picker. */
val Looks = listOf(GlassLook, FeedLook, OrbitLook, AuroraLook)

/** The look stored under [id]; anything unknown (an old or damaged value) falls back to the first. */
fun lookOf(id: String?): Look = Looks.firstOrNull { it.id == id } ?: Looks[0]
