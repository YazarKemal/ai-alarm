package com.kemalcetin.aialarm.feature.assistant.network

import com.kemalcetin.aialarm.BuildConfig
import com.kemalcetin.aialarm.core.planning.AiPlanningPreferences
import com.kemalcetin.aialarm.core.planning.toConversationRequestBody
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The result of interpreting a natural-language request.
 *
 * Three kinds:
 *  - [Alarm]: a single QUICK_ALARM prefill (time/date/days/label).
 *  - [GoalPlan]: a computed GOAL_PLAN (deterministic wake plan + up to 3 alarms).
 *  - [NeedsClarification]: the backend needs one short answer before planning.
 *
 * Nothing here schedules an alarm; the app's CREATE PLAN / SAVE are the only
 * schedulers.
 */
sealed interface AlarmInterpretResult {
    /** A single, direct alarm — the QUICK_ALARM case. */
    data class Alarm(
        val hour: Int,
        val minute: Int,
        val date: LocalDate?,
        val repeatDays: Set<DayOfWeek>,
        val label: String
    ) : AlarmInterpretResult

    /** A computed goal plan with up to 3 alarms and the assumptions it used. */
    data class GoalPlan(
        val destinationLabel: String,
        val targetTime: String,
        val sleepStartTime: String,
        val wakeTime: String,
        val leaveByTime: String,
        val sleepShortfallMinutes: Int,
        val assumptions: PlanAssumptions,
        val alarms: List<PlanAlarm>
    ) : AlarmInterpretResult

    /**
     * The backend needs one more piece of info. [code] is a stable machine key
     * (e.g. "commute_required") the UI can localize; [message] is the fallback.
     */
    data class NeedsClarification(val message: String, val code: String? = null) : AlarmInterpretResult
    data class Failed(val reason: String) : AlarmInterpretResult
}

data class PlanAssumptions(
    val sleepMinutes: Int,
    val preparationMinutes: Int,
    val commuteMinutes: Int?,
    val bufferMinutes: Int
)

data class PlanAlarm(
    val time: String,
    val date: LocalDate?,
    val label: String,
    val role: String,
    val enabled: Boolean
)

/**
 * Client for the server-side PromptHaven AI proxy endpoint
 * `interpretAlarmRequest` (V2: two-stage extract-then-plan).
 *
 * SECURITY: the backend holds the AI provider key. This app sends only the
 * user's text, non-secret context, and the planning preferences needed for the
 * current request — no provider secret ever lives on the device. The backend
 * never schedules alarms; it returns values this client strictly validates.
 */
interface AiAlarmInterpreter {
    /**
     * [conversation] is a short, in-memory list of clarification answers
     * (each a user turn) resubmitted so the model can fill a previously-missing
     * value (e.g. commute). Never a full transcript.
     */
    suspend fun interpret(text: String, conversation: List<String> = emptyList()): AlarmInterpretResult
}

