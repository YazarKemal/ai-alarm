package com.kemalcetin.aialarm.core.alarm

import android.app.AlarmManager
import android.content.Context

/**
 * Debug-only helper that schedules an alarm at an exact offset (e.g. 30 seconds)
 * to exercise the real AlarmManager -> receiver -> service pipeline quickly.
 */
class DebugAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleInSeconds(alarmId: Long, seconds: Long) {
        val triggerAt = System.currentTimeMillis() + seconds * 1000L
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                triggerAt,
                AlarmIntentFactory.fullScreenPendingIntent(context, alarmId)
            ),
            AlarmIntentFactory.startAlarmPendingIntent(context, alarmId)
        )
    }
}
