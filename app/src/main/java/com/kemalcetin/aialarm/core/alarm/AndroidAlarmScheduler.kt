package com.kemalcetin.aialarm.core.alarm

import android.app.AlarmManager
import android.content.Context
import com.kemalcetin.aialarm.core.permission.ExactAlarmPermissionManager
import com.kemalcetin.aialarm.domain.model.Alarm

class AndroidAlarmScheduler(
    private val context: Context,
    private val nextAlarmCalculator: NextAlarmCalculator,
    private val exactAlarmPermissionManager: ExactAlarmPermissionManager
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alarm: Alarm): AlarmScheduleResult {
        if (!alarm.enabled) return AlarmScheduleResult.Success

        val trigger = nextAlarmCalculator.nextTrigger(alarm)
            ?: return AlarmScheduleResult.Error("No future occurrence for alarm ${alarm.id}")

        if (!exactAlarmPermissionManager.canScheduleExactAlarms()) {
            return AlarmScheduleResult.ExactAlarmPermissionRequired
        }

        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    trigger.toInstant().toEpochMilli(),
                    AlarmIntentFactory.fullScreenPendingIntent(context, alarm.id)
                ),
                AlarmIntentFactory.startAlarmPendingIntent(context, alarm.id)
            )
            AlarmScheduleResult.Success
        } catch (e: SecurityException) {
            AlarmScheduleResult.ExactAlarmPermissionRequired
        } catch (e: Exception) {
            AlarmScheduleResult.Error(e.message ?: "Failed to schedule alarm ${alarm.id}")
        }
    }

    override fun cancel(alarmId: Long) {
        alarmManager.cancel(AlarmIntentFactory.startAlarmPendingIntent(context, alarmId))
        alarmManager.cancel(AlarmIntentFactory.snoozeStartPendingIntent(context, alarmId))
    }
}
