package com.queststack.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RoundDao {
    @Insert
    suspend fun insert(round: Round): Long

    @Insert
    suspend fun insertAll(rounds: List<Round>)

    @Query("DELETE FROM rounds WHERE questionId = :questionId")
    suspend fun deleteByQuestionId(questionId: Long)
}
