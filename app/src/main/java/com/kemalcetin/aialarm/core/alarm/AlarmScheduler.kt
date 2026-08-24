package com.kemalcetin.aialarm.core.alarm

import com.kemalcetin.aialarm.domain.model.Alarm

/** Result of attempting to schedule an alarm with [AlarmScheduler]. */
sealed interface AlarmScheduleResult {
    data object Success : AlarmScheduleResult
    data object ExactAlarmPermissionRequired : AlarmScheduleResult
    data class Error(val reason: String) : AlarmScheduleResult
}

interface AlarmScheduler {
    fun schedule(alarm: Alarm): AlarmScheduleResult
    fun cancel(alarmId: Long)
}
