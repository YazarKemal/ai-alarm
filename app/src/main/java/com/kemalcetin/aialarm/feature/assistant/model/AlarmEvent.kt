package com.kemalcetin.aialarm.feature.assistant.model

import java.time.Instant

enum class AlarmEventType {
    FIRED, SNOOZED, DISMISSED, AUTO_CREATED
}

/** Whether a dismiss was deliberate (waking up) or a reflex (still asleep). */
enum class DismissIntent {
    WILLING, ACCIDENTAL, UNKNOWN
}

/**
 * A single observed alarm interaction, persisted so the assistant can learn the
 * user's schedule and dismiss behavior over time.
 */
data class AlarmEvent(
    val alarmId: Long,
    val type: AlarmEventType,
    val occurredAt: Instant,
    val dismissLatencyMs: Long? = null,
    val snoozeCount: Int = 0,
    val dismissIntent: DismissIntent? = null
)
