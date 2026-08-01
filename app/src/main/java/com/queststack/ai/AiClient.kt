package com.queststack.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** OpenAI 兼容聊天消息 */
data class ChatMessage(val role: String, val content: String)

/**
 * OpenAI 兼容 API 客户端。所有方法运行在 IO 调度器且不捕获异常，
 * 失败时抛 [IOException]/[IllegalArgumentException]/[TimeoutCancellationException]，由调用方（ViewModel）处理。
 */
class AiClient(private val okHttpClient: OkHttpClient) {

    /** 普通对话：返回 choices[0].message.content 文本 */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ): String {
        val responseBody = execute(buildRequest(chatCompletionsUrl(baseUrl), apiKey, chatRequestBody(model, messages)), timeoutSeconds)
        val content = json.parseToJsonElement(responseBody).jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return content ?: throw IllegalStateException("AI 响应中缺少 choices[0].message.content")
    }

    /** 生成追问链：返回（question, answer）轮次列表，至少 1 轮 */
    suspend fun generateQuestionChain(
        baseUrl: String,
        apiKey: String,
        model: String,
        title: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ): List<Pair<String, String>> {
        val system = "你是一个面试官。根据给定问题生成追问链：第一轮是问题本身（question=原问题，answer=详细参考答案），" +
            "后续 2-3 轮为追问与参考答案。只输出 JSON，格式：{\"rounds\":[{\"question\":\"...\",\"answer\":\"...\"}]}"
        val body = chatRequestBody(
            model,
            listOf(ChatMessage("system", system), ChatMessage("user", title))
        )
        return parseRounds(execute(buildRequest(chatCompletionsUrl(baseUrl), apiKey, body), timeoutSeconds))
    }

    /** 润色回答：返回润色后的文本 */
    suspend fun optimizeAnswer(
        baseUrl: String,
        apiKey: String,
        model: String,
        title: String,
        answer: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ): String {
        val system = "你是面试辅导专家。润色以下回答，使其更有条理、更专业、更适合面试口述。" +
            "直接输出润色后的回答文本，不要加任何前缀说明。"
        val body = chatRequestBody(
            model,
            listOf(ChatMessage("system", system), ChatMessage("user", "问题：$title\n\n我的回答：$answer"))
        )
        return execute(buildRequest(chatCompletionsUrl(baseUrl), apiKey, body), timeoutSeconds).trim()
    }

    /** 整理自由填写的问答文本为结构化问答链 */
    suspend fun formatAnswer(
        baseUrl: String,
        apiKey: String,
        model: String,
        title: String,
        answer: String,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS
    ): List<Pair<String, String>> {
        val system = "将以下面试问答内容整理为结构化问答链，输出 JSON {\"rounds\":[{\"question\":\"...\",\"answer\":\"...\"}]}，" +
            "第一轮 question 是原问题，后续轮次是合理追问及参考答案；若内容无法拆分，也至少输出一轮。"
        val body = chatRequestBody(
            model,
            listOf(ChatMessage("system", system), ChatMessage("user", "问题：$title\n\n内容：$answer"))
        )
        return parseRounds(execute(buildRequest(chatCompletionsUrl(baseUrl), apiKey, body), timeoutSeconds))
    }

    /** 构建 {baseUrl}/v1/chat/completions，baseUrl 已含 /v1 则不重复加 */
    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1")) "$trimmed/chat/completions" else "$trimmed/v1/chat/completions"
    }

    private fun chatRequestBody(model: String, messages: List<ChatMessage>): String =
        buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages.map { message ->
                buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                }
            }))
            put("temperature", 0.7)
        }.toString()

    private fun buildRequest(url: String, apiKey: String, body: String): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        return builder.build()
    }

    /** 发送请求并返回响应体；HTTP 非 2xx 抛 IOException；超时抛 TimeoutCancellationException */
    private suspend fun execute(request: Request, timeoutSeconds: Int): String =
        withTimeout(timeoutSeconds * 1000L) {
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: $responseBody")
                    }
                    responseBody
                }
            }
        }

    /** 提取响应体中第一个 { 到最后一个 } 的 JSON，解析为 rounds 列表（至少 1 轮，否则抛 IllegalArgumentException） */
    private fun parseRounds(responseBody: String): List<Pair<String, String>> {
        val start = responseBody.indexOf('{')
        val end = responseBody.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalArgumentException("AI 返回内容不是有效 JSON")
        }
        val rounds = json.decodeFromJsonElement<RoundsResponse>(
            json.parseToJsonElement(responseBody.substring(start, end + 1))
        ).rounds
        if (rounds.isEmpty()) {
            throw IllegalArgumentException("AI 返回的问答链为空")
        }
        return rounds.map { it.question to it.answer }
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30

        private val json = Json { ignoreUnknownKeys = true }
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
private data class RoundsResponse(val rounds: List<RoundItem>)

@Serializable
private data class RoundItem(val question: String, val answer: String)
