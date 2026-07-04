package com.onhand.llm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onhand.llm.AppContainer
import com.onhand.llm.data.AppSettings
import com.onhand.llm.engine.EngineBackend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTemperature(v: Float) = launchSave { container.settings.setTemperature(v) }
    fun setTopK(v: Int) = launchSave { container.settings.setTopK(v) }
    fun setTopP(v: Float) = launchSave { container.settings.setTopP(v) }
    fun setMaxTokens(v: Int) = launchSave { container.settings.setMaxTokens(v) }
    fun setBackend(v: EngineBackend) = launchSave { container.settings.setBackend(v) }
    fun setHfToken(v: String) = launchSave { container.settings.setHfToken(v) }
    fun setAutoLoad(v: Boolean) = launchSave { container.settings.setAutoLoadLastModel(v) }

    private fun launchSave(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
