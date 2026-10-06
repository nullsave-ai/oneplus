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
    top = 84.dp, bottom = 112.dp, collapseAt = 96.dp,
    palette = { dark, amoled ->
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

/** Deep space: planets instead of posters, a star field, orbiting nav, 3D coverflow scrolling, a warp between pages. */
val OrbitLook = Look(
    id = "Orbit", label = R.string.style_orbit, blurb = R.string.style_orbit_blurb,
    top = 72.dp, bottom = 112.dp, collapseAt = 48.dp, cosmic = true,
    palette = { dark, _ ->
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

/** Football night: floodlit pitch, a hanging scoreboard, a dock with a rolling ball, collectible player cards. */
val PitchLook = Look(
    id = "Pitch", label = R.string.style_pitch, blurb = R.string.style_pitch_blurb,
    top = 72.dp, bottom = 120.dp, collapseAt = 40.dp, pitch = true,
    palette = { dark, _ ->
        if (dark) Palette(
            Color(0xFF06130D), Color(0xFF0E2218),
            border = Color(0x40F5FFE9), text = Color(0xFFF4FFF1), dim = Color(0xFF8FB39C),
        ) else Palette(
            Color(0xFFEAF5E6), Color.White,
            border = Color(0x3314532D), text = Color(0xFF08210F), dim = Color(0xFF4F7557),
        )
    },
    home = { a -> PitchHome(a) },
    header = { a, m -> PitchHeader(a, m) },
    nav = { selected, onSelect, m -> PitchNav(selected, onSelect, m) },
)

/** Japanese anime / manga: stickers with ink outlines, speed lines, halftone, falling sakura, comic-panel dock. */
val AnimeLook = Look(
    id = "Anime", label = R.string.style_anime, blurb = R.string.style_anime_blurb,
    top = 72.dp, bottom = 112.dp, collapseAt = 40.dp, anime = true,
    palette = { dark, _ ->
        if (dark) Palette(
            Color(0xFF140C2E), Color(0xFF211548),
            border = Color(0xCCFFE6F3), text = Color(0xFFFFF5FA), dim = Color(0xFFB8A6E8),
        ) else Palette(
            Color(0xFFFFF0F6), Color.White,
            border = Color(0xCC1D1033), text = Color(0xFF1D1033), dim = Color(0xFF6E5C8E),
        )
    },
    home = { a -> AnimeHome(a) },
    header = { a, m -> AnimeHeader(a, m) },
    nav = { selected, onSelect, m -> AnimeNav(selected, onSelect, m) },
)

/** Every look the user can pick, in the order of the picker. */
val Looks = listOf(GlassLook, OrbitLook, PitchLook, AnimeLook)

/** The look stored under [id]; anything unknown (an old or damaged value) falls back to the first. */
fun lookOf(id: String?): Look = Looks.firstOrNull { it.id == id } ?: Looks[0]
