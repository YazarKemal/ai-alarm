package com.kemalcetin.aialarm.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kemalcetin.aialarm.AiAlarmApplication
import kotlinx.coroutines.launch

/**
 * Reschedules enabled alarms when the system clock or timezone changes so the
 * next trigger is recalculated for the correct local time.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED
        ) {
            return
        }

        val container = (context.applicationContext as AiAlarmApplication).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                container.alarmRepository.getEnabledAlarms().forEach { alarm ->
                    container.alarmScheduler.schedule(alarm)
                }
                container.aiWatchdogScheduler.scheduleNext()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms after time change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
    }
}
