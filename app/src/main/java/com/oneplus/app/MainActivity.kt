package com.oneplus.app

import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.oneplus.app.ui.OnePlusApp
import com.oneplus.app.ui.system.LocalSystemBars
import com.oneplus.app.ui.system.OnePlusTheme
import com.oneplus.app.ui.system.SystemBars
import com.oneplus.app.ui.system.ThemeController
import com.oneplus.app.ui.system.ThemeStore
import com.oneplus.app.ui.system.TvScreen
import com.oneplus.app.ui.system.resolveDark
import com.oneplus.app.ui.system.resolveTv
import com.oneplus.app.ui.system.trimImages

class MainActivity : ComponentActivity() {
    private lateinit var bars: SystemBars

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 26) window.setFormat(PixelFormat.RGBA_8888)
        enableEdgeToEdge()
        bars = SystemBars(window)
        setContent {
            val theme = remember { ThemeController(ThemeStore(applicationContext)) }
            val dark = theme.prefs.mode.resolveDark()
            DisposableEffect(dark) {
                val clear = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                bars.apply()
                onDispose { }
            }
            val hideStatus = theme.prefs.hideStatusBar
            val hideNav = theme.prefs.hideNavBar
            DisposableEffect(hideStatus, hideNav) {
                bars.hideStatus = hideStatus
                bars.hideNavigation = hideNav
                bars.apply()
                onDispose { }
            }
            CompositionLocalProvider(LocalSystemBars provides bars) {
                TvScreen(theme.prefs.display.resolveTv()) { OnePlusTheme(theme.prefs, dark) { OnePlusApp(theme) } }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        trimImages(level)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) bars.apply()
    }
}
