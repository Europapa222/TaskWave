package com.taskwave.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "taskwave_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override") // "system" | "light" | "dark"
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }
    val darkModeOverride: Flow<String> = context.dataStore.data.map { it[DARK_MODE_OVERRIDE] ?: "system" }

    suspend fun setOnboardingDone() { context.dataStore.edit { it[ONBOARDING_DONE] = true } }
    suspend fun setDarkModeOverride(value: String) { context.dataStore.edit { it[DARK_MODE_OVERRIDE] = value } }
}
