package com.kemalcetin.aialarm.feature.assistant.analytics

import com.kemalcetin.aialarm.feature.assistant.model.DismissIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class DismissIntentAnalyzerTest {

    @Test
    fun `null latency is unknown`() {
        assertEquals(DismissIntent.UNKNOWN, DismissIntentAnalyzer.classify(null, 0))
    }

    @Test
    fun `very fast dismiss without snooze is accidental`() {
        assertEquals(
            DismissIntent.ACCIDENTAL,
            DismissIntentAnalyzer.classify(1_000L, 0)
        )
    }

    @Test
    fun `dismiss just under threshold is accidental`() {
        assertEquals(
            DismissIntent.ACCIDENTAL,
            DismissIntentAnalyzer.classify(DismissIntentAnalyzer.ACCIDENTAL_LATENCY_MS - 1, 0)
        )
    }

    @Test
    fun `slow dismiss is willing`() {
        assertEquals(
            DismissIntent.WILLING,
            DismissIntentAnalyzer.classify(60_000L, 0)
        )
    }

    @Test
    fun `two or more snoozes is willing even on fast dismiss`() {
        assertEquals(DismissIntent.WILLING, DismissIntentAnalyzer.classify(1_000L, 2))
    }

    @Test
    fun `mid range latency is unknown`() {
        assertEquals(
            DismissIntent.UNKNOWN,
            DismissIntentAnalyzer.classify(15_000L, 0)
        )
    }
}
