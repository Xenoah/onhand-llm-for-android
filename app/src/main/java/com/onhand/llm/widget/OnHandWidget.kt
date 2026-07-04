package com.onhand.llm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.onhand.llm.MainActivity
import com.onhand.llm.OnHandApp
import com.onhand.llm.engine.EngineState
import com.onhand.llm.server.ServerController
import com.onhand.llm.server.ServerService
import kotlinx.coroutines.delay

/** ホーム画面ウィジェット: 状態表示 + チャット起動 + APIサーバー切り替え */
class OnHandWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = OnHandApp.from(context)
        provideContent {
            val engineState by container.engine.state.collectAsState()
            val serverState by container.server.state.collectAsState()
            GlanceTheme {
                WidgetContent(engineState, serverState)
            }
        }
    }
}

@Composable
private fun WidgetContent(
    engineState: EngineState,
    serverState: ServerController.State,
) {
    val modelLabel = when (engineState) {
        is EngineState.NotLoaded -> "モデル未読み込み"
        is EngineState.Loading -> "読み込み中: ${engineState.modelName}"
        is EngineState.Ready -> "モデル: ${engineState.model.name}"
        is EngineState.Generating -> "生成中: ${engineState.model.name}"
        is EngineState.Error -> "エラー: ${engineState.message.take(30)}"
    }
    val serverLabel =
        if (serverState.running) "API: 稼働中 (ポート ${serverState.port})" else "API: 停止中"

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Text(
            text = "OnHand LLM",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = modelLabel,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
        Text(
            text = serverLabel,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button(
                text = "チャット",
                onClick = actionStartActivity<MainActivity>(),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Button(
                text = if (serverState.running) "API停止" else "API開始",
                onClick = actionRunCallback<ToggleServerAction>(),
            )
        }
    }
}

/** ウィジェットの「API開始/停止」ボタン */
class ToggleServerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val container = OnHandApp.from(context)
        if (container.server.state.value.running) {
            ServerService.stop(context)
        } else {
            ServerService.start(context)
        }
        // サービス起動/停止が状態に反映されるのを少し待ってから再描画
        delay(500)
        OnHandWidget().updateAll(context)
    }
}

class OnHandWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OnHandWidget()
}
