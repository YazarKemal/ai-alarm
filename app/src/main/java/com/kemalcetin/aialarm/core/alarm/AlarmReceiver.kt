package com.kemalcetin.aialarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kemalcetin.aialarm.AiAlarmApplication

/**
 * Lightweight entry point for AlarmManager triggers and notification actions.
 *
 * Database and service work is delegated to [AlarmController] on an application
 * scope; [goAsync] keeps the broadcast alive until that work completes.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AiAlarmApplication
        val controller = app.container.alarmController
        val pendingResult = goAsync()
        val alarmId = intent.getLongExtra(AlarmIntentFactory.EXTRA_ALARM_ID, -1L)

        if (alarmId <= 0L) {
            pendingResult.finish()
            return
        }

        when (intent.action) {
            AlarmIntentFactory.ACTION_START_ALARM -> {
                val isSnooze = intent.getBooleanExtra(AlarmIntentFactory.EXTRA_IS_SNOOZE, false)
                controller.handleAlarmTriggered(alarmId, isSnooze) { pendingResult.finish() }
            }
            AlarmIntentFactory.ACTION_DISMISS_ALARM ->
                controller.dismiss(alarmId) { pendingResult.finish() }
            AlarmIntentFactory.ACTION_SNOOZE_ALARM ->
                controller.snooze(alarmId) { pendingResult.finish() }
            else -> pendingResult.finish()
        }
    }
}
