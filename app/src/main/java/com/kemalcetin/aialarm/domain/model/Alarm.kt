package com.kemalcetin.aialarm.domain.model

import java.time.DayOfWeek
import java.time.Instant

/**
 * Immutable domain model for a single alarm.
 *
 * [repeatDays] empty means a one-time alarm.
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
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val isOneTime: Boolean get() = repeatDays.isEmpty()

    init {
        require(hour in 0..23) { "hour must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
        require(snoozeMinutes > 0) { "snoozeMinutes must be > 0" }
    }
}
