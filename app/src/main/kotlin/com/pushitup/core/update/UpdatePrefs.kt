package com.pushitup.core.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.updateStore: DataStore<Preferences> by preferencesDataStore("update_prefs")

private val LAST_SEEN_CHANGELOG_VERSION = intPreferencesKey("last_seen_changelog_version")
private val LAST_KNOWN_LATEST_VERSION   = intPreferencesKey("last_known_latest_version")

/** The versionCode for which the user has already seen the What's New dialog (0 = never). */
fun DataStore<Preferences>.lastSeenChangelogVersionFlow(): Flow<Int> =
    data.map { it[LAST_SEEN_CHANGELOG_VERSION] ?: 0 }

suspend fun DataStore<Preferences>.markChangelogSeen(versionCode: Int) {
    edit { it[LAST_SEEN_CHANGELOG_VERSION] = versionCode }
}

/** The latest versionCode we fetched from Firestore (so the WorkManager worker can compare). */
fun DataStore<Preferences>.lastKnownLatestVersionFlow(): Flow<Int> =
    data.map { it[LAST_KNOWN_LATEST_VERSION] ?: 0 }

suspend fun DataStore<Preferences>.saveLatestVersion(versionCode: Int) {
    edit { it[LAST_KNOWN_LATEST_VERSION] = versionCode }
}
