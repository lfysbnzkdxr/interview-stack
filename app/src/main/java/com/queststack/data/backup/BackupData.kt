package com.queststack.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupCategory(val name: String, val sortOrder: Int)

@Serializable
data class BackupQuestion(
    val title: String,
    val categoryName: String?,
    val difficulty: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val rounds: List<BackupRound>,
)

@Serializable
data class BackupRound(val orderIndex: Int, val question: String, val answer: String, val source: String)

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val questions: List<BackupQuestion>,
)
