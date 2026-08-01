package com.queststack.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    indices = [Index("categoryId"), Index("difficulty")]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long?,
    val difficulty: Int,
    val createdAt: Long,
    val updatedAt: Long
)
