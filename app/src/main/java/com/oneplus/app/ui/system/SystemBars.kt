package com.oneplus.app.ui.system

import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SystemBars(window: Window) {
    private val decor = window.decorView
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

    fun settle() {
        apply()
        decor.post { apply() }
        decor.postDelayed({ apply() }, 400)
    }

    private fun set(type: Int, hidden: Boolean) = if (hidden) controller.hide(type) else controller.show(type)
}

val LocalSystemBars = staticCompositionLocalOf<SystemBars?> { null }
