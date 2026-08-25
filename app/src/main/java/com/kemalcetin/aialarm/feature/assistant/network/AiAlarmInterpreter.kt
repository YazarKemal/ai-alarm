package com.kemalcetin.aialarm.feature.assistant.network

import com.kemalcetin.aialarm.BuildConfig
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
 * The result of interpreting a natural-language alarm request.
 *
 * Only ever carries values that should PREFILL the alarm editor. Nothing here
 * schedules an alarm. [Alarm.date] is the optional pinned one-time calendar
 * date (ISO-8601); [Alarm.repeatDays] non-empty means a repeating alarm and
 * [Alarm.date] must then be null.
 */
sealed interface AlarmInterpretResult {
    data class Alarm(
        val hour: Int,
        val minute: Int,
        val date: LocalDate?,
        val repeatDays: Set<DayOfWeek>,
        val label: String
    ) : AlarmInterpretResult

    data class NeedsClarification(val message: String) : AlarmInterpretResult
    data class Failed(val reason: String) : AlarmInterpretResult
}

/**
 * Client for the server-side PromptHaven AI proxy endpoint
 * `interpretAlarmRequest`.
 *
 * SECURITY: the PromptHaven backend holds the AI provider key. This app sends
 * only the user's natural-language text plus non-secret request context
 * (timezone, locale, current time) over a public HTTPS URL — no provider secret
 * ever lives on the device. The backend never schedules alarms; it only returns
 * editor prefill values, which this client strictly validates before use.
 */
interface AiAlarmInterpreter {
    suspend fun interpret(text: String): AlarmInterpretResult
}

class PromptHavenAiAlarmInterpreter(
    private val baseUrl: String = BuildConfig.PROMPTHAVEN_FUNCTIONS_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val tokenProvider: AppCheckTokenProvider? = null
) : AiAlarmInterpreter {

    override suspend fun interpret(text: String): AlarmInterpretResult =
        withContext(Dispatchers.IO) {
            try {
                // A blank base URL means the backend is not configured for this
                // build (release ships blank until the deployer sets the Gradle
                // property). Fail gracefully instead of building a bad request.
                if (baseUrl.isBlank()) {
                    return@withContext AlarmInterpretResult.Failed("AI proxy not configured")
                }
                val body = buildRequestBody(text)

                // Attach a real Firebase App Check token when available. When the
                // token is unavailable (not configured / Play Integrity fails) we
                // send without it; an enforcing backend rejects and the app falls
                // back to the offline workflow. We never fabricate a token.
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

    /**
     * Builds the HTTP request. If [appCheckToken] is non-null the Firebase App
     * Check token is attached as the `x-firebase-app-check` header; otherwise
     * no such header is added. This never adds an Authorization/provider header.
     */
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
     * Builds the request body sent to the backend. The backend is the only
     * party that knows the provider secret; this request carries only the
     * user's text and non-secret context the model needs to interpret it.
     */
    internal fun buildRequestBody(
        text: String,
        timezone: String = ZoneId.systemDefault().id,
        locale: String = Locale.getDefault().toLanguageTag(),
        currentDateTime: String = OffsetDateTime.now().toString()
    ): String = JSONObject()
        .put("text", text.take(MAX_TEXT_LENGTH))
        .put("timezone", timezone)
        .put("locale", locale)
        .put("currentDateTime", currentDateTime)
        .toString()

    /**
     * Never trust AI JSON directly. Every field is range-checked and unknown
     * day names are dropped before the result is handed to the editor.
     * "Impossible combinations" (e.g. a date alongside repeat days) are rejected.
     */
    internal fun parse(payload: String): AlarmInterpretResult {
        return try {
            val root = JSONObject(payload)
            when (root.optString("status", "")) {
                "clarification_required" -> {
                    val question = root.optString("clarificationQuestion", "")
                        .takeIf { it.isNotBlank() }
                        ?: return AlarmInterpretResult.Failed("Clarification without question")
                    AlarmInterpretResult.NeedsClarification(question)
                }
                "success" -> parseInterpretation(root.optJSONObject("interpretation"))
                else -> AlarmInterpretResult.Failed("Unknown status")
            }
        } catch (e: Exception) {
            AlarmInterpretResult.Failed("Invalid response")
        }
    }

    private fun parseInterpretation(interp: JSONObject?): AlarmInterpretResult {
        if (interp == null) return AlarmInterpretResult.Failed("No interpretation payload")

        // "HH:mm" — must be strict and valid.
        val time = interp.optString("time", "")
        val parts = time.split(":").map { it.toIntOrNull() }
        val hour = parts.getOrNull(0)
        val minute = parts.getOrNull(1)
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            return AlarmInterpretResult.Failed("Invalid time")
        }

        // date is optional but must be a valid ISO-8601 local date if present.
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

        // repeatDays: strictly-valid DayOfWeek names; unknown entries dropped.
        val days = interp.optJSONArray("repeatDays")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                DayOfWeek.entries.firstOrNull { it.name == arr.optString(i) }
            }.toSet()
        } ?: emptySet()

        // A pinned date and repeat days are mutually exclusive.
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

        /** Header carrying the Firebase App Check token (matches the backend). */
        const val APP_CHECK_HEADER = "x-firebase-app-check"
    }
}
