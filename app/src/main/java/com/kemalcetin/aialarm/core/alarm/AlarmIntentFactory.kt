package com.kemalcetin.aialarm.core.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.feature.assistant.AiScheduleReceiver

/**
 * Centralizes alarm action constants and PendingIntent construction.
 *
 * PendingIntent identity must be stable and unique per alarm + action, so two
 * alarms never overwrite each other's scheduled PendingIntent (failure mode D).
 */
object AlarmIntentFactory {

    const val ACTION_START_ALARM = "com.kemalcetin.aialarm.action.START_ALARM"
    const val ACTION_DISMISS_ALARM = "com.kemalcetin.aialarm.action.DISMISS_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.kemalcetin.aialarm.action.SNOOZE_ALARM"
    const val ACTION_STOP_RINGING = "com.kemalcetin.aialarm.action.STOP_RINGING"
    const val ACTION_AI_SCHEDULE_CHECK = "com.kemalcetin.aialarm.action.AI_SCHEDULE_CHECK"

    const val EXTRA_ALARM_ID = "com.kemalcetin.aialarm.extra.ALARM_ID"
    const val EXTRA_ALARM_LABEL = "com.kemalcetin.aialarm.extra.ALARM_LABEL"
    const val EXTRA_ALARM_VIBRATE = "com.kemalcetin.aialarm.extra.ALARM_VIBRATE"
    const val EXTRA_IS_SNOOZE = "com.kemalcetin.aialarm.extra.IS_SNOOZE"

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // Kind discriminator keeps request codes unique per action for the same alarm.
    private const val KIND_START = 0
    private const val KIND_DISMISS = 1
    private const val KIND_SNOOZE = 2
    private const val KIND_SNOOZE_START = 3
    private const val KIND_FULLSCREEN = 4
    private const val KIND_AI = 5

    private fun requestCode(alarmId: Long, kind: Int): Int {
        val base = (alarmId % 100_000_000L).toInt()
        return base * 5 + kind
    }

    fun startAlarmIntent(context: Context, alarmId: Long): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_START_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
        }

    fun startAlarmPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, KIND_START),
            startAlarmIntent(context, alarmId),
            FLAGS
        )

    /** One-shot PendingIntent used when a snooze re-trigger fires. */
    fun snoozeStartPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, KIND_SNOOZE_START),
            startAlarmIntent(context, alarmId).apply { putExtra(EXTRA_IS_SNOOZE, true) },
            FLAGS
        )

    fun dismissPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, KIND_DISMISS),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
                putExtra(EXTRA_ALARM_ID, alarmId)
            },
            FLAGS
        )

    fun snoozePendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, KIND_SNOOZE),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
                putExtra(EXTRA_ALARM_ID, alarmId)
            },
            FLAGS
        )

    fun ringingActivityIntent(context: Context, alarmId: Long): Intent =
        Intent(context, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }

    fun fullScreenPendingIntent(context: Context, alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(alarmId, KIND_FULLSCREEN),
            ringingActivityIntent(context, alarmId),
            FLAGS
        )

    fun ringingServiceIntent(context: Context, alarm: Alarm): Intent =
        Intent(context, AlarmRingingService::class.java).apply {
            action = ACTION_START_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_VIBRATE, alarm.vibrate)
        }

    fun stopRingingIntent(context: Context): Intent =
        Intent(context, AlarmRingingService::class.java).apply {
            action = ACTION_STOP_RINGING
        }

    fun aiCheckPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(0L, KIND_AI),
            Intent(context, AiScheduleReceiver::class.java).apply {
                action = ACTION_AI_SCHEDULE_CHECK
            },
            FLAGS
        )
}
