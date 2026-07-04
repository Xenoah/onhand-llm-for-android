package com.onhand.llm.server

import android.util.Log
import com.onhand.llm.data.ModelRepository
import com.onhand.llm.engine.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** API サーバーの起動/停止と状態を管理する */
class ServerController(
    private val engine: InferenceEngine,
    private val models: ModelRepository,
    private val scope: CoroutineScope,
) {
    data class State(
        val running: Boolean = false,
        val port: Int = 8080,
        val addresses: List<String> = emptyList(),
        val error: String? = null,
    )

    private var server: ApiServer? = null

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log = _log.asStateFlow()

    @Synchronized
    fun start(port: Int): Result<Unit> {
        if (server != null) return Result.success(Unit)
        return try {
            val s = ApiServer(port, engine, models, scope, ::appendLog)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = s
            _state.value = State(
                running = true,
                port = port,
                addresses = localIpAddresses(),
            )
            appendLog("サーバーを起動しました (ポート $port)")
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "サーバー起動失敗", t)
            _state.value = State(
                running = false,
                port = port,
                error = "起動に失敗しました: ${t.message}",
            )
            Result.failure(t)
        }
    }

    @Synchronized
    fun stop() {
        server?.let {
            runCatching { it.stop() }
            appendLog("サーバーを停止しました")
        }
        server = null
        _state.value = _state.value.copy(running = false, addresses = emptyList(), error = null)
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _log.value = (_log.value + "[$time] $line").takeLast(MAX_LOG_LINES)
    }

    /** Wi-Fi 等のローカル IPv4 アドレスを列挙する (他端末からのアクセス用) */
    private fun localIpAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif -> nif.inetAddresses.toList() }
            .filter { it.isSiteLocalAddress && it.hostAddress?.contains(':') == false }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "ServerController"
        private const val MAX_LOG_LINES = 200
    }
}
