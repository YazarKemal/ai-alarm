package com.kemalcetin.aialarm.core.locale

import android.content.Context
import android.content.res.Configuration
import com.kemalcetin.aialarm.data.prefs.AppPreferences
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Owns the user-selected application language and applies it per-app.
 *
 * The selected tag is persisted in [AppPreferences] (default English). At
 * attach time the current language is loaded once (blocking read) and cached in
 * memory; [applyTo] wraps a base context with a configuration whose locale is
 * the selected language. This is how Compose `stringResource` and the AI request
 * pick up the user's choice.
 *
 * On change, [setLanguage] updates the in-memory value and the process default
 * `Locale` immediately (so day names / request locale follow), persists the tag
 * in the background, and the caller recreates the single activity so its
 * baseContext is rewrapped. There is no recreation loop: recreation happens only
 * on an explicit user action.
 */
class AppLanguageManager(context: Context) {

    // Use the context as given — never assume `context.applicationContext` is
    // non-null. The Application owns the single instance and constructs it only
    // after attach, so the context passed here is always valid.
    private val prefs = AppPreferences(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = AtomicReference<AppLanguage?>(null)

    /** The currently selected language, loading and caching it on first call. */
    fun current(): AppLanguage {
        current.get()?.let { return it }
        val loaded = runBlocking { prefs.selectedLanguageTag.first() }
            .let { AppLanguage.resolve(it) }
        current.set(loaded)
        Locale.setDefault(loaded.locale)
        return loaded
    }

    /** Wraps [base] with a configuration using the selected language. */
    fun applyTo(base: Context): Context {
        val lang = current()
        val config = Configuration(base.resources.configuration)
        config.setLocale(lang.locale)
        config.setLayoutDirection(lang.locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Changes the application language. In-memory value and default Locale update
     * synchronously so an immediate activity recreation reflects the new language;
     * the tag is persisted in the background.
     */
    fun setLanguage(lang: AppLanguage) {
        current.set(lang)
        Locale.setDefault(lang.locale)
        scope.launch { prefs.setSelectedLanguage(lang.tag) }
    }
}
