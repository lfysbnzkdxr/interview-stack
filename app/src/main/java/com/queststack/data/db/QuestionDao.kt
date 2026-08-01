package com.queststack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: Question): Long

    @Update
    suspend fun update(question: Question)

    @Transaction
    @Query("SELECT * FROM questions ORDER BY updatedAt DESC")
    fun observeAllWithRounds(): Flow<List<QuestionWithRounds>>

    @Transaction
    @Query("SELECT * FROM questions WHERE (:categoryId IS NULL OR categoryId = :categoryId) AND (:difficulty IS NULL OR difficulty = :difficulty) ORDER BY updatedAt DESC")
    fun observeFiltered(categoryId: Long?, difficulty: Int?): Flow<List<QuestionWithRounds>>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getWithRounds(id: Long): QuestionWithRounds?

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM questions WHERE (:categoryId IS NULL OR categoryId = :categoryId) AND (:difficulty IS NULL OR difficulty = :difficulty)")
    suspend fun getIds(categoryId: Long?, difficulty: Int?): List<Long>
}
