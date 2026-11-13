package com.pushitup.core.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val Context.themeStore: DataStore<Preferences> by preferencesDataStore("theme_prefs")

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

fun DataStore<Preferences>.themeModeFlow(): Flow<ThemeMode> =
    data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK"  -> ThemeMode.DARK
            else    -> ThemeMode.SYSTEM
        }
    }

suspend fun DataStore<Preferences>.setThemeMode(mode: ThemeMode) {
    edit { it[THEME_MODE_KEY] = mode.name }
}
