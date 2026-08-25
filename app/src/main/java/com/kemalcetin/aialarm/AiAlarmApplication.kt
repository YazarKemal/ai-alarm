package com.kemalcetin.aialarm

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.kemalcetin.aialarm.core.locale.AppLanguageManager
import com.kemalcetin.aialarm.di.AppContainer

class AiAlarmApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Per-app language: persisted, English by default. */
    val appLanguageManager: AppLanguageManager by lazy {
        AppLanguageManager(this)
    }

    override fun attachBaseContext(base: Context) {
        // Wrap the base context with the selected app language so the application
        // context (used for notification channel names, resources, etc.) resolves
        // in the user's chosen language.
        super.attachBaseContext(appLanguageManager.applyTo(base))
    }

    override fun onCreate() {
        super.onCreate()

        // Firebase App Check (Play Integrity). FirebaseApp.initializeApp returns
        // null when no default options exist (i.e. google-services.json is not
        // present for this build), so local development without Firebase config
        // keeps running — App Check is simply skipped and AI degrades to the
        // offline workflow. We never fabricate a token or ship a provider secret.
        val firebaseApp = runCatching { FirebaseApp.initializeApp(this) }.getOrNull()
        if (firebaseApp != null) {
            runCatching {
                FirebaseAppCheck.getInstance()
                    .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
        }

        container = AppContainer(this)
        container.notificationManager.createChannel()
        container.aiWatchdogScheduler.scheduleNext()
    }
}
