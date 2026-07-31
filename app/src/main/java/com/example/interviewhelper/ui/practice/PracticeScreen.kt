package com.example.interviewhelper.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.ui.common.ErrorState
import com.example.interviewhelper.ui.common.LoadingState
import com.example.interviewhelper.ui.practice.components.FlashCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 筛选栏
        FilterBar(
            difficulty = uiState.difficulty,
            category = uiState.category,
            categories = uiState.categories,
            onDifficultyChange = viewModel::setDifficulty,
            onCategoryChange = viewModel::setCategory
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 内容区域
        when {
            uiState.loading -> LoadingState()
            uiState.loadError != null -> ErrorState(
                message = uiState.loadError!!,
                onRetry = viewModel::reload
            )
            uiState.currentQuestion == null -> EmptyState()
            else -> {
                // FlashCard
                FlashCard(
                    question = uiState.currentQuestion!!,
                    isFlipped = uiState.isFlipped,
                    onFlip = viewModel::flip,
                    onNext = viewModel::next,
                    onPrev = viewModel::prev,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 导航按钮
                NavButtons(
                    progress = uiState.progress,
                    isFirst = uiState.isFirst,
                    isLast = uiState.isLast,
                    onPrev = viewModel::prev,
                    onNext = viewModel::next
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    difficulty: String,
    category: String,
    categories: List<String>,
    onDifficultyChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 难度下拉
        var difficultyExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = difficultyExpanded,
            onExpandedChange = { difficultyExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = difficulty,
                onValueChange = {},
                readOnly = true,
                label = { Text("难度") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = difficultyExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = difficultyExpanded,
                onDismissRequest = { difficultyExpanded = false }
            ) {
                listOf("全部", "初级", "中级", "高级").forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            onDifficultyChange(item)
                            difficultyExpanded = false
                        }
                    )
                }
            }
        }

        // 分类下拉
        var categoryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                label = { Text("分类") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                (listOf("全部") + categories).forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            onCategoryChange(item)
                            categoryExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavButtons(
    progress: String,
    isFirst: Boolean,
    isLast: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrev,
            enabled = !isFirst
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一题")
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = progress,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = onNext,
            enabled = !isLast
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一题")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "题库为空",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请先创建一些面试题目",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
