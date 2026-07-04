package com.onhand.llm.ui.models

import android.app.DownloadManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onhand.llm.AppContainer
import com.onhand.llm.data.DownloadStatus
import com.onhand.llm.data.ModelInfo
import com.onhand.llm.data.RECOMMENDED_MODELS
import com.onhand.llm.engine.EngineState
import com.onhand.llm.ui.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(container: AppContainer) {
    val vm: ModelsViewModel = viewModel { ModelsViewModel(container) }
    val models by vm.models.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val engineState by vm.engineState.collectAsState()
    val activeModelId by vm.activeModelId.collectAsState()
    val importState by vm.importState.collectAsState()
    val message by vm.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFromUri) }

    var showUrlDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ModelInfo?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("モデル管理") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("ファイルから追加")
                    }
                    OutlinedButton(onClick = { showUrlDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("URLから")
                    }
                }
            }

            importState?.let { st ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(st.fileName, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            val progress = st.progress
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item {
                    Text("ダウンロード", style = MaterialTheme.typography.titleMedium)
                }
                items(downloads, key = { it.downloadId }) { dl ->
                    DownloadCard(dl, onCancel = { vm.cancelDownload(dl.downloadId) })
                }
            }

            item {
                Text("インポート済みモデル", style = MaterialTheme.typography.titleMedium)
            }
            if (models.isEmpty()) {
                item {
                    Text(
                        "モデルがまだありません。上のボタンから .task / .litertlm ファイルを追加してください。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    engineState = engineState,
                    isActive = model.id == activeModelId,
                    onLoad = { vm.load(model) },
                    onUnload = { vm.unload() },
                    onDelete = { deleteTarget = model },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("おすすめモデル", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Hugging Face からダウンロードします。ゲート付きモデルは設定画面で HF トークンの登録と、" +
                        "ブラウザでのライセンス同意が必要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(RECOMMENDED_MODELS, key = { it.url }) { rec ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(rec.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            rec.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = {},
                                label = { Text(rec.approxSize) },
                            )
                            if (rec.requiresHfToken) {
                                Spacer(Modifier.size(6.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("要 HF トークン") },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { vm.downloadRecommended(rec) }) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("取得")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        UrlDownloadDialog(
            onDismiss = { showUrlDialog = false },
            onDownload = { url ->
                showUrlDialog = false
                vm.downloadFromUrl(url)
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("モデルを削除") },
            text = {
                Text("${target.name} (${formatBytes(target.sizeBytes)}) を削除しますか?この操作は取り消せません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    engineState: EngineState,
    isActive: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isLoaded = (engineState as? EngineState.Ready)?.model?.id == model.id ||
        (engineState as? EngineState.Generating)?.model?.id == model.id
    val isLoading = engineState is EngineState.Loading

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatBytes(model.sizeBytes)} ・ .${model.format}" +
                    if (isActive) " ・ 前回使用" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoaded) {
                    AssistChip(onClick = {}, label = { Text("使用中") })
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = onUnload) { Text("解放") }
                } else {
                    Button(onClick = onLoad, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                        }
                        Text("読み込む")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "削除")
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(dl: DownloadStatus, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dl.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "キャンセル")
                }
            }
            when (dl.status) {
                DownloadManager.STATUS_FAILED -> Text(
                    "失敗しました (コード ${dl.reason})。URL や HF トークン、ライセンス同意を確認してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val progress =
                        if (dl.totalBytes > 0) dl.downloadedBytes.toFloat() / dl.totalBytes
                        else null
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${formatBytes(dl.downloadedBytes)} / ${formatBytes(dl.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlDownloadDialog(
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URL からダウンロード") },
        text = {
            Column {
                Text(
                    ".task / .litertlm / .bin ファイルの直接ダウンロード URL を入力してください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://huggingface.co/…/model.task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDownload(url.trim()) },
                enabled = url.trim().startsWith("http"),
            ) { Text("ダウンロード") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
