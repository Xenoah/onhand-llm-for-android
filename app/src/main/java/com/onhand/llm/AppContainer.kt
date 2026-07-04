package com.onhand.llm

import android.app.Application
import com.onhand.llm.data.ChatStore
import com.onhand.llm.data.ModelInfo
import com.onhand.llm.data.ModelRepository
import com.onhand.llm.data.SettingsRepository
import com.onhand.llm.engine.EngineState
import com.onhand.llm.engine.InferenceEngine
import com.onhand.llm.engine.MediaPipeEngine
import com.onhand.llm.server.ServerController
import com.onhand.llm.widget.OnHandWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 手動 DI コンテナ。アプリ全体で共有するシングルトンを保持する。 */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(app)
    val models = ModelRepository(app, appScope)
    val engine: InferenceEngine = MediaPipeEngine(app)
    val chatStore = ChatStore(app)
    val server = ServerController(engine, models, appScope)

    private var autoLoadStarted = false

    init {
        // エンジン/サーバーの状態変化をホーム画面ウィジェットへ反映する
        appScope.launch {
            combine(engine.state, server.state) { e, s ->
                Pair(e::class.simpleName + (e as? EngineState.Ready)?.model?.id, s.running)
            }
                .distinctUntilChanged()
                .collect {
                    runCatching { OnHandWidget().updateAll(app) }
                }
        }
    }

    /** 前回使っていたモデルを起動時に自動読み込みする (設定で無効化可能) */
    fun maybeAutoLoadModel() {
        if (autoLoadStarted) return
        autoLoadStarted = true
        appScope.launch {
            val s = settings.current()
            if (!s.autoLoadLastModel || s.activeModelId.isBlank()) return@launch
            if (engine.state.value != EngineState.NotLoaded) return@launch
            models.awaitInitialized()
            val model = models.findById(s.activeModelId) ?: return@launch
            engine.load(model, s.toEngineConfig())
        }
    }

    /** モデルを読み込み、成功したら「使用中モデル」として記憶する */
    suspend fun loadModel(model: ModelInfo) {
        val s = settings.current()
        engine.load(model, s.toEngineConfig())
        if (engine.state.value is EngineState.Ready) {
            settings.setActiveModelId(model.id)
        }
    }
}
