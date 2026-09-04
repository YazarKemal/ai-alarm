package com.kemalcetin.aialarm.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kemalcetin.aialarm.R
import com.kemalcetin.aialarm.core.locale.AppLanguageManager

class AlarmNotificationManager(
    private val context: Context,
    private val appLanguageManager: AppLanguageManager
) {

    /** A context wrapped with the user-selected app language for localized strings. */
    private fun localized(): Context = appLanguageManager.applyTo(context)

    fun createChannel() {
        val res = localized()
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.alarm_ringing_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = res.getString(R.string.alarm_ringing_channel_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun buildRingingNotification(alarmId: Long, label: String): Notification {
        val res = localized()
        val title = label.ifBlank { res.getString(R.string.alarm_default_label) }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(res.getString(R.string.alarm_ringing))
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
        // POST_NOTIFICATIONS is a runtime permission on API 33+. If it has not
        // been granted (or can't be checked), posting would throw; the full-screen
        // ringing activity still shows the alarm either way, so degrade gracefully.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildRingingNotification(alarmId, label))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post — nothing to show.
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "alarm_ringing"
        const val NOTIFICATION_ID = 1001
    }
}
