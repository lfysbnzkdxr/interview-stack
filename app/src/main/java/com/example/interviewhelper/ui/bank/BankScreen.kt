package com.example.interviewhelper.ui.bank

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.ui.common.AppMarkdownText
import com.example.interviewhelper.ui.common.CategoryTag
import com.example.interviewhelper.ui.common.ConfirmDialog
import com.example.interviewhelper.ui.common.DifficultyTag
import com.example.interviewhelper.ui.common.ErrorState
import com.example.interviewhelper.ui.common.FilterBar
import com.example.interviewhelper.ui.common.LoadingState
import kotlin.math.roundToInt

// 难度选项，与全局难度枚举保持一致（初级/中级/高级）
private val DIFFICULTY_OPTIONS = listOf("全部", "初级", "中级", "高级")

@Composable
fun BankScreen(
    viewModel: BankViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagingItems = viewModel.pagedQuestions.collectAsLazyPagingItems()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 多选模式下按返回键退出多选
    if (uiState.selectionMode) {
        BackHandler { viewModel.exitSelectionMode() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索 + 仅看可见开关（同一行）
        FilterBar(
            showSearch = true,
            searchQuery = uiState.searchQuery,
            onSearchChange = viewModel::setSearchQuery,
            showVisibleSwitch = true,
            visibleOnly = uiState.visibleOnly,
            onVisibleOnlyChange = viewModel::setVisibleOnly,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 分类胶囊 + 难度选择（再次点击当前分类展开，各分类独立记忆难度）
        CategoryTabs(
            categories = uiState.categories,
            activeCategory = uiState.filterCategory,
            categoryCounts = uiState.categoryCounts,
            categoryDifficultyCounts = uiState.categoryDifficultyCounts,
            perCategoryDifficulty = uiState.perCategoryDifficulty,
            totalCount = uiState.totalCount,
            difficultyMenuOpenFor = uiState.difficultyMenuOpenFor,
            onCategoryClick = viewModel::onCategoryClick,
            onDifficultySelect = viewModel::setCategoryDifficulty,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 多选操作栏
        if (uiState.selectionMode) {
            val allVisibleIds = pagingItems.itemSnapshotList.items.mapNotNull { it?.id }
            val allSelected = allVisibleIds.isNotEmpty() && allVisibleIds.all { it in uiState.selectedIds }
            SelectionBar(
                count = uiState.selectedIds.size,
                allSelected = allSelected,
                categories = uiState.categories,
                onSelectAll = { viewModel.selectAll(allVisibleIds) },
                onBatchMoveCategory = viewModel::batchMoveCategory,
                onBatchDelete = { showBatchDeleteDialog = true },
                onBatchHide = viewModel::batchHide,
                onBatchUnhide = viewModel::batchUnhide,
                onDone = viewModel::exitSelectionMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 内容
        when {
            uiState.loading -> LoadingState(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            uiState.loadError != null -> ErrorState(
                message = uiState.loadError!!,
                onRetry = viewModel::reload,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            pagingItems.itemCount == 0 -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("没有匹配的题目", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = pagingItems.itemCount) { index ->
                        val question = pagingItems[index]
                        if (question != null) {
                            if (uiState.editId == question.id) {
                                EditCard(uiState = uiState, viewModel = viewModel)
                            } else {
                                SwipeQuestionRow(
                                    question = question,
                                    isSelected = question.id in uiState.selectedIds,
                                    isExpanded = uiState.expandedId == question.id,
                                    selectionMode = uiState.selectionMode,
                                    onToggleSelect = { viewModel.toggleSelect(question.id) },
                                    onToggleExpand = { viewModel.toggleExpand(question.id) },
                                    onEnterSelection = { viewModel.enterSelectionMode(question.id) },
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
private fun CategoryTabs(
    categories: List<String>,
    activeCategory: String,
    categoryCounts: Map<String, Int>,
    categoryDifficultyCounts: Map<String, Map<String, Int>>,
    perCategoryDifficulty: Map<String, String>,
    totalCount: Int,
    difficultyMenuOpenFor: String?,
    onCategoryClick: (String) -> Unit,
    onDifficultySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("全部") + categories

    Column(modifier = modifier) {
        // 分类胶囊：横向滚动，选中主色填充、未选中浅灰容器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { category ->
                val difficulty = perCategoryDifficulty[category] ?: "全部"
                val count = displayCount(category, difficulty, categoryCounts, categoryDifficultyCounts, totalCount)
                val selected = category == activeCategory
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { onCategoryClick(category) }
                ) {
                    Text(
                        text = "$category ($count)",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 难度选择：再次点击当前分类时展开，各分类独立记忆
        AnimatedVisibility(
            visible = difficultyMenuOpenFor != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentDifficulty = difficultyMenuOpenFor?.let { perCategoryDifficulty[it] } ?: "全部"
                DIFFICULTY_OPTIONS.forEach { option ->
                    val selected = option == currentDifficulty
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = { onDifficultySelect(option) }
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 分类胶囊题数：已选难度时显示该分类×难度组合计数，否则显示分类总计数 */
private fun displayCount(
    category: String,
    difficulty: String,
    categoryCounts: Map<String, Int>,
    categoryDifficultyCounts: Map<String, Map<String, Int>>,
    totalCount: Int
): Int {
    if (category == "全部") {
        return if (difficulty == "全部") {
            totalCount
        } else {
            categoryDifficultyCounts.values.sumOf { it[difficulty] ?: 0 }
        }
    }
    return if (difficulty == "全部") {
        categoryCounts[category] ?: 0
    } else {
        categoryDifficultyCounts[category]?.get(difficulty) ?: 0
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    categories: List<String>,
    onSelectAll: () -> Unit,
    onBatchMoveCategory: (String) -> Unit,
    onBatchDelete: () -> Unit,
    onBatchHide: () -> Unit,
    onBatchUnhide: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCatMenu by remember { mutableStateOf(false) }
    // 多选操作栏：primaryContainer 淡蓝底 + 24dp 圆角，内部按钮保持胶囊
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("已选 $count 道", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onDone) { Text("完成") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
            ) {
                TextButton(onClick = onSelectAll) { Text(if (allSelected) "取消全选" else "全选") }
                TextButton(onClick = onBatchHide) { Text("隐藏") }
                TextButton(onClick = onBatchUnhide) { Text("取消隐藏") }
                TextButton(onClick = { showCatMenu = true }) { Text("移动分类") }
                TextButton(onClick = onBatchDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
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

// 左滑操作区状态：关闭（完全覆盖）↔ 打开（露出右侧操作区）
private enum class RevealValue { Closed, Open }

/** 单个操作按钮宽度（编辑/隐藏/删除 三个按钮） */
private val RevealActionWidth = 72.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeQuestionRow(
    question: QuestionEntity,
    isSelected: Boolean,
    isExpanded: Boolean,
    selectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onEnterSelection: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleHide: () -> Unit
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { (RevealActionWidth * 3).toPx() }

    // 左滑状态：关闭(0) ↔ 打开(露出操作区)，横向拖动与长按多选天然区分
    val revealState = remember(question.id, actionWidthPx) {
        AnchoredDraggableState(
            initialValue = RevealValue.Closed,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 200.dp.toPx() } },
            animationSpec = tween()
        ).apply {
            updateAnchors(
                DraggableAnchors {
                    RevealValue.Closed at 0f
                    RevealValue.Open at -actionWidthPx
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 底层操作区：编辑/隐藏/删除，右对齐露出
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium),
            horizontalArrangement = Arrangement.End
        ) {
            RevealActionButton(
                background = MaterialTheme.colorScheme.primaryContainer,
                onClick = onEdit
            ) {
                Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            RevealActionButton(
                background = MaterialTheme.colorScheme.primaryContainer,
                onClick = onToggleHide
            ) {
                Icon(
                    if (question.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    "隐藏",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            RevealActionButton(
                background = MaterialTheme.colorScheme.error,
                onClick = onDelete
            ) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onError)
            }
        }

        // 上层题目卡片：随左滑偏移露出操作区
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(revealState.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(revealState, Orientation.Horizontal)
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onToggleExpand() },
                    onLongClick = { if (selectionMode) onToggleSelect() else onEnterSelection() }
                ),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DifficultyTag(question.difficulty)
                    Spacer(Modifier.width(4.dp))
                    CategoryTag(question.category)
                    if (question.hidden) { Spacer(Modifier.width(4.dp)); Text("已隐藏", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    if (question.builtIn) { Spacer(Modifier.width(4.dp)); Text("内置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.weight(1f))
                    // 多选模式：复选框出现在行右端
                    if (selectionMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                    }
                }
                Text(text = question.question, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))

                AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        if (question.dialog.isNotBlank()) AppMarkdownText(markdown = question.dialog)
                        else Text("暂无回答", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RevealActionButton(
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(RevealActionWidth)
            .fillMaxHeight()
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun EditCard(uiState: BankUiState, viewModel: BankViewModel) {
    val form = uiState.editForm
    // 编辑卡片：secondaryContainer 半透明底 + 24dp 圆角，输入框 16dp 圆角
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = form.question, onValueChange = { viewModel.updateEditForm(form.copy(question = it)) }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = form.category, onValueChange = { viewModel.updateEditForm(form.copy(category = it)) }, label = { Text("分类") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
                OutlinedTextField(value = form.difficulty, onValueChange = { viewModel.updateEditForm(form.copy(difficulty = it)) }, label = { Text("难度") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
            }
            OutlinedTextField(value = form.dialog, onValueChange = { viewModel.updateEditForm(form.copy(dialog = it)) }, label = { Text("对话内容") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = Int.MAX_VALUE, shape = MaterialTheme.shapes.small)

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
                    OutlinedTextField(value = uiState.subQuestionText, onValueChange = viewModel::setSubQuestionText, placeholder = { Text("输入子问题") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small)
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
