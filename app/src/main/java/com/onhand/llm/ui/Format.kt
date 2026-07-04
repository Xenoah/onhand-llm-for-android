package com.onhand.llm.ui

import java.util.Locale

/** バイト数を人間が読みやすい形式にする */
fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "不明"
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
