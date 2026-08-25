package com.kemalcetin.aialarm.core.planning

import org.json.JSONObject

/**
 * Local-only PromptHaven AI planning preferences. Stored on-device in DataStore;
 * never uploaded as a profile. Only the values needed for the current request
 * are sent to the backend.
 */
data class AiPlanningPreferences(
    val targetSleepMinutes: Int = 480,
    val preparationMinutes: Int = 30,
    val commuteMinutes: Int? = null, // null = unknown; an arrive-by goal asks once
    val bufferMinutes: Int = 15,
    val wakePreference: WakePreference = WakePreference.LATEST_POSSIBLE,
    val preAlarmEnabled: Boolean = true,
    val preAlarmMinutes: Int = 10,
    val backupAlarmEnabled: Boolean = true,
    val backupAlarmMinutes: Int = 10
) {
    /** The preferences block sent in the AI request (only what planning needs). */
    fun toRequestBody(): JSONObject = JSONObject()
        .put("targetSleepMinutes", targetSleepMinutes)
        .put("preparationMinutes", preparationMinutes)
        .put("commuteMinutes", commuteMinutes)
        .put("bufferMinutes", bufferMinutes)
        .put("wakePreference", wakePreference.name)
        .put("preAlarmEnabled", preAlarmEnabled)
        .put("preAlarmMinutes", preAlarmMinutes)
        .put("backupAlarmEnabled", backupAlarmEnabled)
        .put("backupAlarmMinutes", backupAlarmMinutes)
}

enum class WakePreference { LATEST_POSSIBLE, BALANCED, EARLY }
