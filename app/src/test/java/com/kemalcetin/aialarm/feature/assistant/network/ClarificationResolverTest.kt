package com.kemalcetin.aialarm.feature.assistant.network

import com.kemalcetin.aialarm.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the client-side localization + anti-loop policy for clarification codes.
 * Known codes must resolve through the app's string resources in the selected
 * application language; the backend message is only a fallback for unknown codes.
 */
class ClarificationResolverTest {

    // Stub string table for the currently selected application language.
    private val strings: (Int) -> String = { id ->
        when (id) {
            R.string.ai_clarification_commute_required -> "Oraya ulaşman genellikle kaç dakika sürüyor?"
            R.string.ai_clarification_commute_minutes_required -> "Yolculuk süresini dakika olarak yazar mısın? Örneğin: 60 dakika."
            else -> "?"
        }
    }

    @Test
    fun `commute_required resolves through local string resources`() {
        val turn = ClarificationResolver.resolve("commute_required", "backend english", emptyList(), strings)
        assertEquals("Oraya ulaşman genellikle kaç dakika sürüyor?", turn.question)
        assertEquals("commute_required", turn.code)
    }

    @Test
    fun `commute_minutes_required resolves through the explicit local string`() {
        val turn = ClarificationResolver.resolve("commute_minutes_required", "backend", emptyList(), strings)
        assertEquals("Yolculuk süresini dakika olarak yazar mısın? Örneğin: 60 dakika.", turn.question)
        assertEquals("commute_minutes_required", turn.code)
    }

    @Test
    fun `unknown code falls back to the backend message`() {
        val turn = ClarificationResolver.resolve(null, "Which day?", emptyList(), strings)
        assertEquals("Which day?", turn.question)
        assertNull(turn.code)
    }

    @Test
    fun `anti-loop escalates a repeated commute_required to the explicit minutes prompt`() {
        // First ask shown and answered as commute_required; backend still asks
        // commute_required again -> escalate to commute_minutes_required.
        val history = listOf(ClarificationTurn("commute_required", "q", "1 saat kadar"))
        val turn = ClarificationResolver.resolve("commute_required", "q", history, strings)
        assertEquals("commute_minutes_required", turn.code)
        assertEquals("Yolculuk süresini dakika olarak yazar mısın? Örneğin: 60 dakika.", turn.question)
    }

    @Test
    fun `no escalation on the first commute_required ask`() {
        val turn = ClarificationResolver.resolve("commute_required", "q", emptyList(), strings)
        assertEquals("commute_required", turn.code)
    }

    @Test
    fun `commute_minutes_required stays explicit even when repeated`() {
        val history = listOf(ClarificationTurn("commute_minutes_required", "q", "hmm"))
        val turn = ClarificationResolver.resolve("commute_minutes_required", "q", history, strings)
        assertEquals("commute_minutes_required", turn.code)
    }
}
