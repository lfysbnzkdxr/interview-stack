package com.example.interviewhelper.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE hidden = 0 ORDER BY createdAt DESC")
    fun getVisible(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id")
    fun getByIdFlow(id: String): Flow<QuestionEntity?>

    @Query("SELECT * FROM questions WHERE category = :category AND hidden = 0 ORDER BY createdAt DESC")
    fun getByCategory(category: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty AND hidden = 0 ORDER BY createdAt DESC")
    fun getByDifficulty(difficulty: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') AND hidden = 0 ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun searchAll(query: String): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE hidden = 0")
    fun getVisibleCount(): Flow<Int>

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
    suspend fun updateBatch(questions: List<QuestionEntity>) {
        questions.forEach { update(it) }
    }

    @Transaction
    suspend fun replaceAll(questions: List<QuestionEntity>) {
        deleteAll()
        insertAll(questions)
    }

    // Paging 3 数据源
    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getPagedAll(): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category ORDER BY createdAt DESC")
    fun getPagedByCategory(category: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty ORDER BY createdAt DESC")
    fun getPagedByDifficulty(difficulty: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun getPagedSearch(query: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND difficulty = :difficulty ORDER BY createdAt DESC")
    fun getPagedByCategoryAndDifficulty(category: String, difficulty: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun getPagedByCategoryAndSearch(category: String, query: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun getPagedByDifficultyAndSearch(difficulty: String, query: String): PagingSource<Int, QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category AND difficulty = :difficulty AND (question LIKE '%' || :query || '%' OR dialog LIKE '%' || :query || '%') ORDER BY createdAt DESC")
    fun getPagedByAll(category: String, difficulty: String, query: String): PagingSource<Int, QuestionEntity>
}
