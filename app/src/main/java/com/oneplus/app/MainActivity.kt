package com.oneplus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oneplus.app.ui.OnePlusApp
import androidx.compose.runtime.*
import com.oneplus.app.ui.system.OnePlusTheme
import com.oneplus.app.ui.system.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // transparent bars; icon color follows the system theme
        setContent {
            val store = remember { ThemeStore(applicationContext) }
            var accent by remember { mutableStateOf(store.load()) }
            OnePlusTheme(accent) { OnePlusApp(accent, { accent = it; store.save(it) }) }
        }
    }
}
