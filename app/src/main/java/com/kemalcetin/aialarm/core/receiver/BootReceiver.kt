package com.kemalcetin.aialarm.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kemalcetin.aialarm.AiAlarmApplication
import kotlinx.coroutines.launch

/**
 * Reschedules enabled alarms after the device boots. Does not start audio.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
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
                Log.e(TAG, "Failed to reschedule alarms after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
