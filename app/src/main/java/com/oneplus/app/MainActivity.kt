package com.oneplus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.oneplus.app.ui.OnePlusApp
import com.oneplus.app.ui.theme.OnePlusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnePlusTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { OnePlusApp() }
            }
        }
    }
}
