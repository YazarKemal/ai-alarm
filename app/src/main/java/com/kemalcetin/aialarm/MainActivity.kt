package com.kemalcetin.aialarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kemalcetin.aialarm.navigation.AppNavigation
import com.kemalcetin.aialarm.ui.theme.AiAlarmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AiAlarmApplication).container
        setContent {
            AiAlarmTheme {
                AppNavigation(container)
            }
        }
    }
}
