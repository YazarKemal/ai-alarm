package com.kemalcetin.aialarm.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

/**
 * Application-level preferences stored in DataStore (distinct from Room alarm records).
 */
class AppPreferences(private val context: Context) {

    val defaultSnoozeMinutes: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_DEFAULT_SNOOZE] ?: DEFAULT_SNOOZE }

    suspend fun setDefaultSnooze(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_SNOOZE] = minutes }
    }

    val autoCreateEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_AUTO_CREATE_ENABLED] ?: DEFAULT_AUTO_CREATE_ENABLED }

    suspend fun setAutoCreateEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CREATE_ENABLED] = enabled }
    }

    val aiLastAutoCreateTime: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[KEY_AI_LAST_AUTO_CREATE] ?: 0L }

    suspend fun setAiLastAutoCreateTime(epochMillis: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_AI_LAST_AUTO_CREATE] = epochMillis }
    }

    companion object {
        const val DEFAULT_SNOOZE = 5
        const val DEFAULT_AUTO_CREATE_ENABLED = true
        private val KEY_DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        private val KEY_AUTO_CREATE_ENABLED = booleanPreferencesKey("ai_auto_create_enabled")
        private val KEY_AI_LAST_AUTO_CREATE = longPreferencesKey("ai_last_auto_create_time")
    }
}
