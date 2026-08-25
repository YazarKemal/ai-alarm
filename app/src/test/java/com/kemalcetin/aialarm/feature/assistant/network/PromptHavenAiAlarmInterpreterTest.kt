package com.kemalcetin.aialarm.feature.assistant.network

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the strict validation applied to provider/AI JSON before any value
 * reaches the editor. Raw AI output is never trusted.
 */
class PromptHavenAiAlarmInterpreterTest {

    private val interpreter = PromptHavenAiAlarmInterpreter()

    @Test
    fun `parses valid alarm payload`() {
        val result = interpreter.parse(
            """{"status":"ok","alarm":{"hour":7,"minute":0,"repeatDays":["MONDAY","FRIDAY"]}}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertEquals(7, alarm.hour)
        assertEquals(0, alarm.minute)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alarm.repeatDays)
    }

    @Test
    fun `rejects out of range hour`() {
        val result = interpreter.parse(
            """{"status":"ok","alarm":{"hour":24,"minute":0,"repeatDays":[]}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects out of range minute`() {
        val result = interpreter.parse(
            """{"status":"ok","alarm":{"hour":7,"minute":60,"repeatDays":[]}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `drops unknown day names instead of trusting them`() {
        val result = interpreter.parse(
            """{"status":"ok","alarm":{"hour":7,"minute":0,"repeatDays":["MONDAY","FUNDAY","FRIDAY"]}}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alarm.repeatDays)
    }

    @Test
    fun `returns clarification for ambiguous requests`() {
        val result = interpreter.parse(
            """{"status":"clarification","clarification":"Which day?"}"""
        )
        val clarification = result as AlarmInterpretResult.NeedsClarification
        assertTrue(clarification.message.contains("day"))
    }

    @Test
    fun `rejects missing alarm payload`() {
        val result = interpreter.parse("""{"status":"ok"}""")
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects malformed json`() {
        assertTrue(interpreter.parse("not json at all") is AlarmInterpretResult.Failed)
    }
}
