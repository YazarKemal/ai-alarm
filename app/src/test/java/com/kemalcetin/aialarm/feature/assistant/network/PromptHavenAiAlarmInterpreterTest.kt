package com.kemalcetin.aialarm.feature.assistant.network

import com.kemalcetin.aialarm.core.planning.AiPlanningPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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

    // ---- Goal plan (V2) ----

    @Test
    fun `parses a goal plan with alarms and assumptions`() {
        val result = interpreter.parse(
            """{"status":"success","kind":"goal_plan","plan":{
                "destinationLabel":"work","targetTime":"13:00",
                "sleepStartTime":"03:35","wakeTime":"11:35","leaveByTime":"12:05",
                "sleepShortfallMinutes":25,
                "assumptions":{"sleepMinutes":480,"preparationMinutes":30,"commuteMinutes":40,"bufferMinutes":15},
                "alarms":[
                  {"time":"11:25","date":"2026-08-26","label":"Gentle wake","role":"gentle","enabled":true},
                  {"time":"11:35","date":"2026-08-26","label":"Wake","role":"main","enabled":true},
                  {"time":"11:45","date":"2026-08-26","label":"Backup","role":"backup","enabled":false}
                ]
            }}"""
        )
        val plan = result as AlarmInterpretResult.GoalPlan
        assertEquals("work", plan.destinationLabel)
        assertEquals("13:00", plan.targetTime)
        assertEquals("11:35", plan.wakeTime)
        assertEquals("12:05", plan.leaveByTime)
        assertEquals(25, plan.sleepShortfallMinutes)
        assertEquals(480, plan.assumptions.sleepMinutes)
        assertEquals(40, plan.assumptions.commuteMinutes)
        assertEquals(15, plan.assumptions.bufferMinutes)
        assertEquals(3, plan.alarms.size)
        assertEquals("11:25", plan.alarms[0].time)
        assertEquals(LocalDate.of(2026, 8, 26), plan.alarms[0].date)
        assertEquals("gentle", plan.alarms[0].role)
        assertTrue(plan.alarms[0].enabled)
        assertFalse(plan.alarms[2].enabled)
    }

    @Test
    fun `goal plan allows commute to be null and disabled to default true`() {
        val result = interpreter.parse(
            """{"status":"success","kind":"goal_plan","plan":{
                "destinationLabel":"","targetTime":"13:00",
                "sleepStartTime":"03:35","wakeTime":"11:35","leaveByTime":"12:05",
                "sleepShortfallMinutes":0,
                "assumptions":{"sleepMinutes":480,"preparationMinutes":30,"commuteMinutes":-1,"bufferMinutes":15},
                "alarms":[
                  {"time":"11:35","date":"2026-08-26","label":"Wake","role":"main","enabled":true}
                ]
            }}"""
        )
        val plan = result as AlarmInterpretResult.GoalPlan
        assertNull(plan.assumptions.commuteMinutes)
        assertEquals(1, plan.alarms.size)
        assertTrue(plan.alarms[0].enabled)
    }

    @Test
    fun `goal plan rejects missing assumptions`() {
        val result = interpreter.parse(
            """{"status":"success","kind":"goal_plan","plan":{
                "destinationLabel":"x","targetTime":"13:00",
                "sleepStartTime":"03:35","wakeTime":"11:35","leaveByTime":"12:05",
                "assumptions":{},
                "alarms":[{"time":"11:35","date":"2026-08-26","label":"W","role":"main","enabled":true}]
            }}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `goal plan rejects more than three alarms`() {
        val result = interpreter.parse(
            """{"status":"success","kind":"goal_plan","plan":{
                "destinationLabel":"x","targetTime":"13:00",
                "sleepStartTime":"03:35","wakeTime":"11:35","leaveByTime":"12:05",
                "assumptions":{"sleepMinutes":480,"preparationMinutes":30,"bufferMinutes":15},
                "alarms":[
                  {"time":"11:25","date":"2026-08-26","label":"1","role":"gentle","enabled":true},
                  {"time":"11:30","date":"2026-08-26","label":"2","role":"main","enabled":true},
                  {"time":"11:35","date":"2026-08-26","label":"3","role":"main","enabled":true},
                  {"time":"11:40","date":"2026-08-26","label":"4","role":"backup","enabled":true}
                ]
            }}"""
        )
        assertTrue(result is AlarmInterpretResult.Failed)
    }

    @Test
    fun `clarification carries a machine code`() {
        val result = interpreter.parse(
            """{"status":"clarification_required","clarificationQuestion":"How long is your commute?","clarificationCode":"commute_required"}"""
        )
        val clarification = result as AlarmInterpretResult.NeedsClarification
        assertEquals("commute_required", clarification.code)
    }

    @Test
    fun `request body carries planning preferences and structured clarifications`() {
        val prefs = AiPlanningPreferences(
            targetSleepMinutes = 420,
            preparationMinutes = 20,
            commuteMinutes = 35,
            bufferMinutes = 10
        )
        val body = interpreter.buildRequestBody(
            text = "arrive by 9",
            preferences = prefs,
            clarifications = listOf(
                ClarificationTurn("commute_required", "How long does it take?", "40 minutes")
            )
        )
        assertTrue(body.contains("\"targetSleepMinutes\":420"))
        assertTrue(body.contains("\"preparationMinutes\":20"))
        assertTrue(body.contains("\"commuteMinutes\":35"))
        assertTrue(body.contains("\"bufferMinutes\":10"))
        assertTrue(body.contains("\"clarifications\""))
        assertTrue(body.contains("\"code\":\"commute_required\""))
        assertTrue(body.contains("\"question\":\"How long does it take?\""))
        assertTrue(body.contains("\"answer\":\"40 minutes\""))
        // The obsolete string-list conversation format is gone.
        assertFalse(body.contains("\"conversation\""))
        assertFalse(body.contains("\"role\":\"user\""))
    }

    // ---- Structured clarifications ----

    @Test
    fun `structured clarification serializes code question and answer`() {
        val arr: JSONArray = listOf(
            ClarificationTurn("commute_required", "Q?", "1 saat kadar")
        ).toClarificationsRequestBody()
        assertEquals(1, arr.length())
        val obj = arr.getJSONObject(0)
        assertEquals("commute_required", obj.getString("code"))
        assertEquals("Q?", obj.getString("question"))
        assertEquals("1 saat kadar", obj.getString("answer"))
    }

    @Test
    fun `clarification with null code serializes a null code`() {
        val arr: JSONArray = listOf(
            ClarificationTurn(null, "Which day?", "tomorrow")
        ).toClarificationsRequestBody()
        assertEquals(1, arr.length())
        assertTrue(arr.getJSONObject(0).isNull("code"))
        assertEquals("Which day?", arr.getJSONObject(0).getString("question"))
    }

    @Test
    fun `selected app locale is still sent with clarifications`() {
        val localized = PromptHavenAiAlarmInterpreter(
            baseUrl = "http://x",
            localeProvider = { "tr-TR" }
        )
        val body = localized.buildRequestBody(
            text = "okulda olmam lazım",
            clarifications = listOf(ClarificationTurn("commute_required", "q", "1 saat"))
        )
        assertTrue(body.contains("\"locale\":\"tr-TR\""))
        assertTrue(body.contains("\"clarifications\""))
    }
}
