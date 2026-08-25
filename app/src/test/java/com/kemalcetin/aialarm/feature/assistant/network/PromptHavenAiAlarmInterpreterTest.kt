package com.kemalcetin.aialarm.feature.assistant.network

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `request body uses the selected app language from the locale provider`() {
        // Simulates the app wiring: the user-selected application language (here
        // Turkish) must be sent to the backend, not the device locale.
        val localized = PromptHavenAiAlarmInterpreter(
            baseUrl = "http://x",
            localeProvider = { "tr-TR" }
        )
        val body = localized.buildRequestBody(text = "yaz saati kur")
        assertTrue(body.contains("\"locale\":\"tr-TR\""))
        assertFalse(body.contains("\"locale\":\"en\""))
    }

    @Test
    fun `locale provider defaults to the device locale`() {
        val defaultInterpreter = PromptHavenAiAlarmInterpreter(baseUrl = "http://x")
        val body = defaultInterpreter.buildRequestBody(text = "wake me")
        assertTrue(body.contains("\"locale\":\""))
    }

    // ---- App Check header / token integration ----

    @Test
    fun `request attaches app check header when token present`() {
        val req = interpreter.buildRequest("http://x/interpretAlarmRequest", "{}", "appcheck-tok-123")
        assertEquals("appcheck-tok-123", req.header(PromptHavenAiAlarmInterpreter.APP_CHECK_HEADER))
    }

    @Test
    fun `request omits app check header when token unavailable`() {
        val req = interpreter.buildRequest("http://x/interpretAlarmRequest", "{}", null)
        assertNull(req.header(PromptHavenAiAlarmInterpreter.APP_CHECK_HEADER))
    }

    @Test
    fun `request never carries a provider authorization header`() {
        // Guards the security invariant: no AI provider secret on the device/APK.
        val req = interpreter.buildRequest("http://x/interpretAlarmRequest", "{}", "appcheck-tok")
        assertNull(req.header("Authorization"))
        assertNull(req.header("authorization"))
    }

    @Test
    fun `blank base url fails gracefully as not configured`() = runBlocking {
        val unconfigured = PromptHavenAiAlarmInterpreter(baseUrl = "")
        val result = unconfigured.interpret("wake me at 7")
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `token acquisition throwing degrades to a failed interpretation`() = runBlocking {
        val throwingProvider = AppCheckTokenProvider { throw IllegalStateException("no firebase") }
        val interpreterWithBadToken = PromptHavenAiAlarmInterpreter(
            baseUrl = "http://example.test",
            tokenProvider = throwingProvider
        )
        // getToken() throws inside the try, which is caught -> Failed (no network hit).
        val result = interpreterWithBadToken.interpret("wake me at 7")
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `firebase app check provider returns null when firebase unavailable`() = runBlocking {
        val provider = FirebaseAppCheckTokenProvider(
            appCheckProvider = { throw IllegalStateException("no default FirebaseApp") }
        )
        assertNull(provider.getToken())
    }
}
