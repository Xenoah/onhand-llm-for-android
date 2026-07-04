package com.onhand.llm.server

import android.util.Log
import com.onhand.llm.data.ModelRepository
import com.onhand.llm.engine.EngineState
import com.onhand.llm.engine.InferenceEngine
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * OpenAI 互換のローカル API サーバー (NanoHTTPD)。
 *
 * エンドポイント:
 * - GET  /            : ステータスページ (HTML)
 * - GET  /health      : ヘルスチェック (JSON)
 * - GET  /v1/models   : 読み込み済みモデル一覧
 * - POST /v1/chat/completions : チャット補完 (stream 対応)
 */
class ApiServer(
    port: Int,
    private val engine: InferenceEngine,
    private val models: ModelRepository,
    private val scope: CoroutineScope,
    private val logLine: (String) -> Unit,
) : NanoHTTPD(port) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // SSE (text/event-stream) を gzip すると壊れるため無効化
    override fun useGzipWhenAccepted(r: Response): Boolean = false

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            route(session)
        } catch (t: Throwable) {
            Log.e(TAG, "リクエスト処理でエラー", t)
            jsonError(Response.Status.INTERNAL_ERROR, t.message ?: "internal error")
        }
        logLine("${session.method} ${session.uri} -> ${response.status}")
        return withCors(response)
    }

    private fun route(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        }
        return when {
            session.uri == "/" && session.method == Method.GET -> statusPage()
            session.uri == "/health" -> jsonResponse("""{"status":"ok"}""")
            session.uri == "/v1/models" && session.method == Method.GET -> listModels()
            session.uri == "/v1/chat/completions" && session.method == Method.POST ->
                chatCompletions(session)
            else -> jsonError(Response.Status.NOT_FOUND, "not found: ${session.uri}")
        }
    }

    private fun listModels(): Response {
        val loaded = engine.loadedModel
        val body = ModelListResponse(
            data = listOfNotNull(
                loaded?.let { ModelObject(id = it.name, created = it.addedAt / 1000) }
            )
        )
        return jsonResponse(json.encodeToString(ModelListResponse.serializer(), body))
    }

    private fun chatCompletions(session: IHTTPSession): Response {
        val body = readBody(session)
        val request = try {
            json.decodeFromString(ChatCompletionRequest.serializer(), body)
        } catch (t: Throwable) {
            return jsonError(Response.Status.BAD_REQUEST, "JSON の解析に失敗: ${t.message}")
        }
        if (request.messages.isEmpty()) {
            return jsonError(Response.Status.BAD_REQUEST, "messages が空です")
        }
        val loaded = engine.loadedModel
            ?: return jsonError(
                Response.Status.SERVICE_UNAVAILABLE,
                "モデルが読み込まれていません。アプリの「モデル」画面でモデルを読み込んでください。",
                type = "model_not_loaded",
            )

        val prompt = buildPrompt(request)
        val id = "chatcmpl-${UUID.randomUUID().toString().take(24)}"
        val created = System.currentTimeMillis() / 1000

        return if (request.stream) {
            streamCompletion(request, prompt, id, created, loaded.name)
        } else {
            blockingCompletion(request, prompt, id, created, loaded.name)
        }
    }

    private fun blockingCompletion(
        request: ChatCompletionRequest,
        prompt: String,
        id: String,
        created: Long,
        modelName: String,
    ): Response {
        // NanoHTTPD はリクエストごとの専用スレッドで serve するためブロックしてよい
        val text = try {
            runBlocking {
                val sb = StringBuilder()
                engine.generateDetached(prompt, request.temperature, request.topP)
                    .collect { sb.append(it) }
                sb.toString()
            }
        } catch (t: Throwable) {
            return jsonError(Response.Status.INTERNAL_ERROR, "生成に失敗: ${t.message}")
        }
        val response = ChatCompletionResponse(
            id = id,
            created = created,
            model = modelName,
            choices = listOf(ChatChoice(message = ApiChatMessageOut(content = text))),
        )
        return jsonResponse(json.encodeToString(ChatCompletionResponse.serializer(), response))
    }

    private fun streamCompletion(
        request: ChatCompletionRequest,
        prompt: String,
        id: String,
        created: Long,
        modelName: String,
    ): Response {
        val input = PipedInputStream(SSE_BUFFER_SIZE)
        val output = PipedOutputStream(input)

        fun chunkJson(delta: ChunkDelta, finish: String? = null): String =
            json.encodeToString(
                ChatCompletionChunk.serializer(),
                ChatCompletionChunk(
                    id = id,
                    created = created,
                    model = modelName,
                    choices = listOf(ChunkChoice(delta = delta, finishReason = finish)),
                ),
            )

        scope.launch(Dispatchers.IO) {
            try {
                output.write(sseEvent(chunkJson(ChunkDelta(role = "assistant"))))
                engine.generateDetached(prompt, request.temperature, request.topP)
                    .collect { partial ->
                        output.write(sseEvent(chunkJson(ChunkDelta(content = partial))))
                        output.flush()
                    }
                output.write(sseEvent(chunkJson(ChunkDelta(), finish = "stop")))
                output.write("data: [DONE]\n\n".toByteArray())
            } catch (io: IOException) {
                // クライアント切断。generateDetached の collect がキャンセルされ生成も止まる
                Log.i(TAG, "SSE クライアント切断")
            } catch (t: Throwable) {
                Log.e(TAG, "ストリーミング生成でエラー", t)
                runCatching {
                    output.write(
                        sseEvent(
                            json.encodeToString(
                                ErrorResponse.serializer(),
                                ErrorResponse(ErrorBody(t.message ?: "generation failed")),
                            )
                        )
                    )
                }
            } finally {
                runCatching { output.close() }
            }
        }

        val response = newChunkedResponse(Response.Status.OK, "text/event-stream", input)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        return response
    }

    /**
     * OpenAI 形式の messages を1つのプロンプトに変換する。
     * ターンのテンプレート (<start_of_turn> 等) はモデルバンドル側で適用されるため、
     * ここでは役割ラベル付きで連結するだけにする。
     */
    private fun buildPrompt(request: ChatCompletionRequest): String {
        val sb = StringBuilder()
        for (m in request.messages) {
            val text = m.textContent().trim()
            if (text.isEmpty()) continue
            when (m.role) {
                "system" -> sb.appendLine("[指示]").appendLine(text)
                "assistant" -> sb.appendLine("[アシスタント]").appendLine(text)
                else -> sb.appendLine("[ユーザー]").appendLine(text)
            }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    private fun statusPage(): Response {
        val state = engine.state.value
        val stateLabel = when (state) {
            is EngineState.NotLoaded -> "モデル未読み込み"
            is EngineState.Loading -> "読み込み中: ${state.modelName}"
            is EngineState.Ready -> "準備完了: ${state.model.name}"
            is EngineState.Generating -> "生成中: ${state.model.name}"
            is EngineState.Error -> "エラー: ${state.message}"
        }
        val modelRows = models.models.value.joinToString("") {
            "<li>${it.name} (${it.format}, ${it.sizeBytes / (1024 * 1024)} MB)</li>"
        }
        val html = """
            <!doctype html>
            <html lang="ja"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>OnHand LLM API</title>
            <style>body{font-family:sans-serif;max-width:640px;margin:2rem auto;padding:0 1rem}
            code{background:#eee;padding:2px 6px;border-radius:4px}</style></head>
            <body>
            <h1>OnHand LLM API サーバー</h1>
            <p>状態: <strong>$stateLabel</strong></p>
            <h2>エンドポイント</h2>
            <ul>
              <li><code>GET /v1/models</code></li>
              <li><code>POST /v1/chat/completions</code> (OpenAI 互換, stream 対応)</li>
            </ul>
            <h2>インポート済みモデル</h2>
            <ul>$modelRows</ul>
            </body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    // ---- ヘルパー ----

    private fun readBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun sseEvent(data: String): ByteArray = "data: $data\n\n".toByteArray()

    private fun jsonResponse(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun jsonError(
        status: Response.Status,
        message: String,
        type: String = "invalid_request_error",
    ): Response = newFixedLengthResponse(
        status,
        "application/json; charset=utf-8",
        json.encodeToString(
            ErrorResponse.serializer(),
            ErrorResponse(ErrorBody(message = message, type = type)),
        ),
    )

    private fun withCors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        return response
    }

    companion object {
        private const val TAG = "ApiServer"
        private const val SSE_BUFFER_SIZE = 64 * 1024
    }
}
