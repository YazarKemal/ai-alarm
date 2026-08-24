package com.kemalcetin.aialarm.core.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kemalcetin.aialarm.AiAlarmApplication
import com.kemalcetin.aialarm.ui.ringing.AlarmRingingScreen
import com.kemalcetin.aialarm.ui.theme.AiAlarmTheme

/**
 * Full-screen activity shown while an alarm is ringing. Works over the lock
 * screen and with the screen off, as permitted by the platform.
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(AlarmIntentFactory.EXTRA_ALARM_ID, -1L)
        val container = (application as AiAlarmApplication).container

        enableEdgeToEdge()
        setContent {
            AiAlarmTheme {
                AlarmRingingScreen(
                    container = container,
                    alarmId = alarmId,
                    onSnooze = { finish() },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
