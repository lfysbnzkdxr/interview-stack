package com.example.interviewhelper.data.remote.llm

import com.example.interviewhelper.data.model.LlmResult
import com.example.interviewhelper.data.model.ProviderConfig
import com.example.interviewhelper.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val json: Json
) {
    companion object {
        private const val GENERATE_TIMEOUT = 60L
        private const val TEST_TIMEOUT = 15L
        private const val MAX_RETRIES = 1
        private const val RETRY_DELAY = 2000L
    }

    suspend fun getActiveProvider(): ProviderConfig? {
        val config = settingsRepository.getApiConfig()
        return config.providers.find { it.id == config.activeProviderId }
            ?: config.providers.find { it.isActive }
            ?: config.providers.firstOrNull()
    }

    suspend fun optimizeQA(question: String, answer: String): Result<LlmResult> {
        val prompt = """你是一位AI面试辅导专家。请根据以下面试问题和答案要点，生成一份高质量的面试问答对话。

面试问题：$question
答案要点：$answer

请以 JSON 格式返回：
{"optimized_question": "优化后的问题标题", "dialog": "**Q**：问题\n\n**A**：详细回答（使用Markdown格式，包含要点、代码示例等）", "difficulty": "初级/中级/高级"}

注意：dialog 字段中使用 **Q**：和 **A**：格式，回答要全面、有条理。"""
        return callLlm(prompt)
    }

    suspend fun generateQA(question: String): Result<LlmResult> {
        val prompt = """你是一位AI面试辅导专家。请为以下面试问题生成一份全面、深入的问答对话。

面试问题：$question

请以 JSON 格式返回：
{"optimized_question": "优化后的问题标题", "dialog": "**Q**：问题\n\n**A**：详细回答（使用Markdown格式，包含要点、代码示例等）", "difficulty": "初级/中级/高级"}

注意：dialog 字段中使用 **Q**：和 **A**：格式，回答要全面、有条理、有深度。"""
        return callLlm(prompt)
    }

    suspend fun polishDialog(question: String, dialog: String): Result<LlmResult> {
        val prompt = """你是一位AI面试辅导专家。请润色以下面试问答对话，使其更加专业、全面、有条理。

面试问题：$question
当前对话内容：
$dialog

请以 JSON 格式返回：
{"optimized_question": "问题标题（可微调）", "dialog": "润色后的完整对话（保持 **Q**：和 **A**：格式）", "difficulty": "初级/中级/高级"}

注意：保持原有内容结构，优化表达、补充遗漏、修正错误。"""
        return callLlm(prompt)
    }

    suspend fun appendSubQA(question: String, dialog: String, subQuestion: String): Result<LlmResult> {
        val prompt = """你是一位AI面试辅导专家。在以下已有对话基础上，追加一个子问题的问答。

主题：$question
已有对话：
$dialog

需要追加的子问题：$subQuestion

请以 JSON 格式返回：
{"optimized_question": "$question", "dialog": "完整的对话内容（包含原有内容 + 新增的子问题问答，使用 **Q**：和 **A**：格式）", "difficulty": "初级/中级/高级"}

注意：保留原有对话内容不变，在末尾追加新的 Q&A。"""
        return callLlm(prompt)
    }

    suspend fun testConnection(provider: ProviderConfig): Result<Boolean> {
        return try {
            val client = okHttpClient.newBuilder()
                .connectTimeout(TEST_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TEST_TIMEOUT, TimeUnit.SECONDS)
                .build()

            val requestBody = buildJsonObject {
                put("model", provider.model)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "Hi")
                    })
                }
                put("max_tokens", 5)
            }

            val request = Request.Builder()
                .url("${provider.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${provider.apiKey}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> Result.success(true)
                        response.code == 401 -> Result.failure(Exception("API Key 无效 (401)"))
                        else -> Result.failure(Exception("连接失败: HTTP ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    private suspend fun callLlm(prompt: String): Result<LlmResult> {
        val provider = getActiveProvider()
            ?: return Result.failure(Exception("未配置 AI 提供商，请先在设置中添加"))

        if (provider.apiKey.isBlank()) {
            return Result.failure(Exception("API Key 为空，请先在设置中配置"))
        }

        var lastError: Throwable? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                val result = doCall(provider, prompt)
                // HTTP 5xx / 429 属于临时性错误，可重试；其余失败（4xx 等）直接返回
                if (result.isSuccess || !isRetryableHttpError(result)) {
                    return result
                }
                lastError = result.exceptionOrNull()
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY)
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY)
                }
            }
        }
        return Result.failure(lastError ?: Exception("未知错误"))
    }

    /**
     * 判断失败是否为可重试的 HTTP 错误（5xx 服务端错误或 429 限流）
     */
    private fun isRetryableHttpError(result: Result<LlmResult>): Boolean {
        val message = result.exceptionOrNull()?.message ?: return false
        val code = Regex("""\((\d{3})\)""").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        return code == 429 || code >= 500
    }

    private suspend fun doCall(provider: ProviderConfig, prompt: String): Result<LlmResult> =
        withContext(Dispatchers.IO) {
            val client = okHttpClient.newBuilder()
                .readTimeout(GENERATE_TIMEOUT, TimeUnit.SECONDS)
                .build()

            val requestBody = buildJsonObject {
                put("model", provider.model)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", "你是一位专业的AI面试辅导助手，擅长生成高质量的面试问答内容。始终以有效JSON格式回复。")
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url("${provider.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${provider.apiKey}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(Exception("API 请求失败 (${response.code})"))
                } else {
                    val body = response.body?.string()
                    if (body == null) {
                        Result.failure(Exception("空响应"))
                    } else {
                        val responseJson = json.parseToJsonElement(body).jsonObject
                        val content = responseJson["choices"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("message")?.jsonObject?.get("content")
                            ?.jsonPrimitive?.contentOrNull
                        if (content == null) {
                            Result.failure(Exception("响应格式错误"))
                        } else {
                            extractJson(content)
                        }
                    }
                }
            }
        }

    /**
     * 三步 JSON 提取策略
     */
    private fun extractJson(content: String): Result<LlmResult> {
        // 策略1：直接解析
        tryParse(content)?.let { return it }

        // 策略2：提取 markdown 代码块
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        codeBlockRegex.find(content)?.groupValues?.get(1)?.trim()?.let { block ->
            tryParse(block)?.let { return it }
        }

        // 策略3：首尾大括号
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            tryParse(content.substring(firstBrace, lastBrace + 1))?.let { return it }
        }

        return Result.failure(Exception("无法解析 AI 返回的内容"))
    }

    private fun tryParse(text: String): Result<LlmResult>? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val dialog = obj["dialog"]?.jsonPrimitive?.contentOrNull ?: return null
            val optimizedQuestion = obj["optimized_question"]?.jsonPrimitive?.contentOrNull
            val difficulty = obj["difficulty"]?.jsonPrimitive?.contentOrNull
            Result.success(
                LlmResult(
                    optimizedQuestion = optimizedQuestion,
                    dialog = dialog,
                    difficulty = difficulty
                )
            )
        } catch (e: Exception) {
            null
        }
    }
}
