package com.onhand.llm.engine

import com.onhand.llm.data.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 推論バックエンド */
enum class EngineBackend { CPU, GPU }

/** モデル読み込み時に適用する設定 */
data class EngineConfig(
    val maxTokens: Int = 1024,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val backend: EngineBackend = EngineBackend.CPU,
)

/** エンジンの状態。GUI にそのまま表示できる形で持つ。 */
sealed interface EngineState {
    data object NotLoaded : EngineState
    data class Loading(val modelName: String) : EngineState
    data class Ready(val model: ModelInfo) : EngineState
    data class Generating(val model: ModelInfo) : EngineState
    data class Error(val message: String, val model: ModelInfo?) : EngineState
}

/**
 * ローカルLLM推論エンジンの抽象。
 * 現在の実装は MediaPipe LLM Inference API ([MediaPipeEngine])。
 * 将来 llama.cpp (GGUF) などのバックエンドを追加できるようにインターフェースで分離している。
 */
interface InferenceEngine {
    val state: StateFlow<EngineState>

    /** 現在読み込まれているモデル (未読み込みなら null) */
    val loadedModel: ModelInfo?

    /** モデルを読み込む。結果は [state] に反映される(失敗時は [EngineState.Error])。 */
    suspend fun load(model: ModelInfo, config: EngineConfig)

    /** モデルを解放する。 */
    suspend fun unload()

    /**
     * チャットセッションをリセットする(会話コンテキストを消去)。
     * [config] を渡すとサンプリング設定を差し替える。
     */
    suspend fun resetSession(config: EngineConfig? = null)

    /**
     * チャット用の継続セッションで生成する。
     * 部分テキスト(増分)を流す Flow を返す。完了で正常終了、エラーは例外で通知。
     * Flow の収集をキャンセルすると生成を中断する。
     */
    fun generate(prompt: String): Flow<String>

    /**
     * チャット履歴と独立した一時セッションで生成する(APIサーバー用)。
     * temperature / topP はリクエストで上書き可能。
     */
    fun generateDetached(
        prompt: String,
        temperature: Float? = null,
        topP: Float? = null,
    ): Flow<String>
}
