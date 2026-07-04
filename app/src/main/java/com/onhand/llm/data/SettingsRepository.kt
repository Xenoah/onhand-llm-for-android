package com.onhand.llm.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onhand.llm.engine.EngineBackend
import com.onhand.llm.engine.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** アプリ設定 */
data class AppSettings(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val backend: EngineBackend = EngineBackend.CPU,
    val serverPort: Int = 8080,
    val hfToken: String = "",
    val activeModelId: String = "",
    val autoLoadLastModel: Boolean = true,
) {
    fun toEngineConfig() = EngineConfig(
        maxTokens = maxTokens,
        temperature = temperature,
        topK = topK,
        topP = topP,
        backend = backend,
    )
}

/** DataStore に設定を保存するリポジトリ */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val BACKEND = stringPreferencesKey("backend")
        val SERVER_PORT = intPreferencesKey("server_port")
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val AUTO_LOAD = booleanPreferencesKey("auto_load_last_model")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            temperature = p[Keys.TEMPERATURE] ?: 0.8f,
            topK = p[Keys.TOP_K] ?: 40,
            topP = p[Keys.TOP_P] ?: 0.95f,
            maxTokens = p[Keys.MAX_TOKENS] ?: 1024,
            backend = runCatching {
                EngineBackend.valueOf(p[Keys.BACKEND] ?: "CPU")
            }.getOrDefault(EngineBackend.CPU),
            serverPort = p[Keys.SERVER_PORT] ?: 8080,
            hfToken = p[Keys.HF_TOKEN] ?: "",
            activeModelId = p[Keys.ACTIVE_MODEL_ID] ?: "",
            autoLoadLastModel = p[Keys.AUTO_LOAD] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTemperature(value: Float) =
        context.dataStore.edit { it[Keys.TEMPERATURE] = value }

    suspend fun setTopK(value: Int) = context.dataStore.edit { it[Keys.TOP_K] = value }

    suspend fun setTopP(value: Float) = context.dataStore.edit { it[Keys.TOP_P] = value }

    suspend fun setMaxTokens(value: Int) =
        context.dataStore.edit { it[Keys.MAX_TOKENS] = value }

    suspend fun setBackend(value: EngineBackend) =
        context.dataStore.edit { it[Keys.BACKEND] = value.name }

    suspend fun setServerPort(value: Int) =
        context.dataStore.edit { it[Keys.SERVER_PORT] = value }

    suspend fun setHfToken(value: String) =
        context.dataStore.edit { it[Keys.HF_TOKEN] = value }

    suspend fun setActiveModelId(value: String) =
        context.dataStore.edit { it[Keys.ACTIVE_MODEL_ID] = value }

    suspend fun setAutoLoadLastModel(value: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_LOAD] = value }
}
