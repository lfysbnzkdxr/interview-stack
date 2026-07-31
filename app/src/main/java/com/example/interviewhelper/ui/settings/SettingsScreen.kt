package com.example.interviewhelper.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.data.model.ImportMode
import com.example.interviewhelper.data.model.ProviderConfig
import com.example.interviewhelper.data.model.WebDavFile
import com.example.interviewhelper.ui.common.ConfirmDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        // API 配置区
        ApiConfigSection(uiState = uiState, viewModel = viewModel)

        HorizontalDivider()

        // 分类管理区
        CategorySection(uiState = uiState, viewModel = viewModel)

        HorizontalDivider()

        // 数据管理区
        DataSection(uiState = uiState, viewModel = viewModel)

        HorizontalDivider()

        // WebDAV 备份区
        WebDavSection(uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun ApiConfigSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("API 提供商", style = MaterialTheme.typography.titleMedium)

            if (uiState.apiProviders.isEmpty()) {
                Text("尚未配置任何提供商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                uiState.apiProviders.forEach { provider ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = provider.id == uiState.activeProviderId,
                                onClick = { viewModel.setActiveProvider(provider.id) }
                            )
                            Column {
                                Text(provider.name, style = MaterialTheme.typography.bodyMedium)
                                Text(provider.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { viewModel.testProvider(provider.id) }) {
                            Text(if (uiState.testingProviderId == provider.id) "测试中..." else "测试")
                        }
                    }
                }
            }

            uiState.testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.contains("成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(onClick = { viewModel.showAddProvider() }, modifier = Modifier.fillMaxWidth()) {
                Text("+ 添加提供商")
            }

            // 添加提供商表单
            if (uiState.showAddProviderForm) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 预设快捷选择
                        var presetMenuExpanded by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { presetMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("从预设选择提供商")
                        }
                        DropdownMenu(expanded = presetMenuExpanded, onDismissRequest = { presetMenuExpanded = false }) {
                            ProviderConfig.PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = { viewModel.applyPreset(preset); presetMenuExpanded = false }
                                )
                            }
                        }
                        OutlinedTextField(value = uiState.newProviderName, onValueChange = viewModel::setNewProviderName, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = uiState.newProviderUrl, onValueChange = viewModel::setNewProviderUrl, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = uiState.newProviderKey, onValueChange = viewModel::setNewProviderKey, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                        OutlinedTextField(value = uiState.newProviderModel, onValueChange = viewModel::setNewProviderModel, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { viewModel.hideAddProvider() }) { Text("取消") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.addProvider() }) { Text("添加") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    var newCatName by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("分类管理", style = MaterialTheme.typography.titleMedium)

            uiState.categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(cat, style = MaterialTheme.typography.bodyMedium)
                    if (cat != "未分类") {
                        IconButton(onClick = { viewModel.deleteCategory(cat) }) {
                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    placeholder = { Text("新分类名称") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.addCategory(newCatName); newCatName = "" }) { Text("添加") }
            }
        }
    }
}

@Composable
private fun DataSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    var showResetDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToFile(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { pendingImportUri = it }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据管理", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(
                onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                    exportLauncher.launch("interview-backup-$timestamp.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出全部数据")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("从文件导入")
            }
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复默认题库", color = MaterialTheme.colorScheme.error)
            }

            uiState.dataMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // 导入模式选择
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("选择导入模式") },
            text = { Text("覆盖将替换现有数据，合并将保留本地数据并追加新题目。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importFromFile(uri, ImportMode.MERGE)
                    pendingImportUri = null
                }) { Text("合并") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.importFromFile(uri, ImportMode.OVERWRITE)
                    pendingImportUri = null
                }) { Text("覆盖") }
            }
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "恢复默认",
            message = "此操作将清除所有自定义题目并恢复为默认种子题库，不可撤销！",
            confirmText = "确认重置",
            isDanger = true,
            onConfirm = { viewModel.resetData(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }
}

@Composable
private fun WebDavSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var selectedBackupFile by remember { mutableStateOf<WebDavFile?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WebDAV 备份", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(value = uiState.webDavUrl, onValueChange = viewModel::setWebDavUrl, label = { Text("服务器地址") }, placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = uiState.webDavUsername, onValueChange = viewModel::setWebDavUsername, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = uiState.webDavPassword, onValueChange = viewModel::setWebDavPassword, label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "切换密码可见")
                    }
                }
            )
            OutlinedTextField(value = uiState.webDavPath, onValueChange = viewModel::setWebDavPath, label = { Text("备份路径") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.testWebDav() }, enabled = !uiState.webDavTesting) {
                    if (uiState.webDavTesting) CircularProgressIndicator(Modifier.height(16.dp).width(16.dp))
                    else Text("测试连接")
                }
                Button(onClick = { viewModel.saveWebDavConfig() }) { Text("保存配置") }
            }

            uiState.webDavMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = if (msg.contains("成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()

            // 手动备份恢复
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.backupNow() }, enabled = !uiState.webDavBackingUp) {
                    if (uiState.webDavBackingUp) CircularProgressIndicator(Modifier.height(16.dp).width(16.dp))
                    else Text("立即备份")
                }
                OutlinedButton(onClick = { viewModel.loadWebDavBackups() }, enabled = !uiState.webDavRestoring) {
                    if (uiState.webDavRestoring) CircularProgressIndicator(Modifier.height(16.dp).width(16.dp))
                    else Text("从 WebDAV 恢复")
                }
            }
        }
    }

    // 备份文件选择弹窗
    if (uiState.showBackupPicker) {
        if (uiState.webDavBackupFiles.isEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupPicker() },
                title = { Text("WebDAV 恢复") },
                text = { Text("服务器上无备份文件") },
                confirmButton = { TextButton(onClick = { viewModel.dismissBackupPicker() }) { Text("确定") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupPicker() },
                title = { Text("选择备份文件") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.webDavBackupFiles.forEach { file ->
                            TextButton(
                                onClick = { selectedBackupFile = file; viewModel.dismissBackupPicker() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { viewModel.dismissBackupPicker() }) { Text("取消") } }
            )
        }
    }

    // 导入模式选择弹窗
    selectedBackupFile?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedBackupFile = null },
            title = { Text("确认恢复") },
            text = { Text("将使用 ${file.name} 恢复数据。\n\n覆盖：替换现有数据\n合并：保留本地数据并追加新题目") },
            confirmButton = {
                TextButton(onClick = { viewModel.restoreFromWebDav(file, ImportMode.MERGE); selectedBackupFile = null }) { Text("合并") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.restoreFromWebDav(file, ImportMode.OVERWRITE); selectedBackupFile = null }) { Text("覆盖") }
            }
        )
    }
}
