package com.onhand.llm.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.onhand.llm.data.ModelInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MediaPipe LLM Inference API を使った推論エンジン実装。
 * .task / .litertlm 形式のモデル (Gemma, Phi, Qwen など) を Android 端末上で実行する。
 */
class MediaPipeEngine(private val context: Context) : InferenceEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.NotLoaded)
    override val state = _state.asStateFlow()

    override val loadedModel: ModelInfo?
        get() = when (val s = _state.value) {
            is EngineState.Ready -> s.model
            is EngineState.Generating -> s.model
            else -> null
        }

    private var llm: LlmInference? = null
    private var chatSession: LlmInferenceSession? = null
    private var currentConfig: EngineConfig? = null
    private var currentModel: ModelInfo? = null

    /** モデル操作と生成を直列化するためのロック (エンジンは同時に1つの生成しか扱えない) */
    private val engineMutex = Mutex()

    override suspend fun load(model: ModelInfo, config: EngineConfig) =
        withContext(Dispatchers.IO) {
            engineMutex.withLock {
                _state.value = EngineState.Loading(model.name)
                try {
                    closeAll()
                    val file = File(model.path)
                    check(file.exists()) { "モデルファイルが見つかりません: ${model.path}" }

                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(file.absolutePath)
                        .setMaxTokens(config.maxTokens)
                        .setPreferredBackend(
                            when (config.backend) {
                                EngineBackend.GPU -> LlmInference.Backend.GPU
                                EngineBackend.CPU -> LlmInference.Backend.CPU
                            }
                        )
                        .build()
                    llm = LlmInference.createFromOptions(context, options)
                    chatSession = newSession(config)
                    currentConfig = config
                    currentModel = model
                    _state.value = EngineState.Ready(model)
                } catch (t: Throwable) {
                    Log.e(TAG, "モデル読み込み失敗", t)
                    closeAll()
                    _state.value = EngineState.Error(
                        t.message ?: "モデルの読み込みに失敗しました", model
                    )
                }
            }
        }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            closeAll()
            currentModel = null
            currentConfig = null
            _state.value = EngineState.NotLoaded
        }
    }

    override suspend fun resetSession(config: EngineConfig?) = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            val engine = llm ?: return@withLock
            val model = currentModel ?: return@withLock
            val newConfig = (config ?: currentConfig)?.copy() ?: EngineConfig()
            try {
                runCatching { chatSession?.close() }
                chatSession = newSession(newConfig)
                currentConfig = newConfig
                _state.value = EngineState.Ready(model)
            } catch (t: Throwable) {
                Log.e(TAG, "セッションリセット失敗", t)
                _state.value = EngineState.Error(
                    t.message ?: "セッションのリセットに失敗しました", model
                )
            }
        }
    }

    override fun generate(prompt: String): Flow<String> =
        generateInternal(prompt) { chatSession ?: error("モデルが読み込まれていません") }

    override fun generateDetached(
        prompt: String,
        temperature: Float?,
        topP: Float?,
    ): Flow<String> = generateInternal(prompt, closeSessionAfter = true) {
        val base = currentConfig ?: EngineConfig()
        newSession(
            base.copy(
                temperature = temperature ?: base.temperature,
                topP = topP ?: base.topP,
            )
        )
    }

    /**
     * 生成の共通処理。コールバック API を Flow に変換する。
     * Flow キャンセル時は生成を中断する。
     */
    private fun generateInternal(
        prompt: String,
        closeSessionAfter: Boolean = false,
        sessionProvider: () -> LlmInferenceSession,
    ): Flow<String> = callbackFlow {
        check(llm != null) { "モデルが読み込まれていません" }
        val model = currentModel ?: error("モデルが読み込まれていません")

        engineMutex.lock()
        var session: LlmInferenceSession? = null
        var finished = false
        try {
            _state.value = EngineState.Generating(model)
            val s = sessionProvider()
            session = s
            s.addQueryChunk(prompt)
            s.generateResponseAsync { partialResult, done ->
                trySendBlocking(partialResult)
                if (done) {
                    finished = true
                    channel.close()
                }
            }
        } catch (t: Throwable) {
            if (closeSessionAfter) runCatching { session?.close() }
            engineMutex.unlock()
            _state.value = EngineState.Ready(model)
            throw t
        }

        awaitClose {
            if (!finished) {
                runCatching { session?.cancelGenerateResponseAsync() }
            }
            if (closeSessionAfter) runCatching { session?.close() }
            engineMutex.unlock()
            if (_state.value is EngineState.Generating) {
                _state.value = EngineState.Ready(model)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun newSession(config: EngineConfig): LlmInferenceSession {
        val engine = llm ?: error("モデルが読み込まれていません")
        val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(config.temperature)
            .setTopK(config.topK)
            .setTopP(config.topP)
            .build()
        return LlmInferenceSession.createFromOptions(engine, options)
    }

    private fun closeAll() {
        runCatching { chatSession?.close() }
        runCatching { llm?.close() }
        chatSession = null
        llm = null
    }

    companion object {
        private const val TAG = "MediaPipeEngine"
    }
}
