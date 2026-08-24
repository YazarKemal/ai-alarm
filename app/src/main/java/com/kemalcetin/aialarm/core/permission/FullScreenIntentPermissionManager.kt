package com.kemalcetin.aialarm.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * On Android 14+ the user can revoke USE_FULL_SCREEN_INTENT per-app. This
 * reports whether a full-screen intent would actually be honored and can open
 * the relevant settings page.
 */
class FullScreenIntentPermissionManager(private val context: Context) {

    fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            NotificationManagerCompat.from(context).canUseFullScreenIntent()
        } else {
            true
        }

    fun openSettings(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            null
        }
}
