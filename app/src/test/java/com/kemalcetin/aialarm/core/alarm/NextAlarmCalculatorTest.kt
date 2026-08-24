package com.kemalcetin.aialarm.core.alarm

import com.kemalcetin.aialarm.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NextAlarmCalculatorTest {

    private val calculator = NextAlarmCalculator()
    private val zone = ZoneId.of("America/New_York")

    private fun alarm(
        hour: Int,
        minute: Int,
        days: Set<DayOfWeek>,
        enabled: Boolean = true
    ) = Alarm(
        id = 1L,
        hour = hour,
        minute = minute,
        label = "Test",
        enabled = enabled,
        repeatDays = days,
        vibrate = false,
        soundUri = null,
        snoozeMinutes = 5,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun zdt(
        year: Int, month: Int, day: Int, hour: Int, minute: Int
    ) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    @Test
    fun `same day future time`() {
        val from = zdt(2024, 1, 15, 8, 0) // Monday
        val result = calculator.nextTrigger(alarm(9, 0, setOf(DayOfWeek.MONDAY)), from)
        assertEquals(zdt(2024, 1, 15, 9, 0), result)
    }

    @Test
    fun `one-time alarm already passed rolls to tomorrow`() {
        val from = zdt(2024, 1, 15, 8, 0) // Monday
        val result = calculator.nextTrigger(alarm(7, 0, emptySet()), from)
        assertEquals(zdt(2024, 1, 16, 7, 0), result)
    }

    @Test
    fun `repeating alarm picks next matching weekday`() {
        val from = zdt(2024, 1, 15, 10, 0) // Monday, past 09:00
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val result = calculator.nextTrigger(alarm(9, 0, days), from)
        assertEquals(zdt(2024, 1, 17, 9, 0), result) // Wednesday
    }

    @Test
    fun `repeating alarm crossing into next week`() {
        val from = zdt(2024, 1, 20, 8, 0) // Saturday
        val result = calculator.nextTrigger(alarm(9, 0, setOf(DayOfWeek.FRIDAY)), from)
        assertEquals(zdt(2024, 1, 26, 9, 0), result) // next Friday
    }

    @Test
    fun `one-time alarm future today`() {
        val from = zdt(2024, 1, 15, 8, 0)
        val result = calculator.nextTrigger(alarm(8, 30, emptySet()), from)
        assertEquals(zdt(2024, 1, 15, 8, 30), result)
    }

    @Test
    fun `boundary near midnight`() {
        val late = zdt(2024, 1, 15, 23, 59)
        assertEquals(
            zdt(2024, 1, 16, 0, 0),
            calculator.nextTrigger(alarm(0, 0, emptySet()), late)
        )

        val almostMidnight = zdt(2024, 1, 15, 23, 58)
        assertEquals(
            zdt(2024, 1, 15, 23, 59),
            calculator.nextTrigger(alarm(23, 59, emptySet()), almostMidnight)
        )
    }

    @Test
    fun `daily alarm preserves local time across DST transition`() {
        // Spring forward in New York: 2024-03-10 at 02:00 (EST -> EDT).
        val from = zdt(2024, 3, 9, 8, 0)
        val allDays = DayOfWeek.entries.toSet()
        val result = calculator.nextTrigger(alarm(7, 0, allDays), from)

        val expected = zdt(2024, 3, 10, 7, 0)
        assertEquals(expected, result)
        assertEquals(ZoneOffset.ofHours(-4), result!!.offset) // EDT, not EST
    }

    @Test
    fun `disabled alarm returns null`() {
        val from = zdt(2024, 1, 15, 8, 0)
        assertNull(calculator.nextTrigger(alarm(9, 0, emptySet(), enabled = false), from))
    }
}
