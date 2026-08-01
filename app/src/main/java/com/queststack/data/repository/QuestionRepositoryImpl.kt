package com.queststack.data.repository

import com.queststack.data.db.Question
import com.queststack.data.db.QuestionDao
import com.queststack.data.db.QuestionWithRounds
import com.queststack.data.db.Round
import com.queststack.data.db.RoundDao
import kotlinx.coroutines.flow.Flow

class QuestionRepositoryImpl(
    private val questionDao: QuestionDao,
    private val roundDao: RoundDao
) : QuestionRepository {

    override fun observeQuestions(categoryId: Long?, difficulty: Int?): Flow<List<QuestionWithRounds>> =
        questionDao.observeFiltered(categoryId, difficulty)

    override suspend fun getQuestion(id: Long): QuestionWithRounds? =
        questionDao.getWithRounds(id)

    override suspend fun addQuestion(
        title: String,
        categoryId: Long?,
        difficulty: Int,
        rounds: List<Pair<String, String>>
    ): Long {
        val now = System.currentTimeMillis()
        val question = Question(
            title = title,
            categoryId = categoryId,
            difficulty = difficulty,
            createdAt = now,
            updatedAt = now
        )
        val questionId = questionDao.insert(question)
        val allRounds = buildList {
            add(Round(questionId = questionId, orderIndex = 0, question = title, answer = "", source = "manual"))
            rounds.forEachIndexed { index, (q, a) ->
                add(Round(questionId = questionId, orderIndex = index + 1, question = q, answer = a, source = "manual"))
            }
        }
        roundDao.insertAll(allRounds)
        return questionId
    }

    override suspend fun updateQuestion(question: Question, rounds: List<Round>) {
        roundDao.deleteByQuestionId(question.id)
        roundDao.insertAll(rounds)
        questionDao.update(question.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteQuestion(id: Long) {
        roundDao.deleteByQuestionId(id)
        questionDao.delete(Question(id = id, title = "", categoryId = null, difficulty = 1, createdAt = 0, updatedAt = 0))
    }

    override suspend fun randomQuestionIds(categoryId: Long?, difficulty: Int?): List<Long> =
        questionDao.getIds(categoryId, difficulty).shuffled()
}
