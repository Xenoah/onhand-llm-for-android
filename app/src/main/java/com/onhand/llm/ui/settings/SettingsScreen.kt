package com.onhand.llm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onhand.llm.AppContainer
import com.onhand.llm.engine.EngineBackend
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) },
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
                        Text("推論バックエンド", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "変更は次回のモデル読み込みから反映されます。GPU は対応端末のみ。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            EngineBackend.entries.forEachIndexed { index, backend ->
                                SegmentedButton(
                                    selected = settings.backend == backend,
                                    onClick = { vm.setBackend(backend) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = EngineBackend.entries.size,
                                    ),
                                ) { Text(backend.name) }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("サンプリング設定", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "「会話をクリア」または次回のモデル読み込みから反映されます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))

                        SliderSetting(
                            label = "Temperature",
                            value = settings.temperature,
                            valueLabel = String.format(Locale.US, "%.2f", settings.temperature),
                            range = 0f..2f,
                            onChangeFinished = vm::setTemperature,
                        )
                        SliderSetting(
                            label = "Top-K",
                            value = settings.topK.toFloat(),
                            valueLabel = settings.topK.toString(),
                            range = 1f..128f,
                            onChangeFinished = { vm.setTopK(it.toInt()) },
                        )
                        SliderSetting(
                            label = "Top-P",
                            value = settings.topP,
                            valueLabel = String.format(Locale.US, "%.2f", settings.topP),
                            range = 0f..1f,
                            onChangeFinished = vm::setTopP,
                        )
                        SliderSetting(
                            label = "最大トークン数 (要モデル再読み込み)",
                            value = settings.maxTokens.toFloat(),
                            valueLabel = settings.maxTokens.toString(),
                            range = 256f..8192f,
                            onChangeFinished = { vm.setMaxTokens(it.toInt()) },
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "起動時に前回のモデルを自動読み込み",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Switch(
                                checked = settings.autoLoadLastModel,
                                onCheckedChange = vm::setAutoLoad,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Hugging Face トークン", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Gemma などゲート付きモデルのダウンロードに使用します。" +
                                "huggingface.co/settings/tokens で発行できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        var token by remember(settings.hfToken) {
                            mutableStateOf(settings.hfToken)
                        }
                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                vm.setHfToken(it.trim())
                            },
                            placeholder = { Text("hf_…") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                Text(
                    "OnHand LLM v0.1.0 ・ 推論エンジン: MediaPipe LLM Inference",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onChangeFinished: (Float) -> Unit,
) {
    var current by remember(value) { mutableStateOf(value) }
    Column {
        Row {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChangeFinished(current) },
            valueRange = range,
        )
    }
}
