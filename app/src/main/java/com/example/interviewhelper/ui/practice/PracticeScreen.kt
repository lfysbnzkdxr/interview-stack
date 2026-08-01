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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.ui.common.ErrorState
import com.example.interviewhelper.ui.common.FilterBar
import com.example.interviewhelper.ui.common.LoadingState
import com.example.interviewhelper.ui.common.PageHeader
import com.example.interviewhelper.ui.practice.components.FlashCard

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // HyperOS 风格页面大标题（自带水平 16dp 内边距）
        PageHeader(title = "练题")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 筛选栏（共享组件：分类 + 难度下拉）
            FilterBar(
                categories = uiState.categories,
                selectedCategory = uiState.category,
                onCategoryChange = viewModel::setCategory,
                selectedDifficulty = uiState.difficulty,
                onDifficultyChange = viewModel::setDifficulty
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
                        onNext = viewModel::next,
                        onPrev = viewModel::prev,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 导航按钮：上一题 / 翻转显示答案 / 下一题
                    NavButtons(
                        isFirst = uiState.isFirst,
                        isLast = uiState.isLast,
                        onPrev = viewModel::prev,
                        onFlip = viewModel::flip,
                        onNext = viewModel::next
                    )
                }
            }
        }
    }
}

@Composable
private fun NavButtons(
    isFirst: Boolean,
    isLast: Boolean,
    onPrev: () -> Unit,
    onFlip: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrev,
            enabled = !isFirst,
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一题")
        }

        Spacer(modifier = Modifier.width(16.dp))

        Button(
            onClick = onFlip,
            modifier = Modifier.height(48.dp)
        ) {
            Text("翻转显示答案")
        }

        Spacer(modifier = Modifier.width(16.dp))

        FilledTonalIconButton(
            onClick = onNext,
            enabled = !isLast,
            shape = RoundedCornerShape(24.dp)
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
