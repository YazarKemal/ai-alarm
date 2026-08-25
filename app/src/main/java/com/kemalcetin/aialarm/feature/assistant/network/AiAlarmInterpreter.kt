package com.kemalcetin.aialarm.feature.assistant.network

import java.time.DayOfWeek
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
 * schedules an alarm.
 */
sealed interface AlarmInterpretResult {
    data class Alarm(
        val hour: Int,
        val minute: Int,
        val repeatDays: Set<DayOfWeek>
    ) : AlarmInterpretResult

    data class NeedsClarification(val message: String) : AlarmInterpretResult
    data class Failed(val reason: String) : AlarmInterpretResult
}

/**
 * Client for the server-side PromptHaven AI proxy endpoint
 * `interpretAlarmRequest`.
 *
 * SECURITY: the PromptHaven backend holds the AI provider key. This app sends
 * only the user's natural-language text and a public HTTPS URL — no provider
 * secret ever lives on the device. The backend never schedules alarms; it only
 * returns editor prefill values, which this client validates before use.
 */
interface AiAlarmInterpreter {
    suspend fun interpret(prompt: String): AlarmInterpretResult
}

class PromptHavenAiAlarmInterpreter(
    private val baseUrl: String = PROMPTHAVEN_FUNCTIONS_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : AiAlarmInterpreter {

    override suspend fun interpret(prompt: String): AlarmInterpretResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("prompt", prompt.take(MAX_PROMPT_LENGTH))
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)

                val request = Request.Builder()
                    .url("$baseUrl/interpretAlarmRequest")
                    .post(body)
                    .build()

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
     * Never trust AI JSON directly. Every field is range-checked and unknown
     * day names are dropped before the result is handed to the editor.
     */
    private fun parse(payload: String): AlarmInterpretResult {
        return try {
            val root = JSONObject(payload)
            val status = root.optString("status", "ok")
            if (status == "clarification") {
                val message = root.optString("clarification", "")
                    .takeIf { it.isNotBlank() }
                    ?: "Could you clarify your alarm?"
                AlarmInterpretResult.NeedsClarification(message)
            } else {
                val alarm = root.optJSONObject("alarm")
                    ?: return AlarmInterpretResult.Failed("No alarm payload")
                val hour = alarm.optInt("hour", -1)
                val minute = alarm.optInt("minute", -1)
                if (hour !in 0..23 || minute !in 0..59) {
                    return AlarmInterpretResult.Failed("Invalid time")
                }
                val days = alarm.optJSONArray("repeatDays")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        DayOfWeek.entries.firstOrNull { it.name == arr.getString(i) }
                    }.toSet()
                } ?: emptySet()
                AlarmInterpretResult.Alarm(hour = hour, minute = minute, repeatDays = days)
            }
        } catch (e: Exception) {
            AlarmInterpretResult.Failed("Invalid response")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_PROMPT_LENGTH = 500

        /**
         * Public HTTPS endpoint for the PromptHaven AI proxy. This is not a
         * secret — it is a publicly reachable function URL. The provider key
         * lives only in the server's secret management.
         */
        const val PROMPTHAVEN_FUNCTIONS_BASE_URL =
            "https://<region>-<project>.cloudfunctions.net"
    }
}
