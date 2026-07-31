package com.example.interviewhelper.data.repository

import android.content.Context
import android.net.Uri
import com.example.interviewhelper.data.model.ImportMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    /**
     * 导出数据为 JSON 字符串
     */
    suspend fun exportToJson(): String {
        return settingsRepository.exportAllData()
    }

    /**
     * 将 JSON 写入 Uri（SAF 文件选择器返回的 Uri）
     */
    suspend fun exportToFile(uri: Uri): Result<Unit> {
        return try {
            val jsonStr = settingsRepository.exportAllData()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("无法打开文件输出流"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("导出失败: ${e.message}"))
        }
    }

    /**
     * 从 Uri 读取 JSON 并导入
     */
    suspend fun importFromFile(uri: Uri, mode: ImportMode): Result<Int> {
        return try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return Result.failure(Exception("无法打开文件输入流"))

            settingsRepository.importData(jsonStr, mode)
        } catch (e: Exception) {
            Result.failure(Exception("导入失败: ${e.message}"))
        }
    }

    /**
     * 预览导入文件内容（返回题目数和分类数）
     */
    suspend fun previewImport(uri: Uri): Result<Pair<Int, Int>> {
        return try {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return Result.failure(Exception("无法打开文件"))

            val exportData = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<ExportData>(jsonStr)
            Result.success(Pair(exportData.questions.size, exportData.categories.size))
        } catch (e: Exception) {
            Result.failure(Exception("数据格式无效: ${e.message}"))
        }
    }
}
