package com.queststack.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.queststack.data.db.Category
import com.queststack.data.db.QuestionWithRounds
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 难度数值 → 展示文案（1=简单 2=中等 3=困难，非法值回退"简单"） */
fun difficultyLabel(d: Int): String = when (d) {
    1 -> "简单"
    2 -> "中等"
    3 -> "困难"
    else -> "简单"
}

private fun difficultyColor(d: Int): Color = when (d) {
    1 -> Color(0xFF00A871)
    2 -> Color(0xFFE8890C)
    3 -> Color(0xFFE5484D)
    else -> Color(0xFF00A871)
}

@Composable
fun LibraryScreen(
    onQuestionClick: (Long) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<QuestionWithRounds?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryFilterBar(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            difficulty = uiState.difficulty,
            onSelectCategory = viewModel::selectCategory,
            onSelectDifficulty = viewModel::selectDifficulty,
        )
        when {
            uiState.loading -> LoadingPlaceholder()
            uiState.questions.isEmpty() -> EmptyPlaceholder(
                hasFilter = uiState.selectedCategoryId != null || uiState.difficulty != null,
            )
            else -> QuestionList(
                questions = uiState.questions,
                categories = uiState.categories,
                onQuestionClick = onQuestionClick,
                onDelete = { deleteTarget = it },
            )
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onConfirm = {
                viewModel.deleteQuestion(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 筛选栏：分类下拉 + 难度 chips */
@Composable
private fun LibraryFilterBar(
    categories: List<Category>,
    selectedCategoryId: Long?,
    difficulty: Int?,
    onSelectCategory: (Long?) -> Unit,
    onSelectDifficulty: (Int?) -> Unit,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var categoryButtonHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val currentCategoryName =
        if (selectedCategoryId == null) "全部分类"
        else categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "全部分类"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 分类选择（DropdownMenu）
        Box(modifier = Modifier.onSizeChanged { categoryButtonHeight = it.height }) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .clickable { categoryMenuExpanded = true }
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentCategoryName,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    maxLines = 1,
                )
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            if (categoryMenuExpanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, categoryButtonHeight + with(density) { 4.dp.roundToPx() }),
                    onDismissRequest = { categoryMenuExpanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    CategoryDropdownPanel(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = { id ->
                            onSelectCategory(id)
                            categoryMenuExpanded = false
                        },
                    )
                }
            }
        }
        // 难度筛选 chips（null = 全部）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DifficultyChip("全部", selected = difficulty == null, onClick = { onSelectDifficulty(null) })
            DifficultyChip("简单", selected = difficulty == 1, onClick = { onSelectDifficulty(1) })
            DifficultyChip("中等", selected = difficulty == 2, onClick = { onSelectDifficulty(2) })
            DifficultyChip("困难", selected = difficulty == 3, onClick = { onSelectDifficulty(3) })
        }
    }
}

@Composable
private fun DifficultyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurfaceContainer,
        )
    }
}

/** 分类下拉面板（自绘，miuix 0.9.3 无公开 DropdownMenu 弹层组件） */
@Composable
private fun CategoryDropdownPanel(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .dropShadow(
                shape = RoundedCornerShape(16.dp),
                shadow = Shadow(radius = 12.dp, color = Color.Black, alpha = 0.15f),
            ),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            DropdownRow("全部分类", isSelected = selectedCategoryId == null, onClick = { onSelect(null) })
            categories.forEach { category ->
                DropdownRow(
                    text = category.name,
                    isSelected = category.id == selectedCategoryId,
                    onClick = { onSelect(category.id) },
                )
            }
        }
    }
}

@Composable
private fun DropdownRow(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = if (isSelected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isSelected) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun QuestionList(
    questions: List<QuestionWithRounds>,
    categories: List<Category>,
    onQuestionClick: (Long) -> Unit,
    onDelete: (QuestionWithRounds) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(questions, key = { it.question.id }) { item ->
            QuestionCard(
                item = item,
                categories = categories,
                onQuestionClick = { onQuestionClick(item.question.id) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun QuestionCard(
    item: QuestionWithRounds,
    categories: List<Category>,
    onQuestionClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val question = item.question
    val categoryName = categories.firstOrNull { it.id == question.categoryId }?.name ?: "未分类"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onQuestionClick),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // TODO: 下一里程碑接入编辑页
                IconButton(onClick = { /* TODO: 编辑题目 */ }) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = "编辑",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = categoryName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Text(
                    text = difficultyLabel(question.difficulty),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = difficultyColor(question.difficulty),
                )
                Text(
                    text = "${item.rounds.size} 轮追问",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    target: QuestionWithRounds,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 28.dp,
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Column {
                Text(
                    text = "删除题目",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "确定要删除「${target.question.title}」吗？该题及其追问轮次都会被移除，且无法恢复。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColorsPrimary(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = "删除", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "加载中…",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun EmptyPlaceholder(hasFilter: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = MiuixIcons.GridView,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无题目",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasFilter) "换个筛选条件试试" else "去「添加」页创建第一道题吧",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
            )
        }
    }
}
