package com.pushitup.core.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore("onboarding_prefs")

object OnboardingKeys {
    val TOUR_COMPLETE = booleanPreferencesKey("tour_complete")
}

fun DataStore<Preferences>.tourCompleteFlow(): Flow<Boolean> =
    data.map { it[OnboardingKeys.TOUR_COMPLETE] ?: false }
