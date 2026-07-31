package com.example.interviewhelper.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 题目实体 - 对应 web 版 IndexedDB 中的 Question 类型
 */
@Serializable
@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val question: String,
    val dialog: String = "",
    val difficulty: String = "中级",
    val source: String = "manual",
    val builtIn: Boolean = false,
    val hidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
