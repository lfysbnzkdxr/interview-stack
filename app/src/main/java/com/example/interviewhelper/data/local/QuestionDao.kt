package com.example.interviewhelper.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 分类题目计数（用于题库页分类 Tab 徽标） */
data class CategoryCount(
    val category: String,
    val count: Int
)

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE hidden = 0 ORDER BY createdAt DESC")
    fun getVisible(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT COUNT(*) FROM questions")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE hidden = 0")
    fun getVisibleCount(): Flow<Int>

    @Query("SELECT category, COUNT(*) AS count FROM questions WHERE (:visibleOnly = 0 OR hidden = 0) GROUP BY category")
    fun getCategoryCounts(visibleOnly: Boolean): Flow<List<CategoryCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM questions WHERE id IN (:ids)")
    suspend fun deleteBatch(ids: List<String>)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    @Query("UPDATE questions SET hidden = :hidden, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateHidden(id: String, hidden: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE questions SET hidden = :hidden, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateHiddenBatch(ids: List<String>, hidden: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE questions SET category = :category, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateCategoryBatch(ids: List<String>, category: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE questions SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategoryName(oldCategory: String, newCategory: String)

    @Transaction
    suspend fun replaceAll(questions: List<QuestionEntity>) {
        deleteAll()
        insertAll(questions)
    }

    // Paging 3 数据源（visibleOnly = true 时仅查询未隐藏题目）
    @Query("SELECT * FROM questions WHERE (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedAll(visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByCategory(category: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByDifficulty(difficulty: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedSearch(query: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND difficulty = :difficulty AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByCategoryAndDifficulty(category: String, difficulty: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByCategoryAndSearch(category: String, query: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByDifficultyAndSearch(difficulty: String, query: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND difficulty = :difficulty AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') AND (:visibleOnly = 0 OR hidden = 0) ORDER BY createdAt DESC")
    fun getPagedByAll(category: String, difficulty: String, query: String, visibleOnly: Boolean): PagingSource<Int, QuestionEntity>
}
