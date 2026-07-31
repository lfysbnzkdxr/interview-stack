package com.example.interviewhelper.ui.bank

import androidx.paging.PagingData
import com.example.interviewhelper.MainDispatcherExtension
import com.example.interviewhelper.data.local.CategoryCount
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.model.LlmResult
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class BankViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private lateinit var questionRepository: QuestionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var llmService: LlmService

    @BeforeEach
    fun setUp() {
        questionRepository = mockk()
        settingsRepository = mockk()
        llmService = mockk()
    }

    private fun question(id: String = "q1") = QuestionEntity(
        id = id,
        category = "Agent 智能体",
        question = "什么是 AI Agent？",
        dialog = "**Q**：什么是 AI Agent？\n\n**A**：智能系统。",
        difficulty = "初级"
    )

    private fun stubEmptyData() {
        coEvery { settingsRepository.getCategories() } returns emptyList()
        coEvery { questionRepository.getQuestionCount() } returns flowOf(0)
        coEvery { questionRepository.getCategoryCounts(any()) } returns flowOf(emptyList())
        every { questionRepository.getPagedQuestions(any(), any(), any(), any()) } returns flowOf(PagingData.empty<QuestionEntity>())
    }

    private fun createViewModel() = BankViewModel(questionRepository, settingsRepository, llmService)

    @Test
    fun `加载时统计题目数量和分类`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python", "未分类")
        coEvery { questionRepository.getQuestionCount() } returns flowOf(2)
        coEvery { questionRepository.getCategoryCounts(false) } returns flowOf(
            listOf(CategoryCount("Agent 智能体", 1), CategoryCount("Python", 1))
        )
        every { questionRepository.getPagedQuestions(any(), any(), any(), any()) } returns flowOf(PagingData.empty<QuestionEntity>())

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals(2, viewModel.uiState.value.totalCount)
        assertEquals(listOf("Agent 智能体", "Python", "未分类"), viewModel.uiState.value.categories)
        assertEquals(mapOf("Agent 智能体" to 1, "Python" to 1), viewModel.uiState.value.categoryCounts)
    }

    @Test
    fun `搜索词触发分页查询`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()
        val collectJob = launch(mainDispatcher.testDispatcher) { viewModel.pagedQuestions.collect() }
        advanceUntilIdle()

        viewModel.setSearchQuery("RAG")
        advanceUntilIdle()

        collectJob.cancel()
        verify { questionRepository.getPagedQuestions(null, null, "RAG") }
    }

    @Test
    fun `分类筛选触发分页查询`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()
        val collectJob = launch(mainDispatcher.testDispatcher) { viewModel.pagedQuestions.collect() }
        advanceUntilIdle()

        viewModel.setFilterCategory("Agent 智能体")
        advanceUntilIdle()

        collectJob.cancel()
        verify { questionRepository.getPagedQuestions("Agent 智能体", null, null, false) }
    }

    @Test
    fun `难度筛选触发分页查询`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()
        val collectJob = launch(mainDispatcher.testDispatcher) { viewModel.pagedQuestions.collect() }
        advanceUntilIdle()

        viewModel.setFilterDifficulty("高级")
        advanceUntilIdle()

        collectJob.cancel()
        verify { questionRepository.getPagedQuestions(null, "高级", null, false) }
    }

    @Test
    fun `选择题目后批量删除`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        coEvery { questionRepository.batchDelete(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.toggleSelect("q1")
        viewModel.toggleSelect("q2")
        assertEquals(setOf("q1", "q2"), viewModel.uiState.value.selectedIds)

        viewModel.batchDelete()
        advanceUntilIdle()

        coVerify { questionRepository.batchDelete(listOf("q1", "q2")) }
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun `取消选择清空选中集合`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()

        viewModel.toggleSelect("q1")
        viewModel.toggleSelect("q1")

        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun `对话为空时 AI 润色报错`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()

        viewModel.startEdit(question("q1").copy(dialog = ""))
        viewModel.handleAiPolish()

        assertEquals("对话内容为空，无法润色", viewModel.uiState.value.aiError)
    }

    @Test
    fun `AI 润色成功后可采用润色版`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        coEvery { llmService.polishDialog(any(), any()) } returns
            Result.success(LlmResult(dialog = "润色后的对话"))
        val viewModel = createViewModel()

        viewModel.startEdit(question("q1"))
        viewModel.handleAiPolish()
        advanceUntilIdle()

        assertEquals("润色后的对话", viewModel.uiState.value.aiPolishResult)
        viewModel.acceptPolish()
        assertEquals("润色后的对话", viewModel.uiState.value.editForm.dialog)
    }

    @Test
    fun `保留原版清空润色结果`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        val viewModel = createViewModel()

        viewModel.startEdit(question("q1"))
        viewModel.rejectPolish()

        assertNull(viewModel.uiState.value.aiPolishResult)
    }

    @Test
    fun `编辑保存调用更新并清空编辑状态`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        coEvery { questionRepository.getQuestionById("q1") } returns question("q1")
        coEvery { questionRepository.updateQuestion(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.startEdit(question("q1"))
        viewModel.updateEditForm(viewModel.uiState.value.editForm.copy(question = "新问题"))
        viewModel.saveEdit()
        advanceUntilIdle()

        coVerify { questionRepository.updateQuestion(match { it.question == "新问题" }) }
        assertNull(viewModel.uiState.value.editId)
    }

    @Test
    fun `切换仅看可见开关更新统计并触发可见分页`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        coEvery { questionRepository.getVisibleCount() } returns flowOf(1)
        coEvery { questionRepository.getCategoryCounts(true) } returns flowOf(
            listOf(CategoryCount("Agent 智能体", 1))
        )
        val viewModel = createViewModel()
        val collectJob = launch(mainDispatcher.testDispatcher) { viewModel.pagedQuestions.collect() }
        advanceUntilIdle()

        viewModel.setVisibleOnly(true)
        advanceUntilIdle()

        collectJob.cancel()
        assertTrue(viewModel.uiState.value.visibleOnly)
        assertEquals(1, viewModel.uiState.value.totalCount)
        assertEquals(mapOf("Agent 智能体" to 1), viewModel.uiState.value.categoryCounts)
        verify { questionRepository.getPagedQuestions(null, null, null, true) }
    }

    @Test
    fun `关闭仅看可见开关恢复全部统计`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        stubEmptyData()
        coEvery { questionRepository.getVisibleCount() } returns flowOf(1)
        coEvery { questionRepository.getCategoryCounts(true) } returns flowOf(emptyList())
        val viewModel = createViewModel()
        val collectJob = launch(mainDispatcher.testDispatcher) { viewModel.pagedQuestions.collect() }
        advanceUntilIdle()

        viewModel.setVisibleOnly(true)
        advanceUntilIdle()
        viewModel.setVisibleOnly(false)
        advanceUntilIdle()

        collectJob.cancel()
        assertFalse(viewModel.uiState.value.visibleOnly)
        assertEquals(0, viewModel.uiState.value.totalCount)
        verify { questionRepository.getPagedQuestions(null, null, null, false) }
    }
}
