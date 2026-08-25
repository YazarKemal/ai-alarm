package com.kemalcetin.aialarm

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.kemalcetin.aialarm.core.locale.AppLanguageManager
import com.kemalcetin.aialarm.di.AppContainer

class AiAlarmApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Per-app language: persisted, English by default.
     *
     * There is exactly ONE instance, owned here and constructed in [onCreate]
     * once a valid attached context exists. We deliberately do NOT wrap the
     * application base context in attachBaseContext: at that point the Application
     * is not yet attached, so constructing a manager (which touches DataStore and
     * configuration) would crash. The locale is instead applied to each Activity's
     * base context (see MainActivity.attachBaseContext) and to app-level strings
     * (e.g. notification channel names) on demand via [AppLanguageManager.applyTo].
     */
    lateinit var appLanguageManager: AppLanguageManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Construct the single language manager only now that a valid context
        // exists (onCreate runs after the Application is attached).
        appLanguageManager = AppLanguageManager(this)

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

        container = AppContainer(this, appLanguageManager)
        container.notificationManager.createChannel()
        container.aiWatchdogScheduler.scheduleNext()
    }
}