class PromptHavenAiAlarmInterpreter(
    private val baseUrl: String = BuildConfig.PROMPTHAVEN_FUNCTIONS_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val tokenProvider: AppCheckTokenProvider? = null,
    /**
     * Supplies the locale sent to the backend so the AI answers in the user's
     * selected application language. Defaults to the device locale when not
     * injected (e.g. in unit tests).
     */
    private val localeProvider: () -> String = { Locale.getDefault().toLanguageTag() },
    /**
     * Supplies the current local planning preferences. Defaults to built-in
     * defaults when not injected (unit tests).
     */
    private val planningPreferencesProvider: suspend () -> AiPlanningPreferences = { AiPlanningPreferences() }
) : AiAlarmInterpreter {

    override suspend fun interpret(text: String, conversation: List<String>): AlarmInterpretResult =
        withContext(Dispatchers.IO) {
            try {
                if (baseUrl.isBlank()) {
                    return@withContext AlarmInterpretResult.Failed("AI proxy not configured")
                }
                val preferences = planningPreferencesProvider()
                val body = buildRequestBody(text, preferences = preferences, conversation = conversation)

                val appCheckToken = tokenProvider?.getToken()
                val request = buildRequest("$baseUrl/interpretAlarmRequest", body, appCheckToken)

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use AlarmInterpretResult.Failed("Server returned ${response.code}")
                    }
                    val payload = response.body?.string() ?: return@use AlarmInterpretResult.Failed("Empty response")
                    parse(payload)
                }
            } catch (e: Exception) {
                AlarmInterpretResult.Failed(e.message ?: "Network error")
            }
        }

    internal fun buildRequest(url: String, body: String, appCheckToken: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        if (appCheckToken != null) {
            builder.addHeader(APP_CHECK_HEADER, appCheckToken)
        }
        return builder.build()
    }

    /**
     * Builds the V2 request body: text + context + local planning preferences +
     * a small recent clarification conversation.
     */
    internal fun buildRequestBody(
        text: String,
        timezone: String = ZoneId.systemDefault().id,
        locale: String = localeProvider(),
        currentDateTime: String = OffsetDateTime.now().toString(),
        preferences: AiPlanningPreferences = AiPlanningPreferences(),
        conversation: List<String> = emptyList()
    ): String = JSONObject()
        .put("text", text.take(MAX_TEXT_LENGTH))
        .put("timezone", timezone)
        .put("locale", locale)
        .put("currentDateTime", currentDateTime)
        .put("preferences", preferences.toRequestBody())
        .put("conversation", conversation.toConversationRequestBody())
        .toString()

    /**
     * Never trust AI JSON directly. Every field is range-checked and unknown
     * values dropped before the result is handed to the UI/planner.
     */
    internal fun parse(payload: String): AlarmInterpretResult {
        return try {
            val root = JSONObject(payload)
            when (root.optString("status", "")) {
                "clarification_required" -> {
                    val question = root.optString("clarificationQuestion", "")
                        .takeIf { it.isNotBlank() }
                        ?: return AlarmInterpretResult.Failed("Clarification without question")
                    AlarmInterpretResult.NeedsClarification(
                        question,
                        root.optString("clarificationCode", "").takeIf { it.isNotBlank() }
                    )
                }
                "success" -> {
                    if (root.optString("kind", "") == "goal_plan") {
                        parseGoalPlan(root.optJSONObject("plan")) ?: AlarmInterpretResult.Failed("Invalid plan payload")
                    } else {
                        parseInterpretation(root.optJSONObject("interpretation"))
                    }
                }
                else -> AlarmInterpretResult.Failed("Unknown status")
            }
        } catch (e: Exception) {
            AlarmInterpretResult.Failed("Invalid response")
        }
    }

    private fun parseGoalPlan(plan: JSONObject?): AlarmInterpretResult.GoalPlan? {
        if (plan == null) return null
        val assumptionsObj = plan.optJSONObject("assumptions") ?: return null

        val sleepMinutes = assumptionsObj.optInt("sleepMinutes", -1)
        val preparationMinutes = assumptionsObj.optInt("preparationMinutes", -1)
        val commuteMinutes = assumptionsObj.optInt("commuteMinutes", -1).takeIf { it >= 0 }
        val bufferMinutes = assumptionsObj.optInt("bufferMinutes", -1)
        if (sleepMinutes < 0 || preparationMinutes < 0 || bufferMinutes < 0) return null

        val alarms = plan.optJSONArray("alarms")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONObject(i) ?: return@mapNotNull null
                parsePlanAlarm(a)
            }
        } ?: emptyList()
        if (alarms.isEmpty() || alarms.size > 3) return null

        return AlarmInterpretResult.GoalPlan(
            destinationLabel = plan.optString("destinationLabel", ""),
            targetTime = plan.optString("targetTime", ""),
            sleepStartTime = plan.optString("sleepStartTime", ""),
            wakeTime = plan.optString("wakeTime", ""),
            leaveByTime = plan.optString("leaveByTime", ""),
            sleepShortfallMinutes = plan.optInt("sleepShortfallMinutes", 0),
            assumptions = PlanAssumptions(
                sleepMinutes = sleepMinutes,
                preparationMinutes = preparationMinutes,
                commuteMinutes = commuteMinutes,
                bufferMinutes = bufferMinutes
            ),
            alarms = alarms
        )
    }

    private fun parsePlanAlarm(a: JSONObject): PlanAlarm? {
        val time = a.optString("time", "")
        val parts = time.split(":").map { it.toIntOrNull() }
        val hour = parts.getOrNull(0)
        val minute = parts.getOrNull(1)
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) return null
        val date = a.optString("date", "").trim().let { if (it.isBlank()) null else runCatching { LocalDate.parse(it) }.getOrNull() }
        return PlanAlarm(
            time = time,
            date = date,
            label = a.optString("label", ""),
            role = a.optString("role", "main"),
            enabled = a.optBoolean("enabled", true)
        )
    }

    private fun parseInterpretation(interp: JSONObject?): AlarmInterpretResult {
        if (interp == null) return AlarmInterpretResult.Failed("No interpretation payload")

        val time = interp.optString("time", "")
        val parts = time.split(":").map { it.toIntOrNull() }
        val hour = parts.getOrNull(0)
        val minute = parts.getOrNull(1)
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            return AlarmInterpretResult.Failed("Invalid time")
        }

        var date: LocalDate? = null
        if (!interp.isNull("date")) {
            val dateStr = interp.optString("date", "").trim()
            date = if (dateStr.isBlank()) {
                null
            } else {
                runCatching { LocalDate.parse(dateStr) }.getOrNull()
                    ?: return AlarmInterpretResult.Failed("Invalid date")
            }
        }

        val days = interp.optJSONArray("repeatDays")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                DayOfWeek.entries.firstOrNull { it.name == arr.optString(i) }
            }.toSet()
        } ?: emptySet()

        if (date != null && days.isNotEmpty()) {
            return AlarmInterpretResult.Failed("Impossible combination")
        }

        val label = interp.optString("label", "").trim().take(MAX_LABEL_LENGTH)
        return AlarmInterpretResult.Alarm(
            hour = hour,
            minute = minute,
            date = date,
            repeatDays = days,
            label = label.ifBlank { "Alarm" }
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_TEXT_LENGTH = 500
        private const val MAX_LABEL_LENGTH = 80

        const val APP_CHECK_HEADER = "x-firebase-app-check"
    }
}
