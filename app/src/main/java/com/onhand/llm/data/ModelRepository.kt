package com.onhand.llm.data

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * モデルファイルの管理。
 * - SAF (ファイルピッカー) からのインポート → 内部ストレージ files/models/
 * - URL からのダウンロード (DownloadManager) → 外部アプリ専用領域 files/models/
 * - 登録情報は files/models.json に保存
 */
class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val registryFile = File(context.filesDir, "models.json")
    private val internalModelsDir = File(context.filesDir, "models").apply { mkdirs() }
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models = _models.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadStatus>>(emptyList())
    val downloads = _downloads.asStateFlow()

    private val registryMutex = Mutex()
    private var pollJob: Job? = null

    private val initJob: Job = scope.launch(Dispatchers.IO) {
        loadRegistry()
        adoptCompletedDownloads()
        scanOrphanFiles()
        startPollingIfNeeded()
    }

    suspend fun awaitInitialized() = initJob.join()

    fun findById(id: String): ModelInfo? = _models.value.find { it.id == id }

    // ---- インポート (SAF) ----

    /**
     * ファイルピッカーで選択されたモデルを内部ストレージへコピーして登録する。
     * @param onProgress (コピー済みバイト, 総バイト。総バイトは不明なら -1)
     */
    suspend fun importFromUri(
        uri: Uri,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ModelInfo = withContext(Dispatchers.IO) {
        val (displayName, totalSize) = queryNameAndSize(uri)
        val fileName = displayName ?: "model_${System.currentTimeMillis()}.task"
        require(ModelInfo.isSupported(fileName)) {
            "未対応の形式です (.task / .litertlm / .bin のみ): $fileName"
        }
        val dest = uniqueFile(internalModelsDir, fileName)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "ファイルを開けませんでした" }
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, totalSize)
                    }
                }
            }
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        register(dest)
    }

    // ---- ダウンロード (DownloadManager) ----

    /** URL からモデルをダウンロードする。Hugging Face のゲート付きモデルにはトークンが必要。 */
    fun startDownload(url: String, hfToken: String?): Long {
        val fileName = url.substringBefore('?').substringAfterLast('/')
            .ifBlank { "model_${System.currentTimeMillis()}.task" }
        require(ModelInfo.isSupported(fileName)) {
            "未対応の形式です (.task / .litertlm / .bin のみ): $fileName"
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("OnHand LLM モデルダウンロード")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(context, "models", fileName)
        if (!hfToken.isNullOrBlank()) {
            request.addRequestHeader("Authorization", "Bearer $hfToken")
        }
        val id = downloadManager.enqueue(request)
        startPollingIfNeeded()
        return id
    }

    /** ダウンロードをキャンセル(または失敗エントリを消去)する。 */
    fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        _downloads.value = _downloads.value.filterNot { it.downloadId == downloadId }
    }

    // ---- 削除 ----

    suspend fun delete(model: ModelInfo) = withContext(Dispatchers.IO) {
        registryMutex.withLock {
            runCatching { File(model.path).delete() }
            _models.value = _models.value.filterNot { it.id == model.id }
            saveRegistryLocked()
        }
    }

    // ---- 内部処理 ----

    private suspend fun register(file: File): ModelInfo = registryMutex.withLock {
        val existing = _models.value.find { it.path == file.absolutePath }
        if (existing != null) return@withLock existing
        val info = ModelInfo(
            id = UUID.randomUUID().toString(),
            name = file.name.substringBeforeLast('.'),
            path = file.absolutePath,
            sizeBytes = file.length(),
            format = ModelInfo.formatOf(file.name),
            addedAt = System.currentTimeMillis(),
        )
        _models.value = _models.value + info
        saveRegistryLocked()
        info
    }

    private suspend fun loadRegistry() = registryMutex.withLock {
        if (!registryFile.exists()) return@withLock
        runCatching {
            val list = json.decodeFromString(
                ListSerializer(ModelInfo.serializer()),
                registryFile.readText(),
            )
            // 実体が消えているエントリは除外
            _models.value = list.filter { File(it.path).exists() }
        }.onFailure { Log.e(TAG, "models.json の読み込みに失敗", it) }
    }

    private fun saveRegistryLocked() {
        runCatching {
            registryFile.writeText(
                json.encodeToString(ListSerializer(ModelInfo.serializer()), _models.value)
            )
        }.onFailure { Log.e(TAG, "models.json の保存に失敗", it) }
    }

    /** アプリ再起動後などに、完了済みダウンロードを登録に取り込む。 */
    private suspend fun adoptCompletedDownloads() {
        val statuses = queryDownloads()
        for (st in statuses) {
            if (st.status == DownloadManager.STATUS_SUCCESSFUL) {
                val file = downloadedFile(st)
                if (file != null && file.exists() && ModelInfo.isSupported(file.name)) {
                    register(file)
                }
            }
        }
    }

    /** SAF インポート先ディレクトリにある未登録ファイルを自動登録する。 */
    private suspend fun scanOrphanFiles() {
        val known = _models.value.map { it.path }.toSet()
        internalModelsDir.listFiles().orEmpty()
            .filter { it.isFile && ModelInfo.isSupported(it.name) && it.absolutePath !in known }
            .forEach { register(it) }
    }

    private fun startPollingIfNeeded() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                val statuses = queryDownloads()
                val active = statuses.filter {
                    it.status == DownloadManager.STATUS_PENDING ||
                        it.status == DownloadManager.STATUS_RUNNING ||
                        it.status == DownloadManager.STATUS_PAUSED
                }
                val failed = statuses.filter { it.status == DownloadManager.STATUS_FAILED }
                _downloads.value = active + failed

                statuses.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }
                    .forEach { st ->
                        val file = downloadedFile(st)
                        if (file != null && file.exists() && ModelInfo.isSupported(file.name)) {
                            register(file)
                        }
                    }
                if (active.isEmpty()) break
                delay(1000)
            }
        }
    }

    private fun queryDownloads(): List<DownloadStatus> {
        val result = mutableListOf<DownloadStatus>()
        runCatching {
            downloadManager.query(DownloadManager.Query()).use { cursor ->
                while (cursor.moveToNext()) {
                    result += DownloadStatus(
                        downloadId = cursor.longOf(DownloadManager.COLUMN_ID),
                        fileName = (cursor.stringOf(DownloadManager.COLUMN_TITLE) ?: "model"),
                        status = cursor.longOf(DownloadManager.COLUMN_STATUS).toInt(),
                        downloadedBytes =
                            cursor.longOf(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        totalBytes = cursor.longOf(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                        reason = cursor.longOf(DownloadManager.COLUMN_REASON).toInt(),
                    )
                }
            }
        }.onFailure { Log.e(TAG, "DownloadManager の照会に失敗", it) }
        return result
    }

    private fun downloadedFile(st: DownloadStatus): File? {
        val uriString = runCatching {
            downloadManager.getUriForDownloadedFile(st.downloadId)?.toString()
        }.getOrNull()
        // DownloadManager はローカルURIを返すことも content:// を返すこともあるため、
        // 保存先ディレクトリからファイル名で解決する。
        val dir = File(context.getExternalFilesDir(null), "models")
        val byTitle = File(dir, st.fileName)
        if (byTitle.exists()) return byTitle
        if (uriString != null && uriString.startsWith("file://")) {
            return File(Uri.parse(uriString).path ?: return null)
        }
        return null
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        return name to size
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        var index = 1
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$index.$ext")
            index++
        }
        return candidate
    }

    private fun Cursor.longOf(column: String): Long {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getLong(idx) else -1L
    }

    private fun Cursor.stringOf(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getString(idx) else null
    }

    companion object {
        private const val TAG = "ModelRepository"
    }
}
