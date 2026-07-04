package com.onhand.llm.data

import kotlinx.serialization.Serializable

/** インポート済みモデルのメタ情報。models.json に保存される。 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val format: String,
    val addedAt: Long,
) {
    companion object {
        val SUPPORTED_EXTENSIONS = listOf("task", "litertlm", "bin")

        fun formatOf(fileName: String): String =
            fileName.substringAfterLast('.', "").lowercase()

        fun isSupported(fileName: String): Boolean =
            formatOf(fileName) in SUPPORTED_EXTENSIONS
    }
}

/** ダウンロード中モデルの状態 (DownloadManager から取得) */
data class DownloadStatus(
    val downloadId: Long,
    val fileName: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int,
)

/** おすすめモデル (ワンタップでダウンロードできる定義) */
data class RecommendedModel(
    val name: String,
    val description: String,
    val url: String,
    val approxSize: String,
    val requiresHfToken: Boolean,
)

val RECOMMENDED_MODELS = listOf(
    RecommendedModel(
        name = "Gemma 3 1B (int4)",
        description = "Google の軽量モデル。日本語対応。まずはこれがおすすめ。",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
        approxSize = "約 550MB",
        requiresHfToken = true,
    ),
    RecommendedModel(
        name = "Qwen2.5 1.5B Instruct (q8)",
        description = "Alibaba の多言語モデル。日本語が得意。",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        approxSize = "約 1.6GB",
        requiresHfToken = false,
    ),
    RecommendedModel(
        name = "Phi-4 mini Instruct (q8)",
        description = "Microsoft の高性能小型モデル。RAM 8GB 以上推奨。",
        url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        approxSize = "約 3.9GB",
        requiresHfToken = false,
    ),
)
