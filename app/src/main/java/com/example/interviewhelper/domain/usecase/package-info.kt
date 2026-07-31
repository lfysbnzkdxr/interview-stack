/**
 * UseCase 层 - 领域业务逻辑编排
 *
 * 该包用于未来复杂业务逻辑的场景编排，例如：
 * - GeneratePracticePlanUseCase: 生成练题计划
 * - RecommendDifficultyUseCase: 智能推荐难度
 * - SyncConflictResolutionUseCase: 同步冲突解决
 *
 * 当前 App 规模较小，ViewModel 直接调用 Repository 足够。
 * 当业务逻辑变得复杂（跨多个 Repository、需要事务编排）时，
 * 应提取到 UseCase 层以保持 ViewModel 的简洁。
 */
package com.example.interviewhelper.domain.usecase
