package com.example.interviewhelper.data.model

import kotlinx.serialization.Serializable

/**
 * API 配置 - 管理多个 LLM 提供商
 */
@Serializable
data class ApiConfig(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null
)

/**
 * LLM 提供商配置
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val isActive: Boolean = false
) {
    companion object {
        /** 预设提供商列表 */
        val PRESETS = listOf(
            ProviderPreset(
                name = "DeepSeek",
                baseUrl = "https://api.deepseek.com/v1",
                models = listOf("deepseek-chat", "deepseek-reasoner")
            ),
            ProviderPreset(
                name = "GLM (智谱)",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                models = listOf("glm-4", "glm-4-flash")
            ),
            ProviderPreset(
                name = "Kimi (月之暗面)",
                baseUrl = "https://api.moonshot.cn/v1",
                models = listOf("moonshot-v1-8k", "moonshot-v1-32k")
            ),
            ProviderPreset(
                name = "通义千问",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                models = listOf("qwen-turbo", "qwen-plus", "qwen-max")
            ),
            ProviderPreset(
                name = "MiMo (小米)",
                baseUrl = "https://api.mimo.xiaomi.com/v1",
                models = listOf("mimo-7b-rl")
            )
        )
    }
}

/**
 * 预设提供商信息
 */
@Serializable
data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val models: List<String>
)

/**
 * LLM 调用结果
 */
@Serializable
data class LlmResult(
    val optimizedQuestion: String? = null,
    val dialog: String,
    val difficulty: String? = null
)

/**
 * WebDAV 配置
 */
@Serializable
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/interview-backups/"
)

/**
 * 数据导入模式
 */
enum class ImportMode {
    OVERWRITE,  // 覆盖现有数据
    MERGE       // 合并（追加新数据）
}

/**
 * WebDAV 文件信息
 */
data class WebDavFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)
