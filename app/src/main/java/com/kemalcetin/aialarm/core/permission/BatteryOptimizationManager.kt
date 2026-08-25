package com.kemalcetin.aialarm.core.permission

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Reports battery-optimization state for the alarm's exact-timing reliability.
 * A device-level RESTRICTED/optimized app can have its alarms delayed, so we
 * surface whether the user should allow the app to run without restrictions.
 */
class BatteryOptimizationManager(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun isIgnoringBatteryOptimizations(): Boolean =
        powerManager.isIgnoringBatteryOptimizations(context.packageName)

    fun openSettings(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            null
        }
}
