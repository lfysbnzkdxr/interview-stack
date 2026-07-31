package com.example.interviewhelper.data.repository

import android.content.Context
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.local.SeedDataInitializer
import com.example.interviewhelper.data.local.SettingsDao
import com.example.interviewhelper.data.model.ImportMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var context: Context
    private lateinit var settingsDao: SettingsDao
    private lateinit var questionRepository: QuestionRepository
    private lateinit var seedDataInitializer: SeedDataInitializer
    private lateinit var repository: SettingsRepository

    private val categories = listOf("Agent 智能体", "RAG 检索增强", "LLM 大模型", "Python", "未分类")

    @BeforeEach
    fun setUp() {
        context = mockk()
        settingsDao = mockk()
        questionRepository = mockk()
        seedDataInitializer = mockk()
        repository = SettingsRepository(context, settingsDao, questionRepository, seedDataInitializer, json)
    }

    private fun sampleQuestion(id: String = "q1") = QuestionEntity(
        id = id,
        category = "Agent 智能体",
        question = "什么是 AI Agent？",
        dialog = "**Q**：什么是 AI Agent？\n\n**A**：AI Agent 是一种智能系统。",
        difficulty = "初级"
    )

    @Test
    fun `导出数据返回包含分类和题目的 JSON`() = runTest {
        val questions = listOf(sampleQuestion("q1"), sampleQuestion("q2"))
        coEvery { settingsDao.get("categories") } returns json.encodeToString(categories)
        coEvery { questionRepository.getAllQuestions() } returns flowOf(questions)

        val result = repository.exportAllData()

        val export = json.decodeFromString<ExportData>(result)
        assertEquals(categories, export.categories)
        assertEquals(questions, export.questions)
    }

    @Test
    fun `覆盖导入清空并写入题目和分类`() = runTest {
        val newQuestion = sampleQuestion("q9")
        coEvery { questionRepository.deleteAll() } returns Unit
        coEvery { questionRepository.insertAll(any()) } returns Unit
        coEvery { settingsDao.put(any()) } returns Unit

        val importStr = json.encodeToString(
            ExportData(categories = categories, questions = listOf(newQuestion))
        )

        val result = repository.importData(importStr, ImportMode.OVERWRITE)

        assertTrue(result.isSuccess)
        coVerify { questionRepository.deleteAll() }
        coVerify { questionRepository.insertAll(listOf(newQuestion)) }
        coVerify { settingsDao.put(match { it.value == json.encodeToString(categories) }) }
    }

    @Test
    fun `合并导入跳过已存在的题目`() = runTest {
        val existing = sampleQuestion("q1")
        val newQuestion = sampleQuestion("q3")
        coEvery { questionRepository.getAllQuestions() } returns flowOf(listOf(existing))
        coEvery { questionRepository.insertAll(any()) } returns Unit
        coEvery { settingsDao.get("categories") } returns json.encodeToString(categories)
        coEvery { settingsDao.put(any()) } returns Unit

        val importStr = json.encodeToString(
            ExportData(categories = categories, questions = listOf(existing, newQuestion))
        )

        val result = repository.importData(importStr, ImportMode.MERGE)

        assertTrue(result.isSuccess)
        coVerify { questionRepository.insertAll(match { it.map { q -> q.id } == listOf("q3") }) }
    }

    @Test
    fun `导入非法 JSON 返回失败`() = runTest {
        val result = repository.importData("不是合法 JSON", ImportMode.MERGE)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("数据格式无效") == true)
    }

    @Test
    fun `重置默认题库删除数据并触发种子导入`() = runTest {
        coEvery { questionRepository.deleteAll() } returns Unit
        coEvery { settingsDao.delete(any()) } returns Unit
        coEvery { seedDataInitializer.initializeSync() } returns Unit

        repository.resetToDefault()

        coVerify { questionRepository.deleteAll() }
        coVerify { seedDataInitializer.initializeSync() }
    }

    @Test
    fun `分类为空时返回默认分类`() = runTest {
        coEvery { settingsDao.get("categories") } returns null
        assertEquals(SeedDataInitializer.DEFAULT_CATEGORIES, repository.getCategories())
    }

    @Test
    fun `分类读取失败时回退默认分类`() = runTest {
        coEvery { settingsDao.get("categories") } returns "非法 JSON"
        assertEquals(SeedDataInitializer.DEFAULT_CATEGORIES, repository.getCategories())
    }
}
