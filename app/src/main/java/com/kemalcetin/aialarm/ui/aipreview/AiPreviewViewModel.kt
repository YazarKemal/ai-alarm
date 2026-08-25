package com.kemalcetin.aialarm.ui.aipreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.core.alarm.AlarmScheduleResult
import com.kemalcetin.aialarm.core.alarm.AlarmScheduler
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.feature.assistant.network.AiAlarmInterpreter
import com.kemalcetin.aialarm.feature.assistant.network.AlarmInterpretResult
import com.kemalcetin.aialarm.ui.alarmeditor.NaturalLanguageParser
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A single structured preview of how a natural-language request was interpreted.
 * Handles all three result kinds:
 *  - QUICK_ALARM: show the time, then APPLY TO ALARM copies it into the editor.
 *  - GOAL_PLAN: show the computed wake plan + assumptions, allow toggling each
 *    alarm, and CREATE PLAN schedules the enabled alarms directly (the explicit
 *    confirmation). No separate SAVE step.
 *  - CLARIFICATION_REQUIRED: show the question + a text input; the answer is
 *    resubmitted with the original request (short in-memory conversation only).
 */
data class AiPreviewResult(
    val hour: Int,
    val minute: Int,
    val date: java.time.LocalDate?,
    val repeatDays: Set<java.time.DayOfWeek>,
    val label: String
) : java.io.Serializable

class AiPreviewViewModel(
    private val interpreter: AiAlarmInterpreter,
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val preferences: AppPreferences,
    private val text: String,
    private val defaultLabel: String
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val result: AlarmInterpretResult? = null,
        val error: String? = null,
        val clarificationInput: String = "",
        val conversation: List<String> = emptyList(),
        val planAlarmEnabled: Set<Int> = emptySet(),
        val creating: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _planCreated = MutableSharedFlow<Unit>()
    val planCreated: SharedFlow<Unit> = _planCreated.asSharedFlow()

    init {
        runInterpretation()
    }

    fun retry() = runInterpretation()

    fun onClarificationInputChange(value: String) =
        _uiState.update { it.copy(clarificationInput = value) }

    /** Resubmits the original request plus the clarification answer. */
    fun submitClarification() {
        val answer = _uiState.value.clarificationInput.trim()
        if (answer.isBlank()) return
        _uiState.update { it.copy(conversation = it.conversation + answer, clarificationInput = "") }
        runInterpretation()
    }

    fun togglePlanAlarm(index: Int) = _uiState.update { state ->
        val enabled = state.planAlarmEnabled.toMutableSet()
        if (!enabled.add(index)) enabled.remove(index)
        state.copy(planAlarmEnabled = enabled)
    }

    /**
     * CREATE PLAN — the user's explicit confirmation. Creates all enabled plan
     * alarms transactionally via the existing Room repository + AlarmManager.
     * Never runs in the background without this confirmation.
     */
    fun createPlan() {
        val plan = (_uiState.value.result as? AlarmInterpretResult.GoalPlan) ?: return
        val enabledAlarms = plan.alarms.filterIndexed { i, _ -> i in _uiState.value.planAlarmEnabled }
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true) }
            var partialFailure = false
            val snooze = preferences.defaultSnoozeMinutes.first()
            for (pa in enabledAlarms) {
                val parsed = parseHm(pa.time) ?: continue
                val now = Instant.now()
                val alarm = Alarm(
                    id = 0L,
                    hour = parsed.first,
                    minute = parsed.second,
                    label = pa.label.ifBlank { defaultLabel },
                    enabled = true,
                    repeatDays = emptySet(),
                    vibrate = true,
                    soundUri = null,
                    snoozeMinutes = snooze,
                    oneTimeDate = pa.date,
                    createdAt = now,
                    updatedAt = now
                )
                val id = repository.insert(alarm)
                val result = scheduler.schedule(alarm.copy(id = id))
                if (result is AlarmScheduleResult.Error ||
                    result is AlarmScheduleResult.ExactAlarmPermissionRequired
                ) {
                    partialFailure = true
                }
            }
            _uiState.update { it.copy(creating = false) }
            _planCreated.emit(Unit)
        }
    }

    private fun runInterpretation() {
        viewModelScope.launch {
            val conversation = _uiState.value.conversation
            _uiState.update { it.copy(loading = true, result = null, error = null) }
            when (val result = interpreter.interpret(text, conversation)) {
                is AlarmInterpretResult.GoalPlan -> {
                    val enabled = result.alarms.mapIndexedNotNull { i, a -> if (a.enabled) i else null }.toSet()
                    _uiState.update { it.copy(loading = false, result = result, planAlarmEnabled = enabled) }
                }
                is AlarmInterpretResult.Alarm,
                is AlarmInterpretResult.NeedsClarification -> {
                    _uiState.update { it.copy(loading = false, result = result) }
                }
                is AlarmInterpretResult.Failed -> {
                    // Backend unreachable/unconfigured: fall back to the offline
                    // parser so the flow "must work without network/AI".
                    val parsed = NaturalLanguageParser.parse(text)
                    if (parsed != null) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                result = AlarmInterpretResult.Alarm(
                                    hour = parsed.hour,
                                    minute = parsed.minute,
                                    date = null,
                                    repeatDays = parsed.repeatDays,
                                    label = defaultLabel
                                )
                            )
                        }
                    } else {
                        _uiState.update { it.copy(loading = false, error = OFFLINE_PARSE_ERROR) }
                    }
                }
            }
        }
    }

    private fun parseHm(time: String): Pair<Int, Int>? {
        val parts = time.split(":").map { it.toIntOrNull() }
        val h = parts.getOrNull(0)
        val m = parts.getOrNull(1)
        return if (h != null && m != null && h in 0..23 && m in 0..59) h to m else null
    }

    companion object {
        const val AI_PREVIEW_RESULT_KEY = "ai_preview_result"
        private const val OFFLINE_PARSE_ERROR = "offline_parse_error"

        fun factory(container: AppContainer, text: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AiPreviewViewModel(
                        interpreter = container.aiAlarmInterpreter,
                        repository = container.alarmRepository,
                        scheduler = container.alarmScheduler,
                        preferences = container.appPreferences,
                        text = text,
                        defaultLabel = container.appContext.getString(
                            com.kemalcetin.aialarm.R.string.alarm_default_label
                        )
                    )
                }
            }
    }
}
