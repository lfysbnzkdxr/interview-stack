package com.example.interviewhelper.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.local.SeedDataInitializer
import com.example.interviewhelper.data.local.SettingsDao
import com.example.interviewhelper.data.local.SettingsEntity
import com.example.interviewhelper.data.model.ApiConfig
import com.example.interviewhelper.data.model.ImportMode
import com.example.interviewhelper.data.model.WebDavConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<String>,
    val questions: List<QuestionEntity>
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: SettingsDao,
    private val questionRepository: QuestionRepository,
    private val seedDataInitializer: SeedDataInitializer,
    private val json: Json
) {
    companion object {
        private const val KEY_API_CONFIG = "apiConfig"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_WEBDAV_CONFIG = "webDavConfig"
        private const val KEY_INITIALIZED = "initialized"
        private const val ESP_KEY_API_KEYS = "api_keys"
        private const val ESP_KEY_WEBDAV_PASSWORD = "webdav_password"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "interview_helper_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ========== API Config ==========

    suspend fun getApiConfig(): ApiConfig {
        val jsonStr = settingsDao.get(KEY_API_CONFIG) ?: return ApiConfig()
        val config = try {
            json.decodeFromString<ApiConfig>(jsonStr)
        } catch (e: Exception) {
            ApiConfig()
        }
        // 从 EncryptedSharedPreferences 注入 API Keys
        val keysJson = encryptedPrefs.getString(ESP_KEY_API_KEYS, "{}") ?: "{}"
        val keysMap = try {
            json.decodeFromString<Map<String, String>>(keysJson)
        } catch (e: Exception) {
            emptyMap()
        }
        return config.copy(
            providers = config.providers.map { provider ->
                provider.copy(apiKey = keysMap[provider.id] ?: "")
            }
        )
    }

    suspend fun saveApiConfig(config: ApiConfig) {
        // API Key 单独存储到 EncryptedSharedPreferences
        val keysMap = config.providers.associate { it.id to it.apiKey }
        encryptedPrefs.edit()
            .putString(ESP_KEY_API_KEYS, json.encodeToString(keysMap))
            .apply()

        // 其余字段存入 Room（不含 apiKey）
        val sanitizedConfig = config.copy(
            providers = config.providers.map { it.copy(apiKey = "") }
        )
        settingsDao.put(
            SettingsEntity(key = KEY_API_CONFIG, value = json.encodeToString(sanitizedConfig))
        )
    }

    // ========== WebDAV Config ==========

    suspend fun getWebDavConfig(): WebDavConfig {
        val jsonStr = settingsDao.get(KEY_WEBDAV_CONFIG) ?: return WebDavConfig()
        val config = try {
            json.decodeFromString<WebDavConfig>(jsonStr)
        } catch (e: Exception) {
            WebDavConfig()
        }
        // 从 ESP 注入密码
        val password = encryptedPrefs.getString(ESP_KEY_WEBDAV_PASSWORD, "") ?: ""
        return config.copy(password = password)
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) {
        // 密码存入 ESP
        encryptedPrefs.edit()
            .putString(ESP_KEY_WEBDAV_PASSWORD, config.password)
            .apply()

        // 其余存入 Room
        val sanitized = config.copy(password = "")
        settingsDao.put(
            SettingsEntity(key = KEY_WEBDAV_CONFIG, value = json.encodeToString(sanitized))
        )
    }

    // ========== Categories ==========

    suspend fun getCategories(): List<String> {
        val jsonStr = settingsDao.get(KEY_CATEGORIES) ?: return SeedDataInitializer.DEFAULT_CATEGORIES
        return try {
            json.decodeFromString<List<String>>(jsonStr)
        } catch (e: Exception) {
            SeedDataInitializer.DEFAULT_CATEGORIES
        }
    }

    fun getCategoriesFlow(): Flow<List<String>> {
        return settingsDao.getFlow(KEY_CATEGORIES).map { jsonStr ->
            if (jsonStr == null) SeedDataInitializer.DEFAULT_CATEGORIES
            else try {
                json.decodeFromString<List<String>>(jsonStr)
            } catch (e: Exception) {
                SeedDataInitializer.DEFAULT_CATEGORIES
            }
        }
    }

    suspend fun saveCategories(categories: List<String>) {
        settingsDao.put(
            SettingsEntity(key = KEY_CATEGORIES, value = json.encodeToString(categories))
        )
    }

    // ========== Export / Import ==========

    suspend fun exportAllData(): String {
        val categories = getCategories()
        val questions = questionRepository.getAllQuestions().first().toMutableList()
        val exportData = ExportData(
            categories = categories,
            questions = questions.map { it.copy() } // 确保不含敏感信息
        )
        return json.encodeToString(exportData)
    }

    suspend fun importData(jsonStr: String, mode: ImportMode): Result<Int> {
        return try {
            val importData = json.decodeFromString<ExportData>(jsonStr)

            when (mode) {
                ImportMode.OVERWRITE -> {
                    questionRepository.replaceAll(importData.questions)
                    saveCategories(importData.categories)
                }
                ImportMode.MERGE -> {
                    // 合并模式：跳过已存在的 id
                    val allQuestions = questionRepository.getAllQuestions().first()
                    val existingIds = allQuestions.map { it.id }.toSet()
                    val newQuestions = importData.questions.filter { it.id !in existingIds }
                    questionRepository.insertAll(newQuestions)

                    // 合并分类（去重）
                    val currentCategories = getCategories().toMutableList()
                    importData.categories.forEach { cat ->
                        if (cat !in currentCategories) currentCategories.add(cat)
                    }
                    saveCategories(currentCategories)
                }
            }
            Result.success(importData.questions.size)
        } catch (e: Exception) {
            Result.failure(Exception("数据格式无效: ${e.message}"))
        }
    }

    suspend fun resetToDefault() {
        questionRepository.deleteAll()
        settingsDao.delete(KEY_INITIALIZED)
        settingsDao.delete(KEY_CATEGORIES)
        // 重新触发种子导入（立即同步执行）
        seedDataInitializer.initializeSync()
    }
}
