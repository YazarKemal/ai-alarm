package com.kemalcetin.aialarm.core.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Handles the SCHEDULE_EXACT_ALARM special app access on Android 12+.
 *
 * Before a Play Store release we may evaluate USE_EXACT_ALARM instead, because
 * AI Alarm is a genuine alarm-clock application. That decision is intentionally
 * isolated here so it is trivial to change later.
 */
class ExactAlarmPermissionManager(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun openSettings(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
}
