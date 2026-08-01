package com.example.interviewhelper.ui.create

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.ui.common.AppMarkdownText
import com.example.interviewhelper.ui.common.PageHeader

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
        // 根布局不再整体加 16dp 内边距：PageHeader 自带水平留白，
        // 各模式内部再统一 16dp 水平 / 8dp 垂直留白，避免双重空白
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
    PageHeader(title = "创建问答", subtitle = "用 AI 生成或手动录入面试问答")

    // 其余内容统一水平 16dp、垂直 8dp 留白，与 PageHeader 自带内边距衔接
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 问题输入
        OutlinedTextField(
            value = uiState.question,
            onValueChange = onQuestionChange,
            label = { Text("面试问题 *") },
            placeholder = { Text("例如：什么是 RAG？") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = MaterialTheme.shapes.small
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 答案要点（可选）
        OutlinedTextField(
            value = uiState.answer,
            onValueChange = onAnswerChange,
            label = { Text("答案要点（可选）") },
            placeholder = { Text("输入关键要点，AI 将据此生成完整回答") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = MaterialTheme.shapes.small
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
                    .menuAnchor(),
                shape = MaterialTheme.shapes.small
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
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
                // 主操作按钮统一胶囊 + 48dp 高度
                Button(
                    onClick = if (uiState.answer.isBlank()) onGenerate else onOptimize,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (uiState.answer.isBlank()) "AI 生成回答" else "AI 优化回答")
                }
                OutlinedButton(
                    onClick = onManualSave,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("跳过 AI，直接保存")
                }
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

    PageHeader(title = "预览 & 编辑")

    // 其余内容统一水平 16dp、垂直 8dp 留白，与 PageHeader 自带内边距衔接
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 问题标题
        Text("问题", style = MaterialTheme.typography.labelLarge)
        Text(
            text = preview.optimizedQuestion,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        // 难度选择：胶囊 FilterChip（选中态 primary 底 / 未选中 surfaceContainer 底）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("难度：", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("初级", "中级", "高级").forEach { diff ->
                    FilterChip(
                        selected = preview.difficulty == diff,
                        onClick = { onDifficultyChange(diff) },
                        label = { Text(diff) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(0.dp, Color.Transparent)
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
            maxLines = Int.MAX_VALUE,
            shape = MaterialTheme.shapes.small
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Markdown 预览：surfaceContainerLow 卡片容器 + 24dp 圆角
        Text("渲染预览", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                AppMarkdownText(markdown = preview.dialog)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮：统一胶囊 + 48dp 高度
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("返回修改") }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(48.dp),
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
}
