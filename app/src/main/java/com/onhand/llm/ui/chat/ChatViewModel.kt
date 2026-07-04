package com.onhand.llm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onhand.llm.AppContainer
import com.onhand.llm.data.ChatMessage
import com.onhand.llm.engine.EngineState
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val engineState = container.engine.state

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    /** 生成途中のアシスタント応答 (null なら生成していない) */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText = _streamingText.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            _messages.value = container.chatStore.load()
        }
    }

    val isGenerating: Boolean
        get() = generateJob?.isActive == true

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || isGenerating) return
        if (engineState.value !is EngineState.Ready) return

        _messages.value = _messages.value + ChatMessage(role = "user", content = prompt)
        persist()

        generateJob = viewModelScope.launch {
            val builder = StringBuilder()
            val startedAt = System.currentTimeMillis()
            _streamingText.value = ""
            var error: Throwable? = null
            try {
                container.engine.generate(prompt).collect { partial ->
                    builder.append(partial)
                    _streamingText.value = builder.toString()
                }
            } catch (c: CancellationException) {
                // ユーザーによる停止。ここまでの応答は保存する
            } catch (t: Throwable) {
                error = t
            } finally {
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
                if (builder.isNotEmpty()) {
                    _messages.value = _messages.value + ChatMessage(
                        role = "assistant",
                        content = builder.toString(),
                        stats = String.format(Locale.US, "%.1f 秒", elapsed),
                    )
                }
                if (error != null) {
                    _messages.value = _messages.value + ChatMessage(
                        role = "error",
                        content = "生成に失敗しました: ${error.message ?: "不明なエラー"}",
                    )
                }
                _streamingText.value = null
                persist()
            }
        }
    }

    fun stop() {
        generateJob?.cancel()
    }

    /** 会話をクリアし、モデルのコンテキストもリセットする (最新の設定を反映) */
    fun clearChat() {
        if (isGenerating) return
        viewModelScope.launch {
            _messages.value = emptyList()
            container.chatStore.clear()
            container.engine.resetSession(container.settings.current().toEngineConfig())
        }
    }

    /** エラーになったモデルの再読み込み */
    fun retryLoad() {
        val state = engineState.value
        if (state is EngineState.Error && state.model != null) {
            viewModelScope.launch { container.loadModel(state.model) }
        }
    }

    private fun persist() {
        val snapshot = _messages.value
        viewModelScope.launch { container.chatStore.save(snapshot) }
    }
}
