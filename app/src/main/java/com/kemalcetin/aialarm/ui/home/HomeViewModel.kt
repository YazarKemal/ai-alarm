package com.kemalcetin.aialarm.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kemalcetin.aialarm.core.alarm.AlarmScheduler
import com.kemalcetin.aialarm.core.alarm.DebugAlarmScheduler
import com.kemalcetin.aialarm.core.permission.ExactAlarmPermissionManager
import com.kemalcetin.aialarm.core.permission.FullScreenIntentPermissionManager
import com.kemalcetin.aialarm.core.permission.NotificationPermissionManager
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import com.kemalcetin.aialarm.di.AppContainer
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.domain.repository.AlarmRepository
import com.kemalcetin.aialarm.feature.assistant.data.AlarmEventRepository
import com.kemalcetin.aialarm.feature.assistant.learning.ExpectedSlot
import com.kemalcetin.aialarm.feature.assistant.learning.ScheduleLearner
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.ui.common.formatExpectedSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class PermissionStatus(
    val canScheduleExact: Boolean = true,
    val notificationsGranted: Boolean = true,
    val fullScreenAvailable: Boolean = true
)

data class HomeUiState(
    val alarms: List<Alarm> = emptyList(),
    val permissions: PermissionStatus = PermissionStatus(),
    val aiAutoCreateEnabled: Boolean = true,
    val aiLastAutoCreateTime: Long = 0L,
    val aiSlots: List<ExpectedSlot> = emptyList(),
    val aiNextExpected: String? = null,
    val aiRecentEvents: List<AlarmEvent> = emptyList(),
    val aiDeepSeekConfigured: Boolean = false
)

class HomeViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val debugAlarmScheduler: DebugAlarmScheduler,
    private val exactPermission: ExactAlarmPermissionManager,
    private val notificationPermission: NotificationPermissionManager,
    private val fullScreenPermission: FullScreenIntentPermissionManager,
    private val preferences: AppPreferences,
    private val alarmEventRepository: AlarmEventRepository,
    private val deepSeekConfigured: Boolean
) : ViewModel() {

    private val permissions = MutableStateFlow(computePermissions())
    private val learner = ScheduleLearner()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAlarms(),
        permissions,
        preferences.autoCreateEnabled,
        preferences.aiLastAutoCreateTime,
        alarmEventRepository.observeRecent(50)
    ) { alarms, perms, autoCreate, lastTime, events ->
        val slots = learner.learnSlots(events)
        val next = learner.nextExpectedTrigger(slots, alarms, ZonedDateTime.now())
        HomeUiState(
            alarms = alarms,
            permissions = perms,
            aiAutoCreateEnabled = autoCreate,
            aiLastAutoCreateTime = lastTime,
            aiSlots = slots,
            aiNextExpected = next?.let { formatExpectedSlot(it) },
            aiRecentEvents = events.take(5),
            aiDeepSeekConfigured = deepSeekConfigured
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPermissions() {
        permissions.value = computePermissions()
    }

    private fun computePermissions() = PermissionStatus(
        canScheduleExact = exactPermission.canScheduleExactAlarms(),
        notificationsGranted = notificationPermission.isGranted(),
        fullScreenAvailable = fullScreenPermission.canUseFullScreenIntent()
    )

    fun setAiAutoCreateEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoCreateEnabled(enabled) }
    }

    fun formatAiLastAutoCreate(epochMillis: Long): String =
        if (epochMillis <= 0L) {
            "—"
        } else {
            Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .toString()
        }

    fun setAlarmEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(alarm.id, enabled)
            if (enabled) {
                scheduler.schedule(alarm.copy(enabled = true))
            } else {
                scheduler.cancel(alarm.id)
            }
            refreshPermissions()
        }
    }

    fun rescheduleAllEnabled() {
        viewModelScope.launch {
            repository.getEnabledAlarms().forEach { scheduler.schedule(it) }
            refreshPermissions()
        }
    }

    fun scheduleTestAlarm() {
        viewModelScope.launch {
            val now = Instant.now()
            val alarm = Alarm(
                id = 0L,
                hour = LocalTime.now().hour,
                minute = LocalTime.now().minute,
                label = "Test alarm (30s)",
                enabled = true,
                repeatDays = emptySet(),
                vibrate = true,
                soundUri = null,
                snoozeMinutes = 5,
                createdAt = now,
                updatedAt = now
            )
            val id = repository.insert(alarm)
            debugAlarmScheduler.scheduleInSeconds(id, 30)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = container.alarmRepository,
                    scheduler = container.alarmScheduler,
                    debugAlarmScheduler = container.debugAlarmScheduler,
                    exactPermission = container.exactAlarmPermissionManager,
                    notificationPermission = container.notificationPermissionManager,
                    fullScreenPermission = container.fullScreenIntentPermissionManager,
                    preferences = container.appPreferences,
                    alarmEventRepository = container.alarmEventRepository,
                    deepSeekConfigured = container.aiInsightsProvider != null
                )
            }
        }
    }
}
