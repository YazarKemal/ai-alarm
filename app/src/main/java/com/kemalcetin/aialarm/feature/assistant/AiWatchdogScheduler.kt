package com.kemalcetin.aialarm.feature.assistant

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import com.kemalcetin.aialarm.core.alarm.AlarmIntentFactory

/**
 * Schedules a silent, self-re-arming check that lets the assistant catch a
 * forgotten alarm even while the app is closed. Uses [AlarmManager.set] (no
 * audio, no exact timing) to stay battery-friendly.
 */
class AiWatchdogScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext() {
        try {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + CHECK_INTERVAL_MS,
                AlarmIntentFactory.aiCheckPendingIntent(context)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to arm assistant watchdog", e)
        }
    }

    fun cancel() {
        alarmManager.cancel(AlarmIntentFactory.aiCheckPendingIntent(context))
    }

    companion object {
        const val CHECK_INTERVAL_MS = 4L * 60 * 60 * 1000
        private const val TAG = "AiWatchdogScheduler"
    }
}
