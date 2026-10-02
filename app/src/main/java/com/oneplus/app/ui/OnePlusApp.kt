package com.oneplus.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneplus.app.R

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Rounded.Home),
    Channels(R.string.tab_channels, Icons.AutoMirrored.Rounded.List),
    Settings(R.string.tab_settings, Icons.Rounded.Settings),
}

@Composable
fun OnePlusApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val bg = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.weight(1f))
                Tab.entries.forEach {
                    NavigationRailItem(tab == it, { tab = it }, { Icon(it.icon, null) }, label = { Text(stringResource(it.label)) })
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    Crossfade(tab, animationSpec = tween(180), label = "tab") {
                        when (it) {
                            Tab.Home -> HomeScreen(state, vm::onQuery)
                            Tab.Channels -> ChannelsScreen(state.channels)
                            Tab.Settings -> SettingsScreen()
                        }
                    }
                }
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach {
                        NavigationBarItem(tab == it, { tab = it }, { Icon(it.icon, null) }, label = { Text(stringResource(it.label)) })
                    }
                }
            }
        }
    }
}
