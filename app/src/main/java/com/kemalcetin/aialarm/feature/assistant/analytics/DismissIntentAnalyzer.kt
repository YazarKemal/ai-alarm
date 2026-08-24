package com.kemalcetin.aialarm.feature.assistant.analytics

import com.kemalcetin.aialarm.feature.assistant.model.DismissIntent

/**
 * Classifies whether a dismiss was deliberate (waking up) or accidental (a reflex
 * tap while still asleep), based on dismiss latency and prior snoozing.
 *
 * Pure JVM so it is independently unit-testable.
 */
object DismissIntentAnalyzer {

    /** Dismisses faster than this are treated as a reflex, not a wake-up. */
    const val ACCIDENTAL_LATENCY_MS = 5_000L

    /** Dismisses slower than this are treated as a deliberate wake-up. */
    const val WILLING_LATENCY_MS = 30_000L

    fun classify(latencyMs: Long?, snoozeCount: Int): DismissIntent {
        val latency = latencyMs ?: return DismissIntent.UNKNOWN

        // After two or more snoozes the user is consciously managing their waking.
        if (snoozeCount >= 2) return DismissIntent.WILLING

        return when {
            latency < ACCIDENTAL_LATENCY_MS -> DismissIntent.ACCIDENTAL
            latency > WILLING_LATENCY_MS -> DismissIntent.WILLING
            else -> DismissIntent.UNKNOWN
        }
    }
}
