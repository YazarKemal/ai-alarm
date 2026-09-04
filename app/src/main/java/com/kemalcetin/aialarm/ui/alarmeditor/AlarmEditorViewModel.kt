package com.kemalcetin.aialarm.ui.alarmeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.core.alarm.AlarmScheduler
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.ui.aipreview.AiPreviewResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class AlarmEditorUiState(
    val alarmId: Long = -1L,
    val isEdit: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Alarm",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val oneTimeDate: LocalDate? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5
)

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val preferences: AppPreferences,
    private val alarmId: Long,
    private val defaultLabel: String,
    private val prefillHour: Int? = null,
    private val prefillMinute: Int? = null,
    private val prefillDays: Set<DayOfWeek>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AlarmEditorUiState(alarmId = alarmId, isEdit = alarmId > 0, label = defaultLabel)
    )
    val uiState: StateFlow<AlarmEditorUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved

    init {
        viewModelScope.launch {
            val defaultSnooze = preferences.defaultSnoozeMinutes.first()
            if (alarmId > 0) {
                repository.getAlarm(alarmId)?.let { alarm ->
                    _uiState.value = AlarmEditorUiState(
                        alarmId = alarm.id,
                        isEdit = true,
                        hour = alarm.hour,
                        minute = alarm.minute,
                        label = alarm.label,
                        repeatDays = alarm.repeatDays,
                        oneTimeDate = alarm.oneTimeDate,
                        vibrate = alarm.vibrate,
                        snoozeMinutes = alarm.snoozeMinutes
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        snoozeMinutes = defaultSnooze,
                        hour = prefillHour ?: it.hour,
                        minute = prefillMinute ?: it.minute,
                        repeatDays = prefillDays ?: it.repeatDays
                    )
                }
            }
        }
    }

    fun setHour(hour: Int) = _uiState.update { it.copy(hour = hour) }
    fun setMinute(minute: Int) = _uiState.update { it.copy(minute = minute) }
    fun setLabel(label: String) = _uiState.update { it.copy(label = label) }

    fun toggleDay(day: DayOfWeek) = _uiState.update { state ->
        val days = state.repeatDays.toMutableSet()
        if (!days.add(day)) days.remove(day)
        // Selecting any repeat day makes this a repeating alarm, which can never
        // carry a one-time date.
        state.copy(repeatDays = days, oneTimeDate = if (days.isEmpty()) state.oneTimeDate else null)
    }

    /** Pins an explicit calendar date for a one-time alarm (repeatDays must be empty). */
    fun setOneTimeDate(date: LocalDate?) = _uiState.update { state ->
        check(state.repeatDays.isEmpty()) { "A repeating alarm cannot have a one-time date" }
        state.copy(oneTimeDate = date)
    }

    fun setVibrate(vibrate: Boolean) = _uiState.update { it.copy(vibrate = vibrate) }
    fun setSnooze(minutes: Int) = _uiState.update { it.copy(snoozeMinutes = minutes) }

    /**
     * Copies a structured AI preview into the editor fields ONLY.
     * Never schedules. This runs when the user taps APPLY TO ALARM on the AI
     * Preview screen; the alarm is only ever scheduled by the editor's SAVE.
     */
    fun applyAiPreview(preview: AiPreviewResult) {
        _uiState.update {
            it.copy(
                hour = preview.hour,
                minute = preview.minute,
                label = preview.label.ifBlank { it.label },
                repeatDays = preview.repeatDays,
                // Only a one-time alarm may carry a pinned date; a repeating
                // interpretation clears it to keep the invariant.
                oneTimeDate = if (preview.repeatDays.isEmpty()) preview.date else null
            )
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val now = Instant.now()
            if (state.isEdit) {
                val existing = repository.getAlarm(state.alarmId)
                val updated = (existing ?: newAlarm(now)).copy(
                    id = state.alarmId,
                    hour = state.hour,
                    minute = state.minute,
                    label = state.label.ifBlank { defaultLabel },
                    repeatDays = state.repeatDays,
                    oneTimeDate = if (state.repeatDays.isEmpty()) state.oneTimeDate else null,
                    vibrate = state.vibrate,
                    snoozeMinutes = state.snoozeMinutes,
                    updatedAt = now
                )
                scheduler.cancel(state.alarmId)
                repository.update(updated)
                if (updated.enabled) scheduler.schedule(updated)
            } else {
                val saved = newAlarm(now).let { alarm ->
                    alarm.copy(id = repository.insert(alarm))
                }
                scheduler.schedule(saved)
            }
            preferences.setDefaultSnooze(state.snoozeMinutes)
            _saved.emit(Unit)
        }
    }

    fun delete() {
        viewModelScope.launch {
            if (alarmId > 0) {
                scheduler.cancel(alarmId)
                repository.delete(alarmId)
            }
            _saved.emit(Unit)
        }
    }

    private fun newAlarm(now: Instant) = Alarm(
        id = 0L,
        hour = _uiState.value.hour,
        minute = _uiState.value.minute,
        label = _uiState.value.label.ifBlank { defaultLabel },
        enabled = true,
        repeatDays = _uiState.value.repeatDays,
        vibrate = _uiState.value.vibrate,
        soundUri = null,
        snoozeMinutes = _uiState.value.snoozeMinutes,
        oneTimeDate = if (_uiState.value.repeatDays.isEmpty()) _uiState.value.oneTimeDate else null,
        createdAt = now,
        updatedAt = now
    )

    companion object {
        fun factory(
            container: AppContainer,
            alarmId: Long,
            prefillHour: Int? = null,
            prefillMinute: Int? = null,
            prefillDays: Set<DayOfWeek>? = null
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlarmEditorViewModel(
                        repository = container.alarmRepository,
                        scheduler = container.alarmScheduler,
                        preferences = container.appPreferences,
                        alarmId = alarmId,
                        defaultLabel = container.appContext.getString(
                            com.kemalcetin.aialarm.R.string.alarm_default_label
                        ),
                        prefillHour = prefillHour,
                        prefillMinute = prefillMinute,
                        prefillDays = prefillDays
                    )
                }
            }
    }
}
