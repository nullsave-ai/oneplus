package com.oneplus.app.ui.system

import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The one owner of the phone's status bar (notifications) and navigation bar (back / home / recents buttons, or the gesture pill).
 * They are hidden when the user asked for it ([hideStatus], [hideNavigation]) or while the fullscreen player is up ([immersive]);
 * a swipe from the edge still brings them back for a moment. [apply] is safe to call at any time and works on every Android
 * version the app supports (the compat controller falls back to the old system-UI flags below Android 11).
 */
class SystemBars(window: Window) {
    private val controller = WindowCompat.getInsetsController(window, window.decorView).also {
        it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    var hideStatus = false
    var hideNavigation = false
    var immersive = false

    fun apply() {
        set(WindowInsetsCompat.Type.statusBars(), hideStatus || immersive)
        set(WindowInsetsCompat.Type.navigationBars(), hideNavigation || immersive)
    }

    private fun set(type: Int, hidden: Boolean) = if (hidden) controller.hide(type) else controller.show(type)
}

val LocalSystemBars = staticCompositionLocalOf<SystemBars?> { null }
