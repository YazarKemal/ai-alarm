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

/** A suggested alarm the assistant believes the user forgot. */
data class ScheduleInsight(
    val suggestedHour: Int?,
    val suggestedMinute: Int?,
    val suggestedDays: Set<DayOfWeek>?,
    val rationale: String?
)

/**
 * Optional enhancement to the on-device [com.kemalcetin.aialarm.feature.assistant.learning.ScheduleLearner].
 * When no provider is configured (no API key) the assistant runs purely on heuristics.
 */
interface AiInsightsProvider {
    suspend fun suggestForgottenAlarm(behaviorSummary: String): ScheduleInsight?
}

class DeepSeekInsightsProvider(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : AiInsightsProvider {

    override suspend fun suggestForgottenAlarm(behaviorSummary: String): ScheduleInsight? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("model", "deepseek-chat")
                    .put("response_format", JSONObject().put("type", "json_object"))
                    .put("messages", arrayOf(
                        JSONObject()
                            .put("role", "system")
                            .put("content", SYSTEM_PROMPT),
                        JSONObject()
                            .put("role", "user")
                            .put("content", behaviorSummary)
                    ))

                val request = Request.Builder()
                    .url("https://api.deepseek.com/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val payload = response.body?.string() ?: return@use null
                    val content = JSONObject(payload)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    parseInsight(JSONObject(content))
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun parseInsight(json: JSONObject): ScheduleInsight? {
        val hour = json.optInt("suggestedHour", -1).takeIf { it in 0..23 }
        val minute = json.optInt("suggestedMinute", -1).takeIf { it in 0..59 }
        val days = json.optJSONArray("suggestedDays")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                DayOfWeek.entries.firstOrNull { it.name == arr.getString(i) }
            }.toSet()
        }
        val rationale = json.optString("rationale", "").takeIf { it.isNotBlank() }
        return ScheduleInsight(hour, minute, days, rationale)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SYSTEM_PROMPT =
            "You are a smart alarm assistant. Given the user's recent alarm behavior, decide " +
                "whether they likely forgot their next alarm and at what time. Respond ONLY with JSON " +
                "like {\"suggestedHour\":7,\"suggestedMinute\":0,\"suggestedDays\":[\"MONDAY\",\"TUESDAY\"]," +
                "\"rationale\":\"short reason\"}. suggestedDays use DayOfWeek enum names. If unsure, set all values to null."
    }
}
