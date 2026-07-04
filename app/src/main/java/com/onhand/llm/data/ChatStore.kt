package com.onhand.llm.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** チャットの1メッセージ */
@Serializable
data class ChatMessage(
    val role: String, // "user" | "assistant" | "error"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** 生成時間などの補足情報 (アシスタントメッセージのみ) */
    val stats: String? = null,
)

/** チャット履歴を JSON ファイルに永続化する */
class ChatStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "chat_history.json")

    suspend fun load(): List<ChatMessage> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(ChatMessage.serializer()), file.readText())
        }.onFailure { Log.e(TAG, "チャット履歴の読み込みに失敗", it) }
            .getOrDefault(emptyList())
    }

    suspend fun save(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        runCatching {
            file.writeText(
                json.encodeToString(ListSerializer(ChatMessage.serializer()), messages)
            )
        }.onFailure { Log.e(TAG, "チャット履歴の保存に失敗", it) }
        Unit
    }

    suspend fun clear() = save(emptyList())

    companion object {
        private const val TAG = "ChatStore"
    }
}
