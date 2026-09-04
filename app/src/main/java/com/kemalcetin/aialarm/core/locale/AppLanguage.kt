package com.kemalcetin.aialarm.core.locale

import java.util.Locale

/**
 * The in-app languages supported in v1.
 *
 * [tag] is the BCP-47 tag used for DataStore persistence and
 * `Locale.forLanguageTag`. Android resource folders use the equivalent
 * qualifiers (e.g. `values-pt-rBR` for tag `pt-BR`).
 * [nativeName] is how the language displays itself (never translated — it is
 * already in that language). [aiTag] is the locale identifier sent to the
 * PromptHaven backend so the AI can respond in the user's chosen language.
 *
 * English is the application default; the app never follows the device locale
 * on first launch.
 */
enum class AppLanguage(
    val tag: String,
    val nativeName: String,
    val aiTag: String
) {
    ENGLISH("en", "English", "en-US"),
    TURKISH("tr", "Türkçe", "tr-TR"),
    SPANISH("es", "Español", "es"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)", "pt-BR"),
    GERMAN("de", "Deutsch", "de"),
    FRENCH("fr", "Français", "fr"),
    ITALIAN("it", "Italiano", "it"),
    INDONESIAN("id", "Bahasa Indonesia", "id"),
    HINDI("hi", "हिन्दी", "hi"),
    JAPANESE("ja", "日本語", "ja"),
    KOREAN("ko", "한국어", "ko"),
    ARABIC("ar", "العربية", "ar");

    val locale: Locale
        get() = Locale.forLanguageTag(tag)

    companion object {
        const val DEFAULT_TAG: String = "en"

        val default: AppLanguage
            get() = ENGLISH

        /** Resolve by BCP-47 tag (case-insensitive). Returns null if unknown. */
        fun fromTag(tag: String?): AppLanguage? =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

        /** Resolve a persisted tag, falling back to English for null/invalid. */
        fun resolve(persistedTag: String?): AppLanguage =
            fromTag(persistedTag) ?: default
    }
}
