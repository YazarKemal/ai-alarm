package com.kemalcetin.aialarm.core.alarm

import com.kemalcetin.aialarm.domain.model.Alarm
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes the next trigger time for an alarm in a given time zone.
 *
 * Pure JVM logic: the current time is injected so results are deterministic
 * and independently unit-testable without Android framework dependencies.
 */
class NextAlarmCalculator(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    /** Next trigger relative to "now". */
    fun nextTrigger(alarm: Alarm): ZonedDateTime? =
        nextTrigger(alarm, ZonedDateTime.now(clock.withZone(zone)))

    fun nextTrigger(alarm: Alarm, from: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null

        val candidate = from
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)

        return if (alarm.repeatDays.isEmpty()) {
            if (candidate.isAfter(from)) candidate else candidate.plusDays(1)
        } else {
            var occurrence = candidate
            var steps = 0
            while (occurrence.dayOfWeek !in alarm.repeatDays || !occurrence.isAfter(from)) {
                occurrence = occurrence.plusDays(1)
                steps++
                if (steps > 8) return null
            }
            occurrence
        }
    }
}
