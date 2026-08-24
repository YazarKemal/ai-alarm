package com.kemalcetin.aialarm

import android.app.Application
import com.kemalcetin.aialarm.di.AppContainer

class AiAlarmApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationManager.createChannel()
        container.aiWatchdogScheduler.scheduleNext()
    }
}
