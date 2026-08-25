package com.kemalcetin.aialarm.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClockStyleViewModel(
    private val preferences: AppPreferences
) : ViewModel() {

    val currentStyle: StateFlow<ClockStyle> = preferences.clockStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.DEFAULT_CLOCK_STYLE)

    fun select(style: ClockStyle) {
        viewModelScope.launch { preferences.setClockStyle(style) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ClockStyleViewModel(container.appPreferences) }
        }
    }
}
