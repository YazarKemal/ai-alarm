package com.kemalcetin.aialarm.ui.ringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.core.alarm.AlarmController
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

class AlarmRingingViewModel(
    private val repository: AlarmRepository,
    private val controller: AlarmController,
    private val alarmId: Long
) : ViewModel() {

    private val _label = MutableStateFlow("Alarm")
    val label: StateFlow<String> = _label

    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime

    init {
        viewModelScope.launch {
            repository.getAlarm(alarmId)?.let { alarm ->
                _label.value = alarm.label.ifBlank { "Alarm" }
            }
        }
        viewModelScope.launch {
            while (true) {
                _currentTime.value = LocalTime.now()
                delay(1_000)
            }
        }
    }

    fun snooze() {
        controller.snooze(alarmId) {}
    }

    fun dismiss() {
        controller.dismiss(alarmId) {}
    }

    companion object {
        fun factory(container: AppContainer, alarmId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlarmRingingViewModel(
                        repository = container.alarmRepository,
                        controller = container.alarmController,
                        alarmId = alarmId
                    )
                }
            }
    }
}
