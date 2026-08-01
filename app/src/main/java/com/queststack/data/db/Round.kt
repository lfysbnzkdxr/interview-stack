package com.queststack.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rounds",
    indices = [Index("questionId")]
)
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val orderIndex: Int,
    val question: String,
    val answer: String,
    val source: String
)
