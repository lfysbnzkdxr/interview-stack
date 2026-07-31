package com.example.interviewhelper.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 用户模块接口 - 为未来用户注册/登录系统预留数据层契约
 */
interface UserRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun register(username: String, password: String, email: String): Result<User>
    suspend fun logout()
    fun getCurrentUser(): Flow<User?>
    suspend fun updateProfile(user: User): Result<User>
}

data class User(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: Long
)

/**
 * 占位实现 - 用户系统尚未实现
 */
class UserRepositoryStub : UserRepository {
    override suspend fun login(username: String, password: String): Result<User> {
        return Result.failure(NotImplementedError("用户系统尚未实现"))
    }

    override suspend fun register(username: String, password: String, email: String): Result<User> {
        return Result.failure(NotImplementedError("用户系统尚未实现"))
    }

    override suspend fun logout() {
        // No-op
    }

    override fun getCurrentUser(): Flow<User?> {
        return flowOf(null)
    }

    override suspend fun updateProfile(user: User): Result<User> {
        return Result.failure(NotImplementedError("用户系统尚未实现"))
    }
}
