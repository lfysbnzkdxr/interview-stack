package com.example.interviewhelper.ui.practice

import com.example.interviewhelper.MainDispatcherExtension
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PracticeViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private fun questions() = listOf(
        QuestionEntity(id = "q1", category = "Agent 智能体", question = "Q1", dialog = "A1", difficulty = "初级"),
        QuestionEntity(id = "q2", category = "Python", question = "Q2", dialog = "A2", difficulty = "中级"),
        QuestionEntity(id = "q3", category = "Python", question = "Q3", dialog = "A3", difficulty = "高级")
    )

    private fun createViewModel(visible: List<QuestionEntity>): PracticeViewModel {
        val questionRepository = mockk<QuestionRepository>()
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(visible)
        val settingsRepository = mockk<SettingsRepository>()
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python", "未分类")
        return PracticeViewModel(questionRepository, settingsRepository)
    }

    @Test
    fun `加载后队列包含题目且进度正确`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNull(state.loadError)
        assertEquals(3, state.queue.size)
        assertNotNull(state.currentQuestion)
        assertEquals("1 / 3", state.progress)
        assertTrue(state.isFirst)
        assertFalse(state.isLast)
    }

    @Test
    fun `空题库时显示空状态`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(emptyList())

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertNull(state.currentQuestion)
        assertTrue(state.queue.isEmpty())
    }

    @Test
    fun `切换难度筛选队列`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        viewModel.setDifficulty("高级")

        val state = viewModel.uiState.value
        assertEquals(1, state.queue.size)
        assertEquals("高级", state.queue.first().difficulty)
        assertEquals("1 / 1", state.progress)
    }

    @Test
    fun `切换分类筛选队列`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        viewModel.setCategory("Python")

        val state = viewModel.uiState.value
        assertEquals(2, state.queue.size)
        assertTrue(state.queue.all { it.category == "Python" })
    }

    @Test
    fun `下一题和上一题正确更新索引`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        viewModel.next()
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals("2 / 3", viewModel.uiState.value.progress)
        assertFalse(viewModel.uiState.value.isFirst)
        assertFalse(viewModel.uiState.value.isLast)

        viewModel.next()
        assertEquals(2, viewModel.uiState.value.currentIndex)
        assertTrue(viewModel.uiState.value.isLast)

        viewModel.prev()
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertFalse(viewModel.uiState.value.isFirst)
    }

    @Test
    fun `第一题时上一题不生效`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        viewModel.prev()

        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertTrue(viewModel.uiState.value.isFirst)
    }

    @Test
    fun `最后一题时下一题不生效`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        viewModel.next()
        viewModel.next()
        viewModel.next()

        assertEquals(2, viewModel.uiState.value.currentIndex)
        assertTrue(viewModel.uiState.value.isLast)
    }

    @Test
    fun `翻转切换卡片状态`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val viewModel = createViewModel(questions())

        assertFalse(viewModel.uiState.value.isFlipped)
        viewModel.flip()
        assertTrue(viewModel.uiState.value.isFlipped)
        viewModel.flip()
        assertFalse(viewModel.uiState.value.isFlipped)
    }

    @Test
    fun `加载失败显示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val questionRepository = mockk<QuestionRepository>()
        coEvery { questionRepository.getVisibleQuestions() } throws RuntimeException("数据库错误")
        val settingsRepository = mockk<SettingsRepository>()
        coEvery { settingsRepository.getCategories() } returns emptyList()

        val viewModel = PracticeViewModel(questionRepository, settingsRepository)

        assertFalse(viewModel.uiState.value.loading)
        assertNotNull(viewModel.uiState.value.loadError)
    }
}
