package com.example.interviewhelper.data.local

import android.content.Context
import com.example.interviewhelper.data.model.ApiConfig
import com.example.interviewhelper.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val questionDao: QuestionDao,
    private val settingsDao: SettingsDao,
    private val json: Json,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_API_CONFIG = "apiConfig"
        val DEFAULT_CATEGORIES = listOf("Agent 智能体", "RAG 检索增强", "LLM 大模型", "Python", "未分类")
    }

    fun initialize() {
        applicationScope.launch {
            initializeSync()
        }
    }

    /**
     * 同步执行种子数据初始化（供 resetToDefault 等场景在调用方协程中直接调用）
     */
    suspend fun initializeSync() {
        try {
            val initialized = settingsDao.get(KEY_INITIALIZED)
            if (initialized == "true") return

            // 写入默认分类
            settingsDao.put(
                SettingsEntity(
                    key = KEY_CATEGORIES,
                    value = json.encodeToString(DEFAULT_CATEGORIES)
                )
            )

            // 写入默认 API 配置（仅当不存在时，避免覆盖用户已配置的 LLM 提供商）
            if (settingsDao.get(KEY_API_CONFIG) == null) {
                settingsDao.put(
                    SettingsEntity(
                        key = KEY_API_CONFIG,
                        value = json.encodeToString(ApiConfig())
                    )
                )
            }

            // 导入种子题目
            val seedJson = context.assets.open("seed-questions.json")
                .bufferedReader()
                .use { it.readText() }

            val questions = json.decodeFromString<List<QuestionEntity>>(seedJson)
            questionDao.insertAll(questions)

            // 标记已初始化
            settingsDao.put(SettingsEntity(key = KEY_INITIALIZED, value = "true"))
        } catch (e: Exception) {
            // 初始化失败不崩溃，下次启动会重试
            android.util.Log.e("SeedDataInitializer", "Seed data initialization failed", e)
        }
    }
}
