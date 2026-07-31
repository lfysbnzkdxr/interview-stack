package com.example.interviewhelper.data.remote.llm

import com.example.interviewhelper.data.model.ApiConfig
import com.example.interviewhelper.data.model.ProviderConfig
import com.example.interviewhelper.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LlmServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var service: LlmService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        settingsRepository = mockk()
        service = LlmService(OkHttpClient(), settingsRepository, Json)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun activeProvider() = ProviderConfig(
        id = "p1",
        name = "DeepSeek",
        baseUrl = server.url("/v1").toString(),
        apiKey = "test-key",
        model = "deepseek-chat",
        isActive = true
    )

    private fun mockActiveProvider() {
        coEvery { settingsRepository.getApiConfig() } returns
            ApiConfig(providers = listOf(activeProvider()), activeProviderId = "p1")
    }

    private fun enqueueChatCompletion(content: String) {
        val body = buildJsonObject {
            putJsonArray("choices") {
                add(buildJsonObject {
                    putJsonObject("message") {
                        put("content", content)
                    }
                })
            }
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(body.toString()))
    }

    @Test
    fun `生成问答解析标准 JSON 响应`() = runTest {
        mockActiveProvider()
        enqueueChatCompletion(
            """{"optimized_question": "Q1", "dialog": "**Q**：问题\n\n**A**：回答", "difficulty": "初级"}"""
        )

        val result = service.generateQA("什么是 RAG？")

        assertTrue(result.isSuccess)
        val llmResult = result.getOrThrow()
        assertEquals("Q1", llmResult.optimizedQuestion)
        assertEquals("**Q**：问题\n\n**A**：回答", llmResult.dialog)
        assertEquals("初级", llmResult.difficulty)
    }

    @Test
    fun `从 markdown 代码块中提取 JSON`() = runTest {
        mockActiveProvider()
        enqueueChatCompletion(
            "```json\n{\"optimized_question\": \"Q2\", \"dialog\": \"**Q**：x\", \"difficulty\": \"中级\"}\n```"
        )

        val result = service.generateQA("问题")

        assertTrue(result.isSuccess)
        assertEquals("Q2", result.getOrThrow().optimizedQuestion)
    }

    @Test
    fun `从前后有杂质的文本中提取 JSON`() = runTest {
        mockActiveProvider()
        enqueueChatCompletion(
            "以下是结果：{\"optimized_question\": \"Q3\", \"dialog\": \"**Q**：y\"} 回答完毕"
        )

        val result = service.generateQA("问题")

        assertTrue(result.isSuccess)
        assertEquals("Q3", result.getOrThrow().optimizedQuestion)
    }

    @Test
    fun `响应缺少 dialog 字段时返回失败`() = runTest {
        mockActiveProvider()
        enqueueChatCompletion("""{"optimized_question": "Q", "difficulty": "初级"}""")

        val result = service.generateQA("问题")

        assertTrue(result.isFailure)
    }

    @Test
    fun `服务器错误返回失败信息`() = runTest {
        mockActiveProvider()
        server.enqueue(MockResponse().setResponseCode(500))

        val result = service.generateQA("问题")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `未配置提供商时返回错误`() = runTest {
        coEvery { settingsRepository.getApiConfig() } returns ApiConfig()

        val result = service.generateQA("问题")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("未配置") == true)
    }

    @Test
    fun `API Key 为空时返回错误`() = runTest {
        coEvery { settingsRepository.getApiConfig() } returns
            ApiConfig(providers = listOf(activeProvider().copy(apiKey = "")), activeProviderId = "p1")

        val result = service.generateQA("问题")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API Key") == true)
    }

    @Test
    fun `连接测试成功`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = service.testConnection(activeProvider())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `连接测试认证失败返回 401 错误`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = service.testConnection(activeProvider())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `优化问答调用返回优化结果`() = runTest {
        mockActiveProvider()
        enqueueChatCompletion(
            """{"optimized_question": "优化后的问题", "dialog": "**Q**：优化后\n\n**A**：详细回答", "difficulty": "高级"}"""
        )

        val result = service.optimizeQA("原始问题", "要点1")

        assertTrue(result.isSuccess)
        assertEquals("优化后的问题", result.getOrThrow().optimizedQuestion)
    }
}
