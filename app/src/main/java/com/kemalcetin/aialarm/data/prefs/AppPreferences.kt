package com.kemalcetin.aialarm.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kemalcetin.aialarm.core.locale.AppLanguage
import com.kemalcetin.aialarm.core.planning.AiPlanningPreferences
import com.kemalcetin.aialarm.core.planning.WakePreference
import com.kemalcetin.aialarm.ui.clock.ClockStyle
import com.kemalcetin.aialarm.ui.theme.ThemeMode
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

    val clockStyle: Flow<ClockStyle> = context.dataStore.data
        .map { prefs ->
            prefs[KEY_CLOCK_STYLE]
                ?.let { runCatching { ClockStyle.valueOf(it) }.getOrNull() }
                ?: DEFAULT_CLOCK_STYLE
        }

    suspend fun setClockStyle(style: ClockStyle) {
        context.dataStore.edit { prefs -> prefs[KEY_CLOCK_STYLE] = style.name }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { prefs ->
            prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    /**
     * User-selected application language. English is the default — the app never
     * follows the device locale on first launch.
     */
    val selectedLanguageTag: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_LANGUAGE_TAG] ?: AppLanguage.DEFAULT_TAG }

    suspend fun setSelectedLanguage(tag: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE_TAG] = tag }
    }

    // ---------------------------------------------------------------------
    // PromptHaven AI planning preferences (local-only, never uploaded)
    // ---------------------------------------------------------------------

    /** Current AI planning preferences. commuteMinutes -1 means "not set". */
    val aiPlanningPreferences: Flow<AiPlanningPreferences> = context.dataStore.data
        .map { prefs ->
            AiPlanningPreferences(
                targetSleepMinutes = prefs[KEY_TARGET_SLEEP] ?: 480,
                preparationMinutes = prefs[KEY_PREPARATION] ?: 30,
                commuteMinutes = prefs[KEY_COMMUTE]?.takeIf { it >= 0 },
                bufferMinutes = prefs[KEY_BUFFER] ?: 15,
                wakePreference = prefs[KEY_WAKE_PREF]
                    ?.let { runCatching { WakePreference.valueOf(it) }.getOrNull() }
                    ?: WakePreference.LATEST_POSSIBLE,
                preAlarmEnabled = prefs[KEY_PRE_ALARM_ENABLED] ?: true,
                preAlarmMinutes = prefs[KEY_PRE_ALARM_MIN] ?: 10,
                backupAlarmEnabled = prefs[KEY_BACKUP_ENABLED] ?: true,
                backupAlarmMinutes = prefs[KEY_BACKUP_MIN] ?: 10
            )
        }

    suspend fun setAiPlanningPreferences(prefs: AiPlanningPreferences) {
        context.dataStore.edit { p ->
            p[KEY_TARGET_SLEEP] = prefs.targetSleepMinutes
            p[KEY_PREPARATION] = prefs.preparationMinutes
            p[KEY_COMMUTE] = prefs.commuteMinutes ?: -1
            p[KEY_BUFFER] = prefs.bufferMinutes
            p[KEY_WAKE_PREF] = prefs.wakePreference.name
            p[KEY_PRE_ALARM_ENABLED] = prefs.preAlarmEnabled
            p[KEY_PRE_ALARM_MIN] = prefs.preAlarmMinutes
            p[KEY_BACKUP_ENABLED] = prefs.backupAlarmEnabled
            p[KEY_BACKUP_MIN] = prefs.backupAlarmMinutes
        }
    }

    companion object {
        const val DEFAULT_SNOOZE = 5
        // Never let a newly installed app create alarms autonomously without an explicit opt-in.
        const val DEFAULT_AUTO_CREATE_ENABLED = false
        val DEFAULT_CLOCK_STYLE = ClockStyle.DIGITAL_MINIMAL
        private val KEY_DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        private val KEY_AUTO_CREATE_ENABLED = booleanPreferencesKey("ai_auto_create_enabled")
        private val KEY_AI_LAST_AUTO_CREATE = longPreferencesKey("ai_last_auto_create_time")
        private val KEY_CLOCK_STYLE = stringPreferencesKey("clock_style")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("selected_language")

        // AI planning preferences
        private val KEY_TARGET_SLEEP = intPreferencesKey("ai_target_sleep_min")
        private val KEY_PREPARATION = intPreferencesKey("ai_preparation_min")
        private val KEY_COMMUTE = intPreferencesKey("ai_commute_min") // -1 = unset
        private val KEY_BUFFER = intPreferencesKey("ai_buffer_min")
        private val KEY_WAKE_PREF = stringPreferencesKey("ai_wake_preference")
        private val KEY_PRE_ALARM_ENABLED = booleanPreferencesKey("ai_pre_alarm_enabled")
        private val KEY_PRE_ALARM_MIN = intPreferencesKey("ai_pre_alarm_min")
        private val KEY_BACKUP_ENABLED = booleanPreferencesKey("ai_backup_alarm_enabled")
        private val KEY_BACKUP_MIN = intPreferencesKey("ai_backup_min")
    }
}
