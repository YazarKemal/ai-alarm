package com.kemalcetin.aialarm.feature.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kemalcetin.aialarm.AiAlarmApplication
import com.kemalcetin.aialarm.core.alarm.AlarmIntentFactory

/**
 * Wakes on the watchdog tick, runs the auto-schedule check, and re-arms the next
 * check. Silent — never plays audio.
 */
class AiScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntentFactory.ACTION_AI_SCHEDULE_CHECK) return
        val container = (context.applicationContext as AiAlarmApplication).container
        val pendingResult = goAsync()
        val job = container.aiAlarmEngine.runAutoScheduleCheck()
        job.invokeOnCompletion { pendingResult.finish() }
        container.aiWatchdogScheduler.scheduleNext()
    }
}
