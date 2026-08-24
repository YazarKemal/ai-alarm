package com.kemalcetin.aialarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val defaultSnoozeMinutes: Int = AppPreferences.DEFAULT_SNOOZE,
    val autoCreateEnabled: Boolean = true,
    val deepSeekConfigured: Boolean = false
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val deepSeekConfigured: Boolean
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combineSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun combineSettings(): Flow<SettingsUiState> =
        combine(
            preferences.defaultSnoozeMinutes,
            preferences.autoCreateEnabled
        ) { snooze, autoCreate ->
            SettingsUiState(
                defaultSnoozeMinutes = snooze,
                autoCreateEnabled = autoCreate,
                deepSeekConfigured = deepSeekConfigured
            )
        }

    fun setDefaultSnooze(minutes: Int) {
        viewModelScope.launch { preferences.setDefaultSnooze(minutes) }
    }

    fun setAutoCreateEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoCreateEnabled(enabled) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    preferences = container.appPreferences,
                    deepSeekConfigured = container.aiInsightsProvider != null
                )
            }
        }
    }
}
