package com.kemalcetin.aialarm.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.core.locale.AppLanguage
import com.kemalcetin.aialarm.core.locale.AppLanguageManager
import com.kemalcetin.aialarm.core.permission.BatteryOptimizationManager
import com.kemalcetin.aialarm.core.permission.ExactAlarmPermissionManager
import com.kemalcetin.aialarm.core.permission.FullScreenIntentPermissionManager
import com.kemalcetin.aialarm.core.permission.NotificationPermissionManager
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.ui.clock.ClockStyle
import com.kemalcetin.aialarm.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultSnoozeMinutes: Int = AppPreferences.DEFAULT_SNOOZE,
    val smartSuggestionsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clockStyle: ClockStyle = AppPreferences.DEFAULT_CLOCK_STYLE,
    val language: AppLanguage = AppLanguage.default,
    val exactAlarmAllowed: Boolean = true,
    val notificationsGranted: Boolean = true,
    val fullScreenAvailable: Boolean = true,
    val batteryOptimized: Boolean = false,
    val appVersion: String = ""
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val appLanguageManager: AppLanguageManager,
    private val exactPermission: ExactAlarmPermissionManager,
    private val notificationPermission: NotificationPermissionManager,
    private val fullScreenPermission: FullScreenIntentPermissionManager,
    private val batteryOptimization: BatteryOptimizationManager,
    private val appVersion: String
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combineSettings()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(appVersion = appVersion)
        )

    private fun combineSettings(): Flow<SettingsUiState> =
        combine(
            preferences.defaultSnoozeMinutes,
            preferences.autoCreateEnabled,
            preferences.themeMode,
            preferences.clockStyle,
            preferences.selectedLanguageTag
        ) { snooze, autoCreate, theme, clock, languageTag ->
            SettingsUiState(
                defaultSnoozeMinutes = snooze,
                smartSuggestionsEnabled = autoCreate,
                themeMode = theme,
                clockStyle = clock,
                language = AppLanguage.resolve(languageTag),
                exactAlarmAllowed = exactPermission.canScheduleExactAlarms(),
                notificationsGranted = notificationPermission.isGranted(),
                fullScreenAvailable = fullScreenPermission.canUseFullScreenIntent(),
                batteryOptimized = batteryOptimization.isIgnoringBatteryOptimizations(),
                appVersion = appVersion
            )
        }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch { preferences.setDefaultSnooze(minutes) }
    }

    fun setSmartSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoCreateEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setClockStyle(style: ClockStyle) {
        viewModelScope.launch { preferences.setClockStyle(style) }
    }

    /**
     * Changes the application language. The manager updates the in-memory value
     * and default Locale synchronously (so an immediate activity recreation shows
     * the new language) and persists the selection in the background.
     */
    fun setLanguage(language: AppLanguage) {
        appLanguageManager.setLanguage(language)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    preferences = container.appPreferences,
                    appLanguageManager = container.appLanguageManager,
                    exactPermission = container.exactAlarmPermissionManager,
                    notificationPermission = container.notificationPermissionManager,
                    fullScreenPermission = container.fullScreenIntentPermissionManager,
                    batteryOptimization = container.batteryOptimizationManager,
                    appVersion = readVersionName(container.appContext)
                )
            }
        }

        private fun readVersionName(context: Context): String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
}
