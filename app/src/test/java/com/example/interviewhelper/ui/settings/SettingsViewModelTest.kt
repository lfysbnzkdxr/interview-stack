package com.example.interviewhelper.ui.settings

import android.net.Uri
import com.example.interviewhelper.MainDispatcherExtension
import com.example.interviewhelper.data.model.ApiConfig
import com.example.interviewhelper.data.model.ImportMode
import com.example.interviewhelper.data.model.ProviderConfig
import com.example.interviewhelper.data.model.WebDavConfig
import com.example.interviewhelper.data.model.WebDavFile
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.remote.webdav.WebDavDataSource
import com.example.interviewhelper.data.repository.BackupRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var llmService: LlmService
    private lateinit var webDavDataSource: WebDavDataSource
    private lateinit var backupRepository: BackupRepository

    @BeforeEach
    fun setUp() {
        settingsRepository = mockk()
        llmService = mockk()
        webDavDataSource = mockk()
        backupRepository = mockk()
        coEvery { settingsRepository.getApiConfig() } returns ApiConfig()
        coEvery { settingsRepository.getCategories() } returns emptyList()
        coEvery { settingsRepository.getWebDavConfig() } returns WebDavConfig()
    }

    private fun createViewModel() = SettingsViewModel(settingsRepository, llmService, webDavDataSource, backupRepository)

    private fun provider(id: String = "p1") = ProviderConfig(
        id = id,
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "key",
        model = "deepseek-chat"
    )

    @Test
    fun `加载初始化各配置`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getApiConfig() } returns
            ApiConfig(providers = listOf(provider("p1")), activeProviderId = "p1")
        coEvery { settingsRepository.getCategories() } returns listOf("Python", "未分类")
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")

        val viewModel = createViewModel()

        assertEquals("p1", viewModel.uiState.value.activeProviderId)
        assertEquals(1, viewModel.uiState.value.apiProviders.size)
        assertEquals(listOf("Python", "未分类"), viewModel.uiState.value.categories)
        assertEquals("https://example.com", viewModel.uiState.value.webDavUrl)
    }

    @Test
    fun `添加提供商并自动激活第一个`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.saveApiConfig(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.showAddProvider()
        viewModel.setNewProviderName("DeepSeek")
        viewModel.setNewProviderUrl("https://api.deepseek.com/v1")
        viewModel.setNewProviderKey("key")
        viewModel.setNewProviderModel("deepseek-chat")
        viewModel.addProvider()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.apiProviders.size)
        assertNotNull(viewModel.uiState.value.activeProviderId)
        assertFalse(viewModel.uiState.value.showAddProviderForm)
        coVerify { settingsRepository.saveApiConfig(any()) }
    }

    @Test
    fun `添加提供商时未填名称不生效`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.saveApiConfig(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.showAddProvider()
        viewModel.setNewProviderUrl("https://example.com")
        viewModel.setNewProviderKey("key")
        viewModel.addProvider()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.apiProviders.isEmpty())
        coVerify(exactly = 0) { settingsRepository.saveApiConfig(any()) }
    }

    @Test
    fun `添加重名分类提示已存在`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Python", "未分类")
        coEvery { settingsRepository.saveCategories(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addCategory("Python")
        advanceUntilIdle()

        assertEquals("分类已存在", viewModel.uiState.value.dataMessage)
        coVerify(exactly = 0) { settingsRepository.saveCategories(any()) }
    }

    @Test
    fun `添加新分类成功保存`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getCategories() } returns listOf("Python", "未分类")
        coEvery { settingsRepository.saveCategories(any()) } returns Unit
        val viewModel = createViewModel()

        viewModel.addCategory("RAG 检索增强")
        advanceUntilIdle()

        coVerify { settingsRepository.saveCategories(any()) }
        assertTrue("RAG 检索增强" in viewModel.uiState.value.categories)
    }

    @Test
    fun `导出到文件成功提示`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { backupRepository.exportToFile(any()) } returns Result.success(Unit)
        val viewModel = createViewModel()

        viewModel.exportToFile(mockk<Uri>())
        advanceUntilIdle()

        assertEquals("导出成功", viewModel.uiState.value.dataMessage)
        coVerify { backupRepository.exportToFile(any()) }
    }

    @Test
    fun `导出到文件失败提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { backupRepository.exportToFile(any()) } returns Result.failure(Exception("磁盘已满"))
        val viewModel = createViewModel()

        viewModel.exportToFile(mockk<Uri>())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dataMessage!!.contains("磁盘已满"))
    }

    @Test
    fun `从文件导入成功刷新数据`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { backupRepository.importFromFile(any(), any()) } returns Result.success(5)
        val viewModel = createViewModel()

        viewModel.importFromFile(mockk<Uri>(), ImportMode.MERGE)
        advanceUntilIdle()

        assertEquals("导入成功: 5 道题目", viewModel.uiState.value.dataMessage)
        coVerify { backupRepository.importFromFile(any(), ImportMode.MERGE) }
    }

    @Test
    fun `重置默认题库`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.resetToDefault() } returns Unit
        val viewModel = createViewModel()

        viewModel.resetData()
        advanceUntilIdle()

        assertEquals("已恢复默认题库", viewModel.uiState.value.dataMessage)
        coVerify { settingsRepository.resetToDefault() }
    }

    @Test
    fun `立即备份成功提示并清理旧备份`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { settingsRepository.exportAllData() } returns "{}"
        coEvery { webDavDataSource.ensureDirectory(any()) } returns Result.success(Unit)
        coEvery { webDavDataSource.uploadBackup(any(), any(), any()) } returns Result.success(Unit)
        coEvery { webDavDataSource.listFiles(any()) } returns Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.backupNow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("备份完成"))
        coVerify { webDavDataSource.listFiles(any()) }
    }

    @Test
    fun `备份失败提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { settingsRepository.exportAllData() } returns "{}"
        coEvery { webDavDataSource.ensureDirectory(any()) } returns Result.success(Unit)
        coEvery { webDavDataSource.uploadBackup(any(), any(), any()) } returns Result.failure(Exception("网络断开"))
        val viewModel = createViewModel()

        viewModel.backupNow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("网络断开"))
    }

    @Test
    fun `测试 WebDAV 连接成功`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { webDavDataSource.testConnection(any()) } returns Result.success(true)
        val viewModel = createViewModel()

        viewModel.setWebDavUrl("https://example.com")
        viewModel.testWebDav()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("连接成功"))
    }

    @Test
    fun `加载备份列表按时间倒序`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { webDavDataSource.listFiles(any()) } returns Result.success(
            listOf(
                WebDavFile(
                    name = "interview-backup-20260730-120000.json",
                    path = "/backups/interview-backup-20260730-120000.json",
                    size = 100L,
                    lastModified = 1000L,
                    isDirectory = false
                ),
                WebDavFile(
                    name = "interview-backup-20260731-090000.json",
                    path = "/backups/interview-backup-20260731-090000.json",
                    size = 200L,
                    lastModified = 2000L,
                    isDirectory = false
                )
            )
        )
        val viewModel = createViewModel()

        viewModel.loadWebDavBackups()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showBackupPicker)
        assertEquals(2, viewModel.uiState.value.webDavBackupFiles.size)
        assertEquals(
            "interview-backup-20260731-090000.json",
            viewModel.uiState.value.webDavBackupFiles.first().name
        )
    }

    @Test
    fun `无备份文件时选择器显示空列表`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { webDavDataSource.listFiles(any()) } returns Result.success(emptyList())
        val viewModel = createViewModel()

        viewModel.loadWebDavBackups()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showBackupPicker)
        assertTrue(viewModel.uiState.value.webDavBackupFiles.isEmpty())
    }

    @Test
    fun `获取备份列表失败时提示错误`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { webDavDataSource.listFiles(any()) } returns Result.failure(Exception("网络断开"))
        val viewModel = createViewModel()

        viewModel.loadWebDavBackups()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showBackupPicker)
        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("获取备份列表失败"))
    }

    @Test
    fun `从指定备份文件恢复并覆盖导入`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val file = WebDavFile(
            name = "interview-backup-20260730-120000.json",
            path = "/backups/interview-backup-20260730-120000.json",
            size = 100L,
            lastModified = 1000L,
            isDirectory = false
        )
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { webDavDataSource.downloadBackup(any(), any()) } returns Result.success("""{"categories":[],"questions":[]}""")
        coEvery { settingsRepository.importData(any(), any()) } returns Result.success(3)
        val viewModel = createViewModel()

        viewModel.restoreFromWebDav(file, ImportMode.OVERWRITE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("恢复成功"))
        assertFalse(viewModel.uiState.value.showBackupPicker)
        coVerify { webDavDataSource.downloadBackup(any(), "interview-backup-20260730-120000.json") }
        coVerify { settingsRepository.importData(any(), ImportMode.OVERWRITE) }
    }

    @Test
    fun `从备份合并导入`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        val file = WebDavFile(
            name = "interview-backup-20260730-120000.json",
            path = "/backups/interview-backup-20260730-120000.json",
            size = 100L,
            lastModified = 1000L,
            isDirectory = false
        )
        coEvery { settingsRepository.getWebDavConfig() } returns
            WebDavConfig(serverUrl = "https://example.com")
        coEvery { webDavDataSource.downloadBackup(any(), any()) } returns Result.success("""{"categories":[],"questions":[]}""")
        coEvery { settingsRepository.importData(any(), any()) } returns Result.success(2)
        val viewModel = createViewModel()

        viewModel.restoreFromWebDav(file, ImportMode.MERGE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.webDavMessage!!.contains("恢复成功"))
        coVerify { settingsRepository.importData(any(), ImportMode.MERGE) }
    }

    @Test
    fun `测试提供商连接`() = runTest(mainDispatcher.testDispatcher.scheduler) {
        coEvery { settingsRepository.getApiConfig() } returns
            ApiConfig(providers = listOf(provider("p1")), activeProviderId = "p1")
        coEvery { llmService.testConnection(any()) } returns Result.success(true)
        val viewModel = createViewModel()

        viewModel.testProvider("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.testResult!!.contains("成功"))
    }
}
