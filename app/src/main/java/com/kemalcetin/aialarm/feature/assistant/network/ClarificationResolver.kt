package com.kemalcetin.aialarm.feature.assistant.network

import com.kemalcetin.aialarm.R

/**
 * Client-side localization + anti-loop policy for known clarification codes.
 *
 * Known codes (commute_required, commute_minutes_required) are displayed from the
 * app's own string resources in the currently selected application language. The
 * backend `clarificationQuestion` is used ONLY as a fallback for unknown codes.
 *
 * Anti-loop: once the generic commute question has been asked and answered but
 * the value is still not usable, the next turn escalates to the explicit
 * minutes prompt (`commute_minutes_required`) so the same generic question is
 * never re-asked indefinitely.
 *
 * [resolveString] is injected (the app context in production, a stub in unit
 * tests) so the decision logic stays pure and testable.
 */
object ClarificationResolver {

    /** Localized display string for a known code; backend message otherwise. */
    fun questionFor(code: String?, message: String, resolveString: (Int) -> String): String = when (code) {
        "commute_required" -> resolveString(R.string.ai_clarification_commute_required)
        "commute_minutes_required" -> resolveString(R.string.ai_clarification_commute_minutes_required)
        else -> message
    }

    /**
     * Resolves the effective code to submit for the next turn. If the generic
     * commute question was already asked once (present in [history]) and the
     * backend re-asks it, escalate to the explicit minutes prompt.
     */
    fun nextCode(backendCode: String?, history: List<ClarificationTurn>): String? {
        if (backendCode == "commute_required" && history.any { it.code == "commute_required" }) {
            return "commute_minutes_required"
        }
        return backendCode
    }

    /**
     * Builds the localized clarification to display for this turn, applying the
     * anti-loop escalation.
     */
    fun resolve(
        backendCode: String?,
        backendMessage: String,
        history: List<ClarificationTurn>,
        resolveString: (Int) -> String
    ): ClarificationTurn {
        val code = nextCode(backendCode, history)
        val question = questionFor(code, backendMessage, resolveString)
        return ClarificationTurn(code = code, question = question, answer = "")
    }
}
