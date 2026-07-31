package com.example.interviewhelper.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.interviewhelper.data.local.QuestionDao
import com.example.interviewhelper.data.local.QuestionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val questionDao: QuestionDao
) {
    companion object {
        const val PAGE_SIZE = 20
    }

    fun getAllQuestions(): Flow<List<QuestionEntity>> = questionDao.getAll()

    fun getVisibleQuestions(): Flow<List<QuestionEntity>> = questionDao.getVisible()

    fun getQuestionsByCategory(category: String): Flow<List<QuestionEntity>> =
        questionDao.getByCategory(category)

    fun getQuestionsByDifficulty(difficulty: String): Flow<List<QuestionEntity>> =
        questionDao.getByDifficulty(difficulty)

    fun searchQuestions(query: String): Flow<List<QuestionEntity>> =
        questionDao.search(query)

    fun getQuestionCount(): Flow<Int> = questionDao.getCount()

    fun getVisibleCount(): Flow<Int> = questionDao.getVisibleCount()

    suspend fun getQuestionById(id: String): QuestionEntity? = questionDao.getById(id)

    suspend fun addQuestion(
        question: String,
        dialog: String = "",
        category: String,
        difficulty: String = "中级",
        source: String = "manual"
    ) {
        val entity = QuestionEntity(
            id = UUID.randomUUID().toString(),
            category = category,
            question = question,
            dialog = dialog,
            difficulty = difficulty,
            source = source,
            builtIn = false,
            hidden = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        questionDao.insert(entity)
    }

    suspend fun addQuestionEntity(entity: QuestionEntity) {
        questionDao.insert(entity)
    }

    suspend fun updateQuestion(entity: QuestionEntity) {
        questionDao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteQuestion(id: String) {
        questionDao.delete(id)
    }

    suspend fun batchDelete(ids: List<String>) {
        questionDao.deleteBatch(ids)
    }

    suspend fun toggleHidden(id: String) {
        val question = questionDao.getById(id) ?: return
        questionDao.updateHidden(id, !question.hidden)
    }

    suspend fun batchHide(ids: List<String>) {
        questionDao.updateHiddenBatch(ids, true)
    }

    suspend fun batchUnhide(ids: List<String>) {
        questionDao.updateHiddenBatch(ids, false)
    }

    suspend fun batchMoveCategory(ids: List<String>, category: String) {
        questionDao.updateCategoryBatch(ids, category)
    }

    suspend fun updateCategoryName(oldName: String, newName: String) {
        questionDao.updateCategoryName(oldName, newName)
    }

    suspend fun deleteAll() {
        questionDao.deleteAll()
    }

    suspend fun insertAll(questions: List<QuestionEntity>) {
        questionDao.insertAll(questions)
    }

    /**
     * 事务性覆盖：清空后写入（用于 OVERWRITE 导入，避免中途失败留下空库）
     */
    suspend fun replaceAll(questions: List<QuestionEntity>) {
        questionDao.replaceAll(questions)
    }

    // Paging 3 数据源
    fun getPagedQuestions(
        category: String? = null,
        difficulty: String? = null,
        query: String? = null
    ): Flow<PagingData<QuestionEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                when {
                    category != null && difficulty != null && !query.isNullOrBlank() ->
                        questionDao.getPagedByAll(category, difficulty, query)
                    category != null && difficulty != null ->
                        questionDao.getPagedByCategoryAndDifficulty(category, difficulty)
                    category != null && !query.isNullOrBlank() ->
                        questionDao.getPagedByCategoryAndSearch(category, query)
                    difficulty != null && !query.isNullOrBlank() ->
                        questionDao.getPagedByDifficultyAndSearch(difficulty, query)
                    category != null -> questionDao.getPagedByCategory(category)
                    difficulty != null -> questionDao.getPagedByDifficulty(difficulty)
                    !query.isNullOrBlank() -> questionDao.getPagedSearch(query)
                    else -> questionDao.getPagedAll()
                }
            }
        ).flow
    }
}
