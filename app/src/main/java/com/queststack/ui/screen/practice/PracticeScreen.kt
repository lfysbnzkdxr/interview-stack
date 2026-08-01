package com.queststack.ui.screen.practice

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 难度数值 → 展示文案（1=简单 2=中等 3=困难，非法值回退"简单"） */
private fun difficultyLabel(d: Int): String = when (d) {
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
fun PracticeScreen(
    onStart: (Long) -> Unit,
    viewModel: PracticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏由 MainScreen 的 Scaffold topBar 槽位统一渲染（标题"练题"），本页不再自绘
        PracticeFilterBar(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            difficulty = uiState.difficulty,
            onSelectCategory = viewModel::selectCategory,
            onSelectDifficulty = viewModel::selectDifficulty,
            onShuffle = viewModel::shuffle,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.loading -> LoadingPlaceholder()
                uiState.empty -> EmptyPlaceholder()
                else -> uiState.current?.let { item ->
                    PracticeCard(
                        item = item,
                        categories = uiState.categories,
                        onStart = { onStart(item.question.id) },
                    )
                }
            }
        }
    }
}

/** 筛选栏：分类下拉（全部 + 各分类）+ 难度四档 chips + 右侧"换一题" */
@Composable
private fun PracticeFilterBar(
    categories: List<Category>,
    selectedCategoryId: Long?,
    difficulty: Int?,
    onSelectCategory: (Long?) -> Unit,
    onSelectDifficulty: (Int?) -> Unit,
    onShuffle: () -> Unit,
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var categoryButtonHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val currentCategoryName =
        if (selectedCategoryId == null) "全部"
        else categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "全部"

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
                    PracticeCategoryDropdownPanel(
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
        // 难度筛选 chips（null = 全部）+ 右侧"换一题"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DifficultyChip("全部", selected = difficulty == null, onClick = { onSelectDifficulty(null) })
                DifficultyChip("简单", selected = difficulty == 1, onClick = { onSelectDifficulty(1) })
                DifficultyChip("中等", selected = difficulty == 2, onClick = { onSelectDifficulty(2) })
                DifficultyChip("困难", selected = difficulty == 3, onClick = { onSelectDifficulty(3) })
            }
            IconButton(onClick = onShuffle) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = "换一题",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
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

/** 分类下拉面板（自绘，与题库页一致） */
@Composable
private fun PracticeCategoryDropdownPanel(
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
            DropdownRow("全部", isSelected = selectedCategoryId == null, onClick = { onSelect(null) })
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

/** 随机题目大卡片：分类/难度标签 + 大字标题 + 追问轮数 + 开始练习按钮 */
@Composable
private fun PracticeCard(
    item: QuestionWithRounds,
    categories: List<Category>,
    onStart: () -> Unit,
) {
    val question = item.question
    val categoryName = categories.firstOrNull { it.id == question.categoryId }?.name ?: "未分类"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(onClick = onStart),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = categoryName,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = difficultyLabel(question.difficulty),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = difficultyColor(question.difficulty),
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(difficultyColor(question.difficulty).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = question.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${item.rounds.size} 轮追问",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(text = "开始练习", fontSize = 15.sp)
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
private fun EmptyPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = MiuixIcons.ListView,
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
                text = "去「添加」页创建第一道题吧",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
            )
        }
    }
}
