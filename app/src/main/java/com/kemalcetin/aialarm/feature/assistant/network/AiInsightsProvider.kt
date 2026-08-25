package com.kemalcetin.aialarm.feature.assistant.network

import java.time.DayOfWeek

/** A suggested alarm the assistant believes the user forgot. */
data class ScheduleInsight(
    val suggestedHour: Int?,
    val suggestedMinute: Int?,
    val suggestedDays: Set<DayOfWeek>?,
    val rationale: String?
)

/**
 * Optional enhancement to the on-device
 * [com.kemalcetin.aialarm.feature.assistant.learning.ScheduleLearner].
 *
 * SECURITY: This app MUST NOT hold an AI provider key or call a provider API
 * directly. The only place provider secrets ever exist is the server-side
 * Firebase proxy (`functions/`). In this build the provider is deliberately
 * left unconfigured (`AppContainer.aiInsightsProvider` is null) so the
 * assistant runs purely on local heuristics; if a server-side insight endpoint
 * is added later, implement it through the same proxy pattern — never inline a
 * provider key here.
 */
interface AiInsightsProvider {
    suspend fun suggestForgottenAlarm(behaviorSummary: String): ScheduleInsight?
}
