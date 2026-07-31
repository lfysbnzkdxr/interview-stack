package com.example.interviewhelper.ui.bank

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.ui.common.AppMarkdownText
import com.example.interviewhelper.ui.common.ConfirmDialog
import com.example.interviewhelper.ui.common.ErrorState
import com.example.interviewhelper.ui.common.LoadingState
import com.example.interviewhelper.ui.practice.components.CategoryTag
import com.example.interviewhelper.ui.practice.components.DifficultyTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(
    viewModel: BankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.pagedQuestions.collectAsLazyPagingItems()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 筛选栏
        FilterBar(uiState = uiState, viewModel = viewModel)
        Spacer(modifier = Modifier.height(8.dp))

        // 统计
        Text(
            text = "共 ${uiState.totalCount} 道题",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 批量操作栏
        if (uiState.selectedIds.isNotEmpty()) {
            val allVisibleIds = pagingItems.itemSnapshotList.items.mapNotNull { it?.id }
            val allSelected = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in uiState.selectedIds }
            BatchBar(
                count = uiState.selectedIds.size,
                allSelected = allSelected,
                onSelectAll = { viewModel.selectAll(allVisibleIds) },
                categories = uiState.categories,
                onBatchMoveCategory = viewModel::batchMoveCategory,
                onBatchDelete = { showBatchDeleteDialog = true },
                onBatchHide = viewModel::batchHide,
                onBatchUnhide = viewModel::batchUnhide,
                onClear = viewModel::clearSelection
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 内容
        when {
            uiState.loading -> LoadingState()
            uiState.loadError != null -> ErrorState(message = uiState.loadError!!, onRetry = viewModel::reload)
            pagingItems.itemCount == 0 -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("没有匹配的题目", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = pagingItems.itemCount) { index ->
                        val question = pagingItems[index]
                        if (question != null) {
                            if (uiState.editId == question.id) {
                                EditCard(uiState = uiState, viewModel = viewModel)
                            } else {
                                QuestionCard(
                                    question = question,
                                    isSelected = question.id in uiState.selectedIds,
                                    isExpanded = uiState.expandedId == question.id,
                                    onToggleSelect = { viewModel.toggleSelect(question.id) },
                                    onToggleExpand = { viewModel.toggleExpand(question.id) },
                                    onEdit = { viewModel.startEdit(question) },
                                    onDelete = { showDeleteDialog = true },
                                    onToggleHide = { viewModel.toggleHideQuestion(question.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除题目",
            message = "确定要删除这道题目吗？此操作不可撤销。",
            confirmText = "删除",
            isDanger = true,
            onConfirm = {
                uiState.expandedId?.let { viewModel.deleteQuestion(it) }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    if (showBatchDeleteDialog) {
        ConfirmDialog(
            title = "批量删除",
            message = "确定要删除选中的 ${uiState.selectedIds.size} 道题目吗？",
            confirmText = "删除",
            isDanger = true,
            onConfirm = { viewModel.batchDelete(); showBatchDeleteDialog = false },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(uiState: BankUiState, viewModel: BankViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("搜索题目...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 分类筛选
            var catExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = uiState.filterCategory, onValueChange = {}, readOnly = true,
                    label = { Text("分类") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    (listOf("全部") + uiState.categories).forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = { viewModel.setFilterCategory(item); catExpanded = false })
                    }
                }
            }
            // 难度筛选
            var diffExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = diffExpanded, onExpandedChange = { diffExpanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = uiState.filterDifficulty, onValueChange = {}, readOnly = true,
                    label = { Text("难度") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(diffExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(expanded = diffExpanded, onDismissRequest = { diffExpanded = false }) {
                    listOf("全部", "初级", "中级", "高级").forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = { viewModel.setFilterDifficulty(item); diffExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    categories: List<String>,
    onBatchMoveCategory: (String) -> Unit,
    onBatchDelete: () -> Unit,
    onBatchHide: () -> Unit,
    onBatchUnhide: () -> Unit,
    onClear: () -> Unit
) {
    var showCatMenu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allSelected, onCheckedChange = { onSelectAll() })
                    Text("已选 $count 道", style = MaterialTheme.typography.labelLarge)
                }
                Row {
                    TextButton(onClick = onBatchHide) { Text("隐藏") }
                    TextButton(onClick = onBatchUnhide) { Text("取消隐藏") }
                    TextButton(onClick = { showCatMenu = true }) { Text("移动分类") }
                    TextButton(onClick = onBatchDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onClear) { Text("取消") }
                }
            }
            DropdownMenu(expanded = showCatMenu, onDismissRequest = { showCatMenu = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = { onBatchMoveCategory(cat); showCatMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: QuestionEntity, isSelected: Boolean, isExpanded: Boolean,
    onToggleSelect: () -> Unit, onToggleExpand: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onToggleHide: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpand)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                DifficultyTag(question.difficulty)
                Spacer(Modifier.width(4.dp))
                CategoryTag(question.category)
                if (question.hidden) { Spacer(Modifier.width(4.dp)); Text("已隐藏", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                if (question.builtIn) { Spacer(Modifier.width(4.dp)); Text("内置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(text = question.question, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (question.dialog.isNotBlank()) AppMarkdownText(markdown = question.dialog)
                    else Text("暂无回答", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
                        IconButton(onClick = onToggleHide) { Icon(if (question.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, "隐藏") }
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCard(uiState: BankUiState, viewModel: BankViewModel) {
    val form = uiState.editForm
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = form.question, onValueChange = { viewModel.updateEditForm(form.copy(question = it)) }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = form.category, onValueChange = { viewModel.updateEditForm(form.copy(category = it)) }, label = { Text("分类") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = form.difficulty, onValueChange = { viewModel.updateEditForm(form.copy(difficulty = it)) }, label = { Text("难度") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = form.dialog, onValueChange = { viewModel.updateEditForm(form.copy(dialog = it)) }, label = { Text("对话内容") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = Int.MAX_VALUE)

            // AI 操作
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::handleAiPolish, enabled = !uiState.aiLoading) {
                    if (uiState.aiLoading) CircularProgressIndicator(Modifier.height(16.dp).width(16.dp))
                    else Text("AI 润色对话")
                }
                OutlinedButton(onClick = { viewModel.setShowSubQuestion(true) }) { Text("+ 新增子问题") }
            }

            if (uiState.showSubQuestion) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = uiState.subQuestionText, onValueChange = viewModel::setSubQuestionText, placeholder = { Text("输入子问题") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = viewModel::handleAppendSub, enabled = !uiState.aiLoading) { Text("生成") }
                }
            }

            uiState.aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            // AI 润色结果对比
            uiState.aiPolishResult?.let { polished ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text("AI 润色结果", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        AppMarkdownText(markdown = polished)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = viewModel::rejectPolish) { Text("保留原版") }
                            Button(onClick = viewModel::acceptPolish) { Text("采用润色版") }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = viewModel::cancelEdit) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::saveEdit) { Text("保存") }
            }
        }
    }
}
