package com.oneplus.app.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.store by preferencesDataStore("app", produceMigrations = { c ->
    listOf("theme", "channels", "telegram", "player", "library").map { SharedPreferencesMigration(c, it) }
})

class Prefs(private val ctx: Context, private val scope: CoroutineScope) {
    @Volatile private var now: Preferences = runBlocking { ctx.store.data.first() }

    fun string(k: String) = now[stringPreferencesKey(k)]
    fun float(k: String) = now[floatPreferencesKey(k)]
    fun int(k: String) = now[intPreferencesKey(k)]
    fun bool(k: String) = now[booleanPreferencesKey(k)]

    fun put(vararg kv: Pair<String, Any>) = change { kv.forEach { (k, v) -> write(k, v) } }

    fun remove(vararg keys: String) = change { keys.forEach { n -> asMap().keys.firstOrNull { it.name == n }?.let { remove(it) } } }

    private fun change(block: MutablePreferences.() -> Unit) {
        now = now.toMutablePreferences().apply(block)
        scope.launch { ctx.store.edit { it.block() } }
    }

    private fun MutablePreferences.write(k: String, v: Any) {
        when (v) {
            is String -> this[stringPreferencesKey(k)] = v
            is Float -> this[floatPreferencesKey(k)] = v
            is Int -> this[intPreferencesKey(k)] = v
            is Long -> this[longPreferencesKey(k)] = v
            is Boolean -> this[booleanPreferencesKey(k)] = v
        }
    }
}
