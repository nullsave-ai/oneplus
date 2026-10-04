package com.oneplus.app

import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oneplus.app.ui.OnePlusApp
import com.oneplus.app.ui.system.OnePlusTheme
import com.oneplus.app.ui.system.ThemeController
import com.oneplus.app.ui.system.ThemeStore
import com.oneplus.app.ui.system.TvScreen
import com.oneplus.app.ui.system.resolveDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pre-Oreo low-end devices may composite the window in 16-bit colour, which bands every gradient; force 32-bit.
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 26) window.setFormat(PixelFormat.RGBA_8888)
        enableEdgeToEdge() // edge-to-edge from the first frame; bar icon colors are then synced with the chosen theme below
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.oneplus.app.player.NetflyEngine.init(applicationContext)
        }
        setContent {
            val theme = remember { ThemeController(ThemeStore(applicationContext)) }
            val dark = theme.prefs.mode.resolveDark() // System / Light / Dark
            DisposableEffect(dark) {
                // Status/nav icon color must follow the app's theme, not the device's (they differ when the user forces a mode).
                val clear = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val hideStatus = theme.prefs.hideStatusBar
            DisposableEffect(hideStatus) {
                // The user's choice for the phone's status (notification) bar; a swipe from the top edge still reveals it for a moment.
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hideStatus) bars.hide(WindowInsetsCompat.Type.statusBars()) else bars.show(WindowInsetsCompat.Type.statusBars())
                onDispose { }
            }
            TvScreen(theme.prefs.tvMode) { OnePlusTheme(theme.prefs, dark) { OnePlusApp(theme) } }
        }
    }
}
