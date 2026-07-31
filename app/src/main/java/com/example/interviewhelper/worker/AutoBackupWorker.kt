package com.example.interviewhelper.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.interviewhelper.data.model.WebDavConfig
import com.example.interviewhelper.data.remote.webdav.WebDavDataSource
import com.example.interviewhelper.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 退出 App 时自动备份到 WebDAV
 * - 仅当 WebDAV 已配置（serverUrl 非空）时执行
 * - 上传成功后清理旧备份，保留最近 5 个
 * - 永久性失败（4xx client error）不再重试，避免无限重试
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val webDavDataSource: WebDavDataSource
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_RUN_ATTEMPTS) return Result.failure()

        val config = settingsRepository.getWebDavConfig()
        if (config.serverUrl.isBlank()) return Result.success()

        val data = settingsRepository.exportAllData()
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val fileName = "auto-backup-$timestamp.json"

        webDavDataSource.ensureDirectory(config)
        val uploadResult = webDavDataSource.uploadBackup(config, fileName, data)
        if (uploadResult.isSuccess) {
            cleanupOldBackups(config)  // 保留最近5个
            return Result.success()
        }

        // 永久性错误（4xx 认证/权限问题）不重试
        val errorMsg = uploadResult.exceptionOrNull()?.message ?: ""
        return if (errorMsg.contains("HTTP 40") || errorMsg.contains("HTTP 41")) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    private suspend fun cleanupOldBackups(config: WebDavConfig) {
        try {
            val files = webDavDataSource.listFiles(config).getOrNull() ?: return
            val backups = files
                .filter { it.name.startsWith("auto-backup-") && it.name.endsWith(".json") }
                .sortedByDescending { it.name }  // 文件名含时间戳，字典序即时间序
            if (backups.size > KEEP_BACKUP_COUNT) {
                backups.drop(KEEP_BACKUP_COUNT).forEach { old ->
                    webDavDataSource.deleteBackup(config, old.name)  // 删除失败不影响主流程
                }
            }
        } catch (e: Exception) {
            // 清理失败不影响主流程
        }
    }

    companion object {
        private const val KEEP_BACKUP_COUNT = 5
        private const val MAX_RUN_ATTEMPTS = 3
    }
}
