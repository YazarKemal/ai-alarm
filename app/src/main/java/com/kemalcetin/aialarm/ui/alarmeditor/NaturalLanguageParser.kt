package com.kemalcetin.aialarm.ui.alarmeditor

import java.time.DayOfWeek

/**
 * Minimal offline parser for natural-language alarm descriptions.
 *
 * This is the "must work without network/AI" fallback used by the editor's
 * "Set with PromptHaven AI" row. It understands a 24h time and, optionally,
 * weekday words (English + Turkish). Phase 5 replaces the call site with the
 * server-side PromptHaven AI proxy for richer inputs; this stays as the
 * offline safe path. It only ever produces editor prefill values — it never
 * schedules anything.
 */
object NaturalLanguageParser {

    private val dayWords: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
        "pazartesi" to DayOfWeek.MONDAY, "salı" to DayOfWeek.TUESDAY,
        "çarşamba" to DayOfWeek.WEDNESDAY, "perşembe" to DayOfWeek.THURSDAY,
        "cuma" to DayOfWeek.FRIDAY, "cumartesi" to DayOfWeek.SATURDAY,
        "pazar" to DayOfWeek.SUNDAY
    )

    data class Parsed(
        val hour: Int,
        val minute: Int,
        val repeatDays: Set<DayOfWeek>
    )

    fun parse(text: String): Parsed? {
        val t = text.trim()
        val timeMatch = Regex("""(?:^|\D)([01]?\d|2[0-3])[:.\s](\d{2})(?:\D|$)""").find(t)
        val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull()
        val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull()
        if (hour == null || minute == null || minute !in 0..59) return null

        val lower = t.lowercase()
        val days = dayWords.entries
            .filter { (word, _) -> Regex("\\b$word\\b").containsMatchIn(lower) }
            .map { it.value }
            .toSet()

        return Parsed(hour = hour, minute = minute, repeatDays = days)
    }
}
