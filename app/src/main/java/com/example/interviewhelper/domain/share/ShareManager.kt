package com.example.interviewhelper.domain.share

import com.example.interviewhelper.data.local.QuestionEntity

/**
 * 分享模块接口 - 为未来题目分享/社区功能预留导出和分享契约
 *
 * 注: generateShareCode/resolveShareCode 需要服务端存储映射关系，
 * 当前 App 无云端后端，故注释掉留作未来扩展。
 */
interface ShareManager {
    suspend fun exportQuestionForShare(questionId: String): Result<ShareData>
    suspend fun importSharedQuestion(shareData: ShareData): Result<QuestionEntity>

    // 以下方法需要未来后端服务支持（当前 App 无云端能力）:
    // suspend fun generateShareCode(questionId: String, ttl: Long): Result<String>
    // suspend fun resolveShareCode(code: String): Result<ShareData>
}

data class ShareData(
    val question: String,
    val dialog: String,
    val difficulty: String,
    val category: String,
    val author: String?,
    val version: Int = 1,
    val checksum: String? = null
)

/**
 * 占位实现 - 分享功能尚未实现
 */
class ShareManagerStub : ShareManager {
    override suspend fun exportQuestionForShare(questionId: String): Result<ShareData> {
        return Result.failure(NotImplementedError("分享功能尚未实现"))
    }

    override suspend fun importSharedQuestion(shareData: ShareData): Result<QuestionEntity> {
        return Result.failure(NotImplementedError("分享功能尚未实现"))
    }
}
