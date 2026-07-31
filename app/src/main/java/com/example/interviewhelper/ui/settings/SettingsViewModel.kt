package com.example.interviewhelper.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.interviewhelper.data.model.ApiConfig
import com.example.interviewhelper.data.model.ImportMode
import com.example.interviewhelper.data.model.ProviderConfig
import com.example.interviewhelper.data.model.ProviderPreset
import com.example.interviewhelper.data.model.WebDavConfig
import com.example.interviewhelper.data.model.WebDavFile
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.remote.webdav.WebDavDataSource
import com.example.interviewhelper.data.repository.BackupRepository
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    // API
    val apiProviders: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val testingProviderId: String? = null,
    val testResult: String? = null,
    val showAddProviderForm: Boolean = false,
    val newProviderName: String = "",
    val newProviderUrl: String = "",
    val newProviderKey: String = "",
    val newProviderModel: String = "",
    // Categories
    val categories: List<String> = emptyList(),
    // Data
    val dataMessage: String? = null,
    // WebDAV
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavPath: String = "/interview-backups/",
    val webDavTesting: Boolean = false,
    val webDavMessage: String? = null,
    val webDavBackingUp: Boolean = false,
    val webDavRestoring: Boolean = false,
    val webDavBackupFiles: List<WebDavFile> = emptyList(),
    val showBackupPicker: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService,
    private val webDavDataSource: WebDavDataSource,
    private val backupRepository: BackupRepository,
    private val questionRepository: QuestionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            val apiConfig = settingsRepository.getApiConfig()
            val categories = settingsRepository.getCategories()
            val webDavConfig = settingsRepository.getWebDavConfig()
            _uiState.update {
                it.copy(
                    apiProviders = apiConfig.providers,
                    activeProviderId = apiConfig.activeProviderId,
                    categories = categories,
                    webDavUrl = webDavConfig.serverUrl,
                    webDavUsername = webDavConfig.username,
                    webDavPassword = webDavConfig.password,
                    webDavPath = webDavConfig.remotePath
                )
            }
        }
    }

    // ========== API Config ==========
    fun showAddProvider() = _uiState.update { it.copy(showAddProviderForm = true, newProviderName = "", newProviderUrl = "", newProviderKey = "", newProviderModel = "") }
    fun hideAddProvider() = _uiState.update { it.copy(showAddProviderForm = false) }
    fun setNewProviderName(v: String) = _uiState.update { it.copy(newProviderName = v) }
    fun setNewProviderUrl(v: String) = _uiState.update { it.copy(newProviderUrl = v) }
    fun setNewProviderKey(v: String) = _uiState.update { it.copy(newProviderKey = v) }
    fun setNewProviderModel(v: String) = _uiState.update { it.copy(newProviderModel = v) }

    /**
     * 应用预设提供商配置到添加表单（仅需用户补充 API Key）
     */
    fun applyPreset(preset: ProviderPreset) {
        _uiState.update {
            it.copy(
                newProviderName = preset.name,
                newProviderUrl = preset.baseUrl,
                newProviderModel = preset.models.firstOrNull() ?: ""
            )
        }
    }

    fun addProvider() {
        val state = _uiState.value
        if (state.newProviderName.isBlank() || state.newProviderUrl.isBlank() || state.newProviderKey.isBlank()) return

        viewModelScope.launch {
            val newProvider = ProviderConfig(
                id = UUID.randomUUID().toString(),
                name = state.newProviderName,
                baseUrl = state.newProviderUrl,
                apiKey = state.newProviderKey,
                model = state.newProviderModel.ifBlank { "default" }
            )
            val config = settingsRepository.getApiConfig()
            val updatedProviders = config.providers + newProvider
            val newActiveId = config.activeProviderId ?: newProvider.id
            settingsRepository.saveApiConfig(ApiConfig(providers = updatedProviders, activeProviderId = newActiveId))
            _uiState.update { it.copy(apiProviders = updatedProviders, activeProviderId = newActiveId, showAddProviderForm = false) }
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            val config = settingsRepository.getApiConfig()
            settingsRepository.saveApiConfig(config.copy(activeProviderId = id))
            _uiState.update { it.copy(activeProviderId = id) }
        }
    }

    fun testProvider(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(testingProviderId = id, testResult = null) }
            val provider = _uiState.value.apiProviders.find { it.id == id } ?: return@launch
            val result = llmService.testConnection(provider)
            result.fold(
                onSuccess = { _uiState.update { it.copy(testingProviderId = null, testResult = "连接成功 ✓") } },
                onFailure = { e -> _uiState.update { it.copy(testingProviderId = null, testResult = "连接失败: ${e.message}") } }
            )
        }
    }

    // ========== Categories ==========
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val cats = settingsRepository.getCategories().toMutableList()
            if (trimmed in cats) {
                _uiState.update { it.copy(dataMessage = "分类已存在") }
                return@launch
            }
            val insertIndex = (cats.size - 1).coerceAtLeast(0) // 在"未分类"前
            cats.add(insertIndex, trimmed)
            settingsRepository.saveCategories(cats)
            _uiState.update { it.copy(categories = cats) }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            val cats = settingsRepository.getCategories().toMutableList()
            cats.remove(name)
            settingsRepository.saveCategories(cats)
            // 该分类下的题目迁移到"未分类"
            questionRepository.updateCategoryName(name, "未分类")
            _uiState.update { it.copy(categories = cats) }
        }
    }

    // ========== Data ==========
    fun exportToFile(uri: Uri) {
        viewModelScope.launch {
            val result = backupRepository.exportToFile(uri)
            result.fold(
                onSuccess = { _uiState.update { it.copy(dataMessage = "导出成功") } },
                onFailure = { e -> _uiState.update { it.copy(dataMessage = "导出失败: ${e.message}") } }
            )
        }
    }

    fun importFromFile(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            val result = backupRepository.importFromFile(uri, mode)
            result.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(dataMessage = "导入成功: $count 道题目") }
                    loadAll()
                },
                onFailure = { e -> _uiState.update { it.copy(dataMessage = "导入失败: ${e.message}") } }
            )
        }
    }

    fun resetData() {
        viewModelScope.launch {
            settingsRepository.resetToDefault()
            loadAll()
            _uiState.update { it.copy(dataMessage = "已恢复默认题库") }
        }
    }

    // ========== WebDAV ==========
    fun setWebDavUrl(v: String) = _uiState.update { it.copy(webDavUrl = v) }
    fun setWebDavUsername(v: String) = _uiState.update { it.copy(webDavUsername = v) }
    fun setWebDavPassword(v: String) = _uiState.update { it.copy(webDavPassword = v) }
    fun setWebDavPath(v: String) = _uiState.update { it.copy(webDavPath = v) }

    fun saveWebDavConfig() {
        viewModelScope.launch {
            val config = WebDavConfig(
                serverUrl = _uiState.value.webDavUrl,
                username = _uiState.value.webDavUsername,
                password = _uiState.value.webDavPassword,
                remotePath = _uiState.value.webDavPath
            )
            settingsRepository.saveWebDavConfig(config)
            _uiState.update { it.copy(webDavMessage = "配置已保存") }
        }
    }

    fun testWebDav() {
        viewModelScope.launch {
            _uiState.update { it.copy(webDavTesting = true, webDavMessage = null) }
            val config = WebDavConfig(
                serverUrl = _uiState.value.webDavUrl,
                username = _uiState.value.webDavUsername,
                password = _uiState.value.webDavPassword,
                remotePath = _uiState.value.webDavPath
            )
            val result = webDavDataSource.testConnection(config)
            result.fold(
                onSuccess = { _uiState.update { it.copy(webDavTesting = false, webDavMessage = "连接成功 ✓") } },
                onFailure = { e -> _uiState.update { it.copy(webDavTesting = false, webDavMessage = "连接失败: ${e.message}") } }
            )
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(webDavBackingUp = true, webDavMessage = null) }
            try {
                val config = settingsRepository.getWebDavConfig()
                val data = settingsRepository.exportAllData()
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                val fileName = "interview-backup-$timestamp.json"

                webDavDataSource.ensureDirectory(config)
                val result = webDavDataSource.uploadBackup(config, fileName, data)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(webDavBackingUp = false, webDavMessage = "备份完成: $fileName") }
                        cleanupManualBackups()
                    },
                    onFailure = { e -> _uiState.update { it.copy(webDavBackingUp = false, webDavMessage = "备份失败: ${e.message}") } }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(webDavBackingUp = false, webDavMessage = "备份失败: ${e.message}") }
            }
        }
    }

    /**
     * 拉取服务器上的备份文件列表并弹出选择器
     */
    fun loadWebDavBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(webDavRestoring = true, webDavMessage = null) }
            try {
                val config = settingsRepository.getWebDavConfig()
                val listResult = webDavDataSource.listFiles(config)
                val files = listResult.getOrNull()
                if (files == null) {
                    _uiState.update {
                        it.copy(
                            webDavRestoring = false,
                            webDavMessage = "获取备份列表失败: ${listResult.exceptionOrNull()?.message ?: "未知错误"}"
                        )
                    }
                    return@launch
                }
                val jsonFiles = files
                    .filter { it.name.endsWith(".json") }
                    .sortedByDescending { it.lastModified }
                _uiState.update {
                    it.copy(webDavRestoring = false, webDavBackupFiles = jsonFiles, showBackupPicker = true)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(webDavRestoring = false, webDavMessage = "获取备份列表失败: ${e.message}")
                }
            }
        }
    }

    fun dismissBackupPicker() = _uiState.update { it.copy(showBackupPicker = false) }

    /**
     * 从指定备份文件恢复（需用户先选择文件并确认导入模式）
     */
    fun restoreFromWebDav(file: WebDavFile, mode: ImportMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(webDavRestoring = true, webDavMessage = null) }
            try {
                val config = settingsRepository.getWebDavConfig()
                val downloadResult = webDavDataSource.downloadBackup(config, file.name)
                downloadResult.fold(
                    onSuccess = { jsonStr ->
                        val importResult = settingsRepository.importData(jsonStr, mode)
                        importResult.fold(
                            onSuccess = { count ->
                                _uiState.update {
                                    it.copy(
                                        webDavRestoring = false,
                                        showBackupPicker = false,
                                        webDavMessage = "恢复成功: 导入 $count 道题目"
                                    )
                                }
                                loadAll()
                            },
                            onFailure = { e ->
                                _uiState.update { it.copy(webDavRestoring = false, webDavMessage = "恢复失败: ${e.message}") }
                            }
                        )
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(webDavRestoring = false, webDavMessage = "下载失败: ${e.message}") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(webDavRestoring = false, webDavMessage = "恢复失败: ${e.message}") }
            }
        }
    }

    /**
     * 清理过旧的手动备份，仅保留最近 MANUAL_BACKUP_KEEP 个
     */
    private suspend fun cleanupManualBackups() {
        try {
            val config = settingsRepository.getWebDavConfig()
            val files = webDavDataSource.listFiles(config).getOrNull() ?: return
            val backups = files
                .filter { it.name.startsWith("interview-backup-") && it.name.endsWith(".json") }
                .sortedByDescending { it.name }  // 文件名含时间戳，字典序即时间序
            if (backups.size > MANUAL_BACKUP_KEEP) {
                backups.drop(MANUAL_BACKUP_KEEP).forEach { old ->
                    webDavDataSource.deleteBackup(config, old.name)  // 删除失败不影响主流程
                }
            }
        } catch (e: Exception) {
            // 清理失败不影响主流程
        }
    }

    companion object {
        private const val MANUAL_BACKUP_KEEP = 5
    }
}
