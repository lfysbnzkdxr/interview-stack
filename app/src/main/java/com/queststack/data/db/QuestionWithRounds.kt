package com.queststack.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class QuestionWithRounds(
    @Embedded val question: Question,
    @Relation(parentColumn = "id", entityColumn = "questionId")
    val rounds: List<Round>
)
