package com.kemalcetin.aialarm.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kemalcetin.aialarm.R

class AlarmNotificationManager(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_ringing_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_ringing_channel_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildRingingNotification(alarmId: Long, label: String): Notification {
        val title = label.ifBlank { context.getString(R.string.alarm_default_label) }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(AlarmIntentFactory.fullScreenPendingIntent(context, alarmId))
            .setFullScreenIntent(AlarmIntentFactory.fullScreenPendingIntent(context, alarmId), true)
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.snooze),
                AlarmIntentFactory.snoozePendingIntent(context, alarmId)
            )
            .addAction(
                R.drawable.ic_dismiss,
                context.getString(R.string.dismiss),
                AlarmIntentFactory.dismissPendingIntent(context, alarmId)
            )
            .build()
    }

    fun notifyRinging(alarmId: Long, label: String) {
        createChannel()
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, buildRingingNotification(alarmId, label))
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "alarm_ringing"
        const val NOTIFICATION_ID = 1001
    }
}
