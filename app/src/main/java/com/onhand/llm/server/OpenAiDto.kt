package com.onhand.llm.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---- リクエスト ----

@Serializable
data class ChatCompletionRequest(
    val model: String? = null,
    val messages: List<ApiChatMessage> = emptyList(),
    val stream: Boolean = false,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class ApiChatMessage(
    val role: String = "user",
    /** OpenAI 互換: 文字列または {type:"text"} パーツ配列の両方を受け付ける */
    val content: JsonElement = JsonPrimitive(""),
) {
    fun textContent(): String = when (content) {
        is JsonPrimitive -> content.content
        is JsonArray -> content.mapNotNull { part ->
            (part as? JsonObject)?.get("text")?.jsonPrimitive?.content
        }.joinToString("\n")
        else -> content.toString()
    }
}

// ---- レスポンス (非ストリーミング) ----

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage = Usage(),
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ApiChatMessageOut,
    @SerialName("finish_reason") val finishReason: String = "stop",
)

@Serializable
data class ApiChatMessageOut(
    val role: String = "assistant",
    val content: String = "",
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// ---- レスポンス (ストリーミング) ----

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>,
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChunkDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChunkDelta(
    val role: String? = null,
    val content: String? = null,
)

// ---- /v1/models ----

@Serializable
data class ModelListResponse(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelObject>,
)

@Serializable
data class ModelObject(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "onhand-llm",
)

// ---- エラー ----

@Serializable
data class ErrorResponse(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val message: String,
    val type: String = "invalid_request_error",
    val code: String? = null,
)
