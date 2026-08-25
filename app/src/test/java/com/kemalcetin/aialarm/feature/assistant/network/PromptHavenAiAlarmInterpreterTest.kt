package com.kemalcetin.aialarm.feature.assistant.network

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the strict validation applied to provider/AI JSON before any value
 * reaches the editor. Raw AI output is never trusted.
 */
class PromptHavenAiAlarmInterpreterTest {

    private val interpreter = PromptHavenAiAlarmInterpreter()

    @Test
    fun `parses valid success payload`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":"2026-08-26","repeatDays":[],"label":"Wake Up"},"needsClarification":false,"clarificationQuestion":null}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals(LocalDate.of(2026, 8, 26), alarm.date)
        assertEquals(setOf<DayOfWeek>(), alarm.repeatDays)
        assertEquals("Wake Up", alarm.label)
    }

    @Test
    fun `parses repeating payload with null date`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":null,"repeatDays":["MONDAY","FRIDAY"],"label":""}}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertNull(alarm.date)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alarm.repeatDays)
    }

    @Test
    fun `defaults blank label to Alarm`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":null,"repeatDays":[],"label":"   "}}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertEquals("Alarm", alarm.label)
    }

    @Test
    fun `rejects out of range hour`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"24:00","date":null,"repeatDays":[],"label":""}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects malformed time`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"not-a-time","date":null,"repeatDays":[],"label":""}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects invalid iso date`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":"26/08/2026","repeatDays":[],"label":""}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects impossible date with repeat days`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":"2026-08-26","repeatDays":["MONDAY"],"label":""}}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `drops unknown day names instead of trusting them`() {
        val result = interpreter.parse(
            """{"status":"success","interpretation":{"time":"07:30","date":null,"repeatDays":["MONDAY","FUNDAY","FRIDAY"],"label":""}}"""
        )
        val alarm = result as AlarmInterpretResult.Alarm
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), alarm.repeatDays)
    }

    @Test
    fun `returns clarification for ambiguous requests`() {
        val result = interpreter.parse(
            """{"status":"clarification_required","interpretation":null,"needsClarification":true,"clarificationQuestion":"Which day?"}"""
        )
        val clarification = result as AlarmInterpretResult.NeedsClarification
        assertTrue(clarification.message.contains("day"))
    }

    @Test
    fun `rejects clarification without a question`() {
        val result = interpreter.parse(
            """{"status":"clarification_required","interpretation":null,"needsClarification":true,"clarificationQuestion":"   "}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects missing interpretation payload`() {
        val result = interpreter.parse("""{"status":"success","interpretation":null}""")
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects unknown status`() {
        val result = interpreter.parse("""{"status":"ok","alarm":{"hour":7}}""")
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `rejects malformed json`() {
        assertTrue(interpreter.parse("not json at all") is AlarmInterpretResult.Failed)
    }

    @Test
    fun `request body carries text timezone locale and currentDateTime`() {
        val body = interpreter.buildRequestBody(
            text = "wake me tomorrow at 7",
            timezone = "Europe/Istanbul",
            locale = "tr-TR",
            currentDateTime = "2026-08-25T06:00:00+03:00"
        )
        assertTrue(body.contains("\"text\":\"wake me tomorrow at 7\""))
        assertTrue(body.contains("\"timezone\":\"Europe/Istanbul\""))
        assertTrue(body.contains("\"locale\":\"tr-TR\""))
        assertTrue(body.contains("\"currentDateTime\":\"2026-08-25T06:00:00+03:00\""))
        assertFalse(body.contains("prompt"))
    }
}
