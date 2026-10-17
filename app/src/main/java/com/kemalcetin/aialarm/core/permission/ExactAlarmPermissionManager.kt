package com.kemalcetin.aialarm.core.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Exact-alarm capability on Android.
 *
 * AI Alarm is a genuine alarm-clock app, so on Android 13+ (API 33+) it holds the
 * Play-recommended, auto-granted, non-revocable `USE_EXACT_ALARM` permission. On
 * Android 12 / 12L (API 31-32) `USE_EXACT_ALARM` does not exist, so `SCHEDULE_EXACT_ALARM`
 * (scoped in the manifest via maxSdkVersion=32) is the only mechanism and is user-revocable
 * there. Scheduling itself always uses `AlarmManager.setAlarmClock()`, which is exempt from
 * the exact-alarm permission, so the settings flow below only matters on API 31-32.
 */
class ExactAlarmPermissionManager(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** True when exact alarms can be scheduled on this device/version. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Intent to let the user grant exact-alarm access, or null when no such flow is needed.
     *
     * On API 33+ `USE_EXACT_ALARM` is auto-granted and non-revocable, so there is nothing to
     * request. The `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` settings screen is only relevant on
     * API 31-32, where `SCHEDULE_EXACT_ALARM` is user-revocable.
     */
    fun openSettings(): Intent? =
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
}
