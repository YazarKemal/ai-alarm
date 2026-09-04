package com.kemalcetin.aialarm.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Immutable domain model for a single alarm.
 *
 * Alarm type is derived from [repeatDays]:
 * - [repeatDays] non-empty => a repeating alarm; [oneTimeDate] must be null.
 * - [repeatDays] empty => a one-time alarm; [oneTimeDate] optionally pins the
 *   exact calendar date it fires on. When null, the alarm fires on its next
 *   occurrence (legacy behavior: tomorrow if today's time already passed).
 *
 * [soundUri] null means "use the system default alarm sound".
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val vibrate: Boolean,
    val soundUri: String?,
    val snoozeMinutes: Int,
    val oneTimeDate: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /** A one-time alarm fires exactly once and never repeats. */
    val isOneTime: Boolean get() = repeatDays.isEmpty()

    init {
        require(hour in 0..23) { "hour must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
        require(snoozeMinutes > 0) { "snoozeMinutes must be > 0" }
        require(repeatDays.isEmpty() || oneTimeDate == null) {
            "a repeating alarm cannot carry a one-time date"
        }
    }
}
