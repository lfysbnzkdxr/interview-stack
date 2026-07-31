package com.example.interviewhelper.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.ui.common.AppMarkdownText
import com.example.interviewhelper.ui.common.ErrorState
import com.example.interviewhelper.ui.common.LoadingState
import com.example.interviewhelper.ui.practice.components.CategoryTag
import com.example.interviewhelper.ui.practice.components.DifficultyTag

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 分类 Tab
        CategoryTabs(
            categories = uiState.categories,
            activeCategory = uiState.activeCategory,
            questions = uiState.questions,
            onSelect = viewModel::selectCategory
        )

        // 内容
        when {
            uiState.loading -> LoadingState()
            uiState.loadError != null -> ErrorState(
                message = uiState.loadError!!,
                onRetry = viewModel::reload
            )
            else -> {
                val filtered = if (uiState.activeCategory == null) {
                    uiState.questions
                } else {
                    uiState.questions.filter { it.category == uiState.activeCategory }
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "该分类下暂无题目",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { question ->
                            QuestionItem(
                                question = question,
                                isExpanded = uiState.expandedId == question.id,
                                onToggle = { viewModel.toggleExpand(question.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<String>,
    activeCategory: String?,
    questions: List<QuestionEntity>,
    onSelect: (String?) -> Unit
) {
    val tabs = listOf(null) + categories  // null = "全部"
    val selectedIndex = if (activeCategory == null) 0 else tabs.indexOf(activeCategory).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, category ->
            val label = category ?: "全部"
            val count = if (category == null) questions.size
                else questions.count { it.category == category }

            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(category) },
                text = {
                    Text(
                        text = "$label ($count)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun QuestionItem(
    question: QuestionEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DifficultyTag(question.difficulty)
                Spacer(modifier = Modifier.width(8.dp))
                CategoryTag(question.category)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleSmall,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    if (question.dialog.isBlank()) {
                        Text(
                            text = "暂无回答",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AppMarkdownText(markdown = question.dialog)
                    }
                }
            }
        }
    }
}
