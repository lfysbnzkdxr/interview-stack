package com.example.interviewhelper.ui.create

import com.example.interviewhelper.MainDispatcherExtension
import com.example.interviewhelper.data.model.LlmResult
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CreateViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private lateinit var llmService: LlmService
    private lateinit var questionRepository: QuestionRepository
    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setUp() {
        llmService = mockk()
        questionRepository = mockk()
        settingsRepository = mockk()
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python", "未分类")
    }

    private fun createViewModel() = CreateViewModel(llmService, questionRepository, settingsRepository)

    @Test
    fun `初始化加载分类`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()

        assertEquals(listOf("Agent 智能体", "Python", "未分类"), viewModel.uiState.value.categories)
    }

    @Test
    fun `生成时问题为空提示错误且不调用 LLM`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.setCategory("Agent 智能体")

        viewModel.handleGenerate()

        assertEquals("请输入面试问题", viewModel.uiState.value.error)
        coVerify(exactly = 0) { llmService.generateQA(any()) }
    }

    @Test
    fun `生成时未选分类提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")

        viewModel.handleGenerate()

        assertEquals("请选择分类", viewModel.uiState.value.error)
        coVerify(exactly = 0) { llmService.generateQA(any()) }
    }

    @Test
    fun `生成成功填充预览`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { llmService.generateQA("什么是协程？") } returns Result.success(
            LlmResult(optimizedQuestion = "优化后的问题", dialog = "生成的答案", difficulty = "高级")
        )
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")

        viewModel.handleGenerate()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals("优化后的问题", viewModel.uiState.value.preview?.optimizedQuestion)
        assertEquals("生成的答案", viewModel.uiState.value.preview?.dialog)
        assertEquals("高级", viewModel.uiState.value.preview?.difficulty)
    }

    @Test
    fun `生成成功时未提供字段使用默认值`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { llmService.generateQA("什么是协程？") } returns Result.success(
            LlmResult(dialog = "答案")
        )
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")

        viewModel.handleGenerate()

        assertEquals("什么是协程？", viewModel.uiState.value.preview?.optimizedQuestion)
        assertEquals("中级", viewModel.uiState.value.preview?.difficulty)
    }

    @Test
    fun `生成失败设置错误信息`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { llmService.generateQA(any()) } returns Result.failure(Exception("LLM 服务超时"))
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")

        viewModel.handleGenerate()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals("LLM 服务超时", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.preview)
    }

    @Test
    fun `优化时答案为空提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")

        viewModel.handleOptimize()

        assertEquals("请输入答案要点", viewModel.uiState.value.error)
        coVerify(exactly = 0) { llmService.optimizeQA(any(), any()) }
    }

    @Test
    fun `手动保存时问题为空提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.setCategory("Agent 智能体")

        viewModel.handleManualSave()

        assertEquals("请输入面试问题", viewModel.uiState.value.error)
    }

    @Test
    fun `手动保存生成预览`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setAnswer("协程是一种轻量级线程")
        viewModel.setCategory("Agent 智能体")

        viewModel.handleManualSave()

        val preview = viewModel.uiState.value.preview
        assertEquals("什么是协程？", preview?.optimizedQuestion)
        assertTrue(preview?.dialog?.contains("**Q**：什么是协程？") == true)
        assertTrue(preview?.dialog?.contains("**A**：协程是一种轻量级线程") == true)
        assertEquals("中级", preview?.difficulty)
        assertTrue(preview?.isManual == true)
    }

    @Test
    fun `保存题目成功重置表单并调用仓库`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { questionRepository.addQuestion(any(), any(), any(), any(), any()) } returns Unit
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")
        viewModel.handleManualSave()

        viewModel.handleSave()

        coVerify {
            questionRepository.addQuestion(
                question = "什么是协程？",
                dialog = any(),
                category = "Agent 智能体",
                difficulty = "中级",
                source = "manual"
            )
        }
        assertTrue(viewModel.uiState.value.saveSuccess)
        assertEquals("", viewModel.uiState.value.question)
        assertEquals("", viewModel.uiState.value.answer)
        assertNull(viewModel.uiState.value.preview)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `保存失败设置错误信息`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { questionRepository.addQuestion(any(), any(), any(), any(), any()) } throws
            RuntimeException("磁盘已满")
        val viewModel = createViewModel()
        viewModel.setQuestion("什么是协程？")
        viewModel.setCategory("Agent 智能体")
        viewModel.handleManualSave()

        viewModel.handleSave()

        assertTrue(viewModel.uiState.value.error?.contains("保存失败") == true)
        assertFalse(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `无预览时保存不执行`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()

        viewModel.handleSave()

        coVerify(exactly = 0) { questionRepository.addQuestion(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `确认新分类插入并选中`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.saveCategories(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.setNewCategoryName("Kotlin")
        viewModel.confirmNewCategory()

        coVerify {
            settingsRepository.saveCategories(listOf("Agent 智能体", "Python", "Kotlin", "未分类"))
        }
        assertEquals("Kotlin", viewModel.uiState.value.selectedCategory)
        assertFalse(viewModel.uiState.value.showNewCategory)
        assertEquals(listOf("Agent 智能体", "Python", "Kotlin", "未分类"), viewModel.uiState.value.categories)
    }

    @Test
    fun `空白新分类名不执行保存`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel()

        viewModel.setNewCategoryName("   ")
        viewModel.confirmNewCategory()

        coVerify(exactly = 0) { settingsRepository.saveCategories(any()) }
        // showNewCategory 初始为 false，空白名时不改变任何状态
        assertFalse(viewModel.uiState.value.showNewCategory)
        assertEquals("   ", viewModel.uiState.value.newCategoryName)
    }
}
