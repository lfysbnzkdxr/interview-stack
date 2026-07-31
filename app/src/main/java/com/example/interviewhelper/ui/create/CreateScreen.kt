package com.example.interviewhelper.ui.create

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.ui.common.AppMarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    viewModel: CreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("保存成功！")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.preview == null) {
                // 表单模式
                FormMode(
                    uiState = uiState,
                    onQuestionChange = viewModel::setQuestion,
                    onAnswerChange = viewModel::setAnswer,
                    onCategoryChange = viewModel::setCategory,
                    onGenerate = viewModel::handleGenerate,
                    onOptimize = viewModel::handleOptimize,
                    onManualSave = viewModel::handleManualSave,
                    onNewCategory = viewModel::showNewCategoryInput,
                    onConfirmNewCategory = viewModel::confirmNewCategory,
                    onCancelNewCategory = viewModel::cancelNewCategory,
                    onNewCategoryNameChange = viewModel::setNewCategoryName
                )
            } else {
                // 预览模式
                PreviewMode(
                    uiState = uiState,
                    onDialogChange = viewModel::updatePreviewDialog,
                    onDifficultyChange = viewModel::updatePreviewDifficulty,
                    onSave = viewModel::handleSave,
                    onBack = viewModel::cancelPreview
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormMode(
    uiState: CreateUiState,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onOptimize: () -> Unit,
    onManualSave: () -> Unit,
    onNewCategory: () -> Unit,
    onConfirmNewCategory: () -> Unit,
    onCancelNewCategory: () -> Unit,
    onNewCategoryNameChange: (String) -> Unit
) {
    Text("创建问答", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))

    // 问题输入
    OutlinedTextField(
        value = uiState.question,
        onValueChange = onQuestionChange,
        label = { Text("面试问题 *") },
        placeholder = { Text("例如：什么是 RAG？") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    Spacer(modifier = Modifier.height(12.dp))

    // 答案要点（可选）
    OutlinedTextField(
        value = uiState.answer,
        onValueChange = onAnswerChange,
        label = { Text("答案要点（可选）") },
        placeholder = { Text("输入关键要点，AI 将据此生成完整回答") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
    )
    Spacer(modifier = Modifier.height(12.dp))

    // 分类选择
    var categoryExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = it }
    ) {
        OutlinedTextField(
            value = uiState.selectedCategory.ifBlank { "选择分类" },
            onValueChange = {},
            readOnly = true,
            label = { Text("分类 *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false }
        ) {
            uiState.categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        onCategoryChange(cat)
                        categoryExpanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ 新建分类", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onNewCategory()
                    categoryExpanded = false
                }
            )
        }
    }

    // 新建分类输入
    if (uiState.showNewCategory) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.newCategoryName,
                onValueChange = onNewCategoryNameChange,
                label = { Text("新分类名称") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirmNewCategory) { Text("确定") }
            TextButton(onClick = onCancelNewCategory) { Text("取消") }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 按钮组
    if (uiState.loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(uiState.step, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = if (uiState.answer.isBlank()) onGenerate else onOptimize,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.answer.isBlank()) "AI 生成回答" else "AI 优化回答")
            }
            OutlinedButton(
                onClick = onManualSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("跳过 AI，直接保存")
            }
        }
    }
}

@Composable
private fun PreviewMode(
    uiState: CreateUiState,
    onDialogChange: (String) -> Unit,
    onDifficultyChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val preview = uiState.preview ?: return

    Text("预览 & 编辑", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))

    // 问题标题
    Text("问题", style = MaterialTheme.typography.labelLarge)
    Text(
        text = preview.optimizedQuestion,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(12.dp))

    // 难度选择
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("难度：", style = MaterialTheme.typography.labelLarge)
        listOf("初级", "中级", "高级").forEach { diff ->
            TextButton(onClick = { onDifficultyChange(diff) }) {
                Text(
                    text = diff,
                    color = if (preview.difficulty == diff)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    // 对话内容编辑
    OutlinedTextField(
        value = preview.dialog,
        onValueChange = onDialogChange,
        label = { Text("对话内容") },
        modifier = Modifier.fillMaxWidth().height(200.dp),
        maxLines = Int.MAX_VALUE
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Markdown 预览
    Text("渲染预览", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    AppMarkdownText(markdown = preview.dialog)

    Spacer(modifier = Modifier.height(24.dp))

    // 操作按钮
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f)
        ) { Text("返回修改") }

        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            enabled = !uiState.loading
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
            } else {
                Text("保存到题库")
            }
        }
    }
}
