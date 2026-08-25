package com.kemalcetin.aialarm.feature.assistant.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * One structured clarification exchange sent back to the backend with the next
 * request. The backend knows which answer belongs to which missing value (code),
 * so a valid answer is consumed deterministically and the same question is never
 * re-asked in a loop. [question] is the localized string that was shown to the
 * user, so the provider's conversation is reconstructed in the exact semantic
 * order (SYSTEM, USER original, ASSISTANT question, USER answer).
 */
data class ClarificationTurn(
    val code: String?,
    val question: String,
    val answer: String
)

/** Serializes clarifications as `[{ code, question, answer }]`. */
fun List<ClarificationTurn>.toClarificationsRequestBody(): JSONArray =
    JSONArray().apply {
        forEach { turn ->
            put(
                JSONObject()
                    .put("code", turn.code ?: JSONObject.NULL)
                    .put("question", turn.question)
                    .put("answer", turn.answer)
            )
        }
    }
