package com.kemalcetin.aialarm.feature.assistant.learning

import com.kemalcetin.aialarm.domain.model.Alarm
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEvent
import com.kemalcetin.aialarm.feature.assistant.model.AlarmEventType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleLearnerTest {

    private val learner = ScheduleLearner()

    private fun fired(day: DayOfWeek, hour: Int, minute: Int) = AlarmEvent(
        alarmId = 1L,
        type = AlarmEventType.FIRED,
        occurredAt = ZonedDateTime.of(
            2026, 1, 1, hour, minute, 0, 0, ZoneId.systemDefault()
        ).with(day).toInstant()
    )

    @Test
    fun `learns slot from events on distinct days`() {
        val events = listOf(fired(DayOfWeek.MONDAY, 7, 0), fired(DayOfWeek.TUESDAY, 7, 0))
        val slots = learner.learnSlots(events)
        assertEquals(1, slots.size)
        assertEquals(ExpectedSlot(7, 0, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)), slots[0])
    }

    @Test
    fun `single day event does not form a slot`() {
        val events = listOf(fired(DayOfWeek.MONDAY, 7, 0))
        assertEquals(emptyList<ExpectedSlot>(), learner.learnSlots(events))
    }

    @Test
    fun `five or more days expands to all days`() {
        val days = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        val events = days.map { fired(it, 7, 0) }
        val slot = learner.learnSlots(events).single()
        assertEquals(DayOfWeek.entries.toSet(), slot.repeatDays)
    }

    @Test
    fun `returns missing slot when no enabled alarm covers it`() {
        // Sunday 10:00; the Monday 07:00 slot falls within the 24h horizon.
        val now = ZonedDateTime.of(2026, 8, 23, 10, 0, 0, 0, ZoneId.systemDefault())
        val slot = ExpectedSlot(7, 0, setOf(DayOfWeek.MONDAY))
        val trigger = learner.nextExpectedTrigger(listOf(slot), emptyList(), now)
        assertNotNull(trigger)
    }

    @Test
    fun `returns null when an enabled alarm already covers the slot`() {
        val now = ZonedDateTime.of(2026, 8, 23, 10, 0, 0, 0, ZoneId.systemDefault())
        val slot = ExpectedSlot(7, 0, setOf(DayOfWeek.MONDAY))
        val nowTime = Instant.now()
        val covering = Alarm(
            id = 1L, hour = 7, minute = 0, label = "cover", enabled = true,
            repeatDays = setOf(DayOfWeek.MONDAY), vibrate = true, soundUri = null,
            snoozeMinutes = 5, createdAt = nowTime, updatedAt = nowTime
        )
        assertNull(learner.nextExpectedTrigger(listOf(slot), listOf(covering), now))
    }
}
