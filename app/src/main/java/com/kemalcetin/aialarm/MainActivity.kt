package com.kemalcetin.aialarm

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kemalcetin.aialarm.navigation.AppNavigation
import com.kemalcetin.aialarm.ui.theme.AiAlarmTheme
import com.kemalcetin.aialarm.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the user-selected app language (English default). Re-running
        // attachBaseContext on recreate rewraps the context with the new locale,
        // so stringResource and Locale.getDefault() both follow the selection.
        super.attachBaseContext((newBase.applicationContext as AiAlarmApplication).appLanguageManager.applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AiAlarmApplication).container
        setContent {
            val themeMode by container.appPreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AiAlarmTheme(darkTheme = darkTheme) {
                AppNavigation(container)
            }
        }
    }
}
