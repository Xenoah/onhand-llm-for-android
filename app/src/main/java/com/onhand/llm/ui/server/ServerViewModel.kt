package com.onhand.llm.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onhand.llm.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(private val container: AppContainer) : ViewModel() {

    val serverState = container.server.state
    val log = container.server.log
    val engineState = container.engine.state

    val port = container.settings.settings
        .map { it.serverPort }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 8080)

    fun setPort(value: Int) {
        viewModelScope.launch { container.settings.setServerPort(value) }
    }
}
