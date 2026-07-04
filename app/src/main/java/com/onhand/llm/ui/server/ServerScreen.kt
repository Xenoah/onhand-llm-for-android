package com.onhand.llm.ui.server

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onhand.llm.AppContainer
import com.onhand.llm.engine.EngineState
import com.onhand.llm.server.ServerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(container: AppContainer) {
    val vm: ServerViewModel = viewModel { ServerViewModel(container) }
    val serverState by vm.serverState.collectAsState()
    val engineState by vm.engineState.collectAsState()
    val log by vm.log.collectAsState()
    val savedPort by vm.port.collectAsState()

    val context = LocalContext.current
    var portText by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var pendingStart by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 許可の有無に関わらずサーバーは起動する (通知が出ないだけ)
        if (pendingStart) {
            pendingStart = false
            ServerService.start(context)
        }
    }

    fun startServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingStart = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ServerService.start(context)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("API サーバー") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "OpenAI 互換 API サーバー",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (serverState.running) "稼働中 (ポート ${serverState.port})"
                                    else "停止中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = serverState.running,
                                onCheckedChange = { checked ->
                                    if (checked) startServer()
                                    else ServerService.stop(context)
                                },
                            )
                        }
                        serverState.error?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (engineState !is EngineState.Ready &&
                            engineState !is EngineState.Generating
                        ) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "注意: モデルが読み込まれていないため、リクエストはエラーになります。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text("ポート番号 (停止中のみ変更可)") },
                    enabled = !serverState.running,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LaunchedEffect(portText) {
                    portText.toIntOrNull()?.let { p ->
                        if (p in 1024..65535 && p != savedPort) vm.setPort(p)
                    }
                }
            }

            if (serverState.running) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("アクセス URL", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "この端末から: http://127.0.0.1:${serverState.port}/",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            serverState.addresses.forEach { addr ->
                                Text(
                                    "同一ネットワークから: http://$addr:${serverState.port}/",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("使用例 (curl)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = curlExample(serverState.port),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            item {
                Text("リクエストログ", style = MaterialTheme.typography.titleMedium)
            }
            if (log.isEmpty()) {
                item {
                    Text(
                        "ログはまだありません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(log.asReversed()) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

private fun curlExample(port: Int): String = """
curl http://127.0.0.1:$port/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "こんにちは"}],
    "stream": false
  }'
""".trimIndent()
