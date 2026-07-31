package com.example.interviewhelper.ui.browse

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class BrowseViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private lateinit var questionRepository: QuestionRepository
    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setUp() {
        questionRepository = mockk()
        settingsRepository = mockk()
    }

    private fun question(id: String, category: String = "Agent 智能体") = QuestionEntity(
        id = id,
        category = category,
        question = "问题 $id",
        dialog = "**Q**：问题\n\n**A**：答案",
        difficulty = "中级"
    )

    private fun createViewModel() = BrowseViewModel(questionRepository, settingsRepository)

    @Test
    fun `加载可见题目和分类`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python")
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(
            listOf(question("q1", "Agent 智能体"), question("q2", "Python"))
        )

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.uiState.value.loadError)
        assertEquals(listOf("Agent 智能体", "Python"), viewModel.uiState.value.categories)
        assertEquals(2, viewModel.uiState.value.questions.size)
    }

    @Test
    fun `未选择分类时显示全部题目`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns emptyList()
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(
            listOf(question("q1", "Agent 智能体"), question("q2", "Python"), question("q3", "Agent 智能体"))
        )

        val viewModel = createViewModel()

        assertEquals(3, viewModel.filteredQuestions.size)
    }

    @Test
    fun `选择分类后只显示该分类题目`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python")
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(
            listOf(question("q1", "Agent 智能体"), question("q2", "Python"), question("q3", "Agent 智能体"))
        )

        val viewModel = createViewModel()
        viewModel.selectCategory("Agent 智能体")

        assertEquals(2, viewModel.filteredQuestions.size)
        assertTrue(viewModel.filteredQuestions.all { it.category == "Agent 智能体" })
    }

    @Test
    fun `切回全部分类恢复显示全部`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Agent 智能体", "Python")
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(
            listOf(question("q1", "Agent 智能体"), question("q2", "Python"))
        )

        val viewModel = createViewModel()
        viewModel.selectCategory("Python")
        assertEquals(1, viewModel.filteredQuestions.size)

        viewModel.selectCategory(null)
        assertEquals(2, viewModel.filteredQuestions.size)
        assertNull(viewModel.uiState.value.activeCategory)
    }

    @Test
    fun `加载失败设置错误信息`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } throws RuntimeException("数据库不可用")

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.loadError?.contains("数据加载失败") == true)
    }

    @Test
    fun `切换展开状态`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns emptyList()
        coEvery { questionRepository.getVisibleQuestions() } returns flowOf(listOf(question("q1")))

        val viewModel = createViewModel()

        viewModel.toggleExpand("q1")
        assertEquals("q1", viewModel.uiState.value.expandedId)

        viewModel.toggleExpand("q1")
        assertNull(viewModel.uiState.value.expandedId)
    }
}
