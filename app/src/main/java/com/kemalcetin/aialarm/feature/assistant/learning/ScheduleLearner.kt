package com.kemalcetin.aialarm.feature.assistant.learning

import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEventType
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** A recurring wake time learned from observed alarm behavior. */
data class ExpectedSlot(
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<DayOfWeek>
)

/**
 * Learns the user's recurring alarm times from recorded firing/dismiss events, so
 * the assistant can detect when an expected alarm is missing. Pure JVM.
 */
class ScheduleLearner(
    private val minimumOccurrences: Int = 2
) {

    /** Groups firing events into slots that recur on [minimumOccurrences] or more days. */
    fun learnSlots(events: List<AlarmEvent>): List<ExpectedSlot> {
        val firing = events.filter {
            it.type == AlarmEventType.FIRED || it.type == AlarmEventType.DISMISSED
        }
        return firing
            .map { it.occurredAt.atZone(ZoneId.systemDefault()) }
            .groupBy { it.hour to it.minute }
            .mapNotNull { (key, times) ->
                val days = times.map { it.dayOfWeek }.distinct()
                if (days.size < minimumOccurrences) return@mapNotNull null
                val (hour, minute) = key
                val repeatDays = if (days.size >= 5) DayOfWeek.entries.toSet() else days.toSet()
                ExpectedSlot(hour, minute, repeatDays)
            }
    }

    /**
     * Returns the next expected slot within [horizonHours] that has no matching
     * enabled alarm, i.e. an alarm the user likely forgot.
     */
    fun nextExpectedTrigger(
        slots: List<ExpectedSlot>,
        enabledAlarms: List<Alarm>,
        from: ZonedDateTime,
        horizonHours: Long = 24
    ): ExpectedSlot? {
        val horizon = from.plusHours(horizonHours)
        return slots
            .map { it to nextOccurrence(it, from) }
            .filter { (slot, trigger) ->
                trigger != null &&
                    !trigger.isAfter(horizon) &&
                    !isCovered(enabledAlarms, slot, trigger.dayOfWeek)
            }
            .minByOrNull { (_, trigger) -> trigger!! }
            ?.first
    }

    private fun nextOccurrence(slot: ExpectedSlot, from: ZonedDateTime): ZonedDateTime? {
        val candidate = from
            .withHour(slot.hour)
            .withMinute(slot.minute)
            .withSecond(0)
            .withNano(0)

        if (slot.repeatDays.isEmpty()) {
            return if (candidate.isAfter(from)) candidate else candidate.plusDays(1)
        }

        var occurrence = candidate
        var steps = 0
        while (occurrence.dayOfWeek !in slot.repeatDays || !occurrence.isAfter(from)) {
            occurrence = occurrence.plusDays(1)
            steps++
            if (steps > 8) return null
        }
        return occurrence
    }

    private fun isCovered(enabledAlarms: List<Alarm>, slot: ExpectedSlot, weekday: DayOfWeek): Boolean =
        enabledAlarms.any { alarm ->
            alarm.enabled &&
                alarm.hour == slot.hour &&
                alarm.minute == slot.minute &&
                (alarm.repeatDays.isEmpty() || weekday in alarm.repeatDays)
        }
}
