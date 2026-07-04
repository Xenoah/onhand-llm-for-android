package com.onhand.llm.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onhand.llm.AppContainer
import com.onhand.llm.data.ModelInfo
import com.onhand.llm.data.RecommendedModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** SAF インポートの進捗 */
data class ImportState(
    val fileName: String,
    /** 0.0〜1.0。総サイズ不明なら null */
    val progress: Float?,
)

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    val models = container.models.models
    val downloads = container.models.downloads
    val engineState = container.engine.state

    val activeModelId = container.settings.settings
        .map { it.activeModelId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _importState = MutableStateFlow<ImportState?>(null)
    val importState = _importState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun load(model: ModelInfo) {
        viewModelScope.launch { container.loadModel(model) }
    }

    fun unload() {
        viewModelScope.launch { container.engine.unload() }
    }

    fun delete(model: ModelInfo) {
        viewModelScope.launch {
            if (container.engine.loadedModel?.id == model.id) {
                container.engine.unload()
            }
            container.models.delete(model)
            _message.value = "${model.name} を削除しました"
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState("ファイル", null)
                val info = container.models.importFromUri(uri) { copied, total ->
                    _importState.value = ImportState(
                        fileName = "インポート中…",
                        progress = if (total > 0) copied.toFloat() / total else null,
                    )
                }
                _message.value = "${info.name} を追加しました"
            } catch (t: Throwable) {
                _message.value = "インポートに失敗: ${t.message}"
            } finally {
                _importState.value = null
            }
        }
    }

    fun downloadFromUrl(url: String) {
        viewModelScope.launch {
            try {
                container.models.startDownload(url, container.settings.current().hfToken)
                _message.value = "ダウンロードを開始しました"
            } catch (t: Throwable) {
                _message.value = "ダウンロード開始に失敗: ${t.message}"
            }
        }
    }

    fun downloadRecommended(model: RecommendedModel) = downloadFromUrl(model.url)

    fun cancelDownload(downloadId: Long) {
        container.models.cancelDownload(downloadId)
    }
}
