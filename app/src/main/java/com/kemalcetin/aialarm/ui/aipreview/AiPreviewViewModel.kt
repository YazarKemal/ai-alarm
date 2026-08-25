package com.kemalcetin.aialarm.ui.aipreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.feature.assistant.network.AiAlarmInterpreter
import com.kemalcetin.aialarm.feature.assistant.network.AlarmInterpretResult
import com.kemalcetin.aialarm.ui.alarmeditor.NaturalLanguageParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A single structured preview of how a natural-language description was
 * interpreted. This is shown BEFORE the editor is touched; tapping "APPLY TO
 * ALARM" copies it into the editor, and only the editor's SAVE schedules.
 *
 * This is deliberately not a chatbot — there is no multi-turn conversation.
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
    private val text: String
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val result: AlarmInterpretResult? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        runInterpretation()
    }

    fun retry() = runInterpretation()

    private fun runInterpretation() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, result = null, error = null) }
            when (val result = interpreter.interpret(text)) {
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
                                    label = "Alarm"
                                )
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(loading = false, error = "Couldn't understand that request. Try a time like 07:30 and a day.")
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Nav back-stack key carrying the applied preview back to the editor. */
        const val AI_PREVIEW_RESULT_KEY = "ai_preview_result"

        fun factory(container: AppContainer, text: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AiPreviewViewModel(
                        interpreter = container.aiAlarmInterpreter,
                        text = text
                    )
                }
            }
    }
}
