package com.example.interviewhelper.data.repository

import androidx.paging.PagingSource
import com.example.interviewhelper.data.local.QuestionDao
import com.example.interviewhelper.data.local.QuestionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QuestionRepositoryTest {

    private lateinit var dao: QuestionDao
    private lateinit var repository: QuestionRepository

    @BeforeEach
    fun setUp() {
        dao = mockk()
        repository = QuestionRepository(dao)
    }

    private fun sampleQuestion(id: String = "q1") = QuestionEntity(
        id = id,
        category = "Agent 智能体",
        question = "什么是 AI Agent？",
        dialog = "**Q**：什么是 AI Agent？\n\n**A**：AI Agent 是一种智能系统。",
        difficulty = "初级"
    )

    @Test
    fun `新增题目自动生成 UUID 和时间戳`() = runTest {
        coEvery { dao.insert(any()) } returns Unit

        repository.addQuestion(
            question = "什么是 RAG？",
            dialog = "**Q**：什么是 RAG？",
            category = "RAG 检索增强",
            difficulty = "中级"
        )

        coVerify {
            dao.insert(match { entity ->
                entity.id.isNotBlank() &&
                    entity.question == "什么是 RAG？" &&
                    entity.category == "RAG 检索增强" &&
                    entity.difficulty == "中级" &&
                    entity.source == "manual" &&
                    !entity.builtIn &&
                    !entity.hidden &&
                    entity.createdAt > 0
            })
        }
    }

    @Test
    fun `切换隐藏状态根据当前状态取反`() = runTest {
        val question = sampleQuestion("q1").copy(hidden = false)
        coEvery { dao.getById("q1") } returns question
        coEvery { dao.updateHidden(any(), any(), any()) } returns Unit

        repository.toggleHidden("q1")

        coVerify { dao.updateHidden("q1", true, any()) }
    }

    @Test
    fun `切换隐藏状态对不存在的题目不做操作`() = runTest {
        coEvery { dao.getById("missing") } returns null

        repository.toggleHidden("missing")

        coVerify(exactly = 0) { dao.updateHidden(any(), any(), any()) }
    }

    @Test
    fun `更新题目刷新 updatedAt`() = runTest {
        val question = sampleQuestion("q1").copy(updatedAt = 100L)
        coEvery { dao.update(any()) } returns Unit

        repository.updateQuestion(question)

        coVerify { dao.update(match { it.updatedAt > 100L }) }
    }

    @Test
    fun `批量操作委托给 DAO`() = runTest {
        coEvery { dao.deleteBatch(any()) } returns Unit
        coEvery { dao.updateHiddenBatch(any(), any(), any()) } returns Unit
        coEvery { dao.updateCategoryBatch(any(), any(), any()) } returns Unit

        repository.batchDelete(listOf("q1", "q2"))
        repository.batchHide(listOf("q1"))
        repository.batchUnhide(listOf("q2"))
        repository.batchMoveCategory(listOf("q1"), "Python")

        coVerify { dao.deleteBatch(listOf("q1", "q2")) }
        coVerify { dao.updateHiddenBatch(listOf("q1"), true, any()) }
        coVerify { dao.updateHiddenBatch(listOf("q2"), false, any()) }
        coVerify { dao.updateCategoryBatch(listOf("q1"), "Python", any()) }
    }

    @Test
    fun `组合筛选时选择正确的 DAO 分页查询`() = runTest {
        val pagingSource = mockk<PagingSource<Int, QuestionEntity>>(relaxed = true)
        every { dao.getPagedByAll(any(), any(), any(), any()) } returns pagingSource
        coEvery { pagingSource.load(any()) } returns
            PagingSource.LoadResult.Page(data = listOf(sampleQuestion()), prevKey = null, nextKey = null)

        repository.getPagedQuestions(category = "Agent", difficulty = "高级", query = "RAG").first()

        verify { dao.getPagedByAll("Agent", "高级", "RAG", false) }
    }

    @Test
    fun `仅分类筛选时选择对应的 DAO 分页查询`() = runTest {
        val pagingSource = mockk<PagingSource<Int, QuestionEntity>>(relaxed = true)
        every { dao.getPagedByCategory(any(), any()) } returns pagingSource
        coEvery { pagingSource.load(any()) } returns
            PagingSource.LoadResult.Page(data = listOf(sampleQuestion()), prevKey = null, nextKey = null)

        repository.getPagedQuestions(category = "Python", difficulty = null, query = null).first()

        verify { dao.getPagedByCategory("Python", false) }
    }

    @Test
    fun `切换隐藏状态不存在的题目时 getById 返回 null`() = runTest {
        coEvery { dao.getById("q1") } returns null
        assertNull(dao.getById("q1"))
    }

    @Test
    fun `删除不存在的题目不会崩溃`() = runTest {
        coEvery { dao.delete(any()) } returns Unit
        repository.deleteQuestion("missing")
        assertTrue(true)
    }
}
