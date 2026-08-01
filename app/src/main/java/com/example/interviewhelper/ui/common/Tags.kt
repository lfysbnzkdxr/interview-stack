package com.example.interviewhelper.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.interviewhelper.ui.theme.DifficultyAdvanced
import com.example.interviewhelper.ui.theme.DifficultyBeginner
import com.example.interviewhelper.ui.theme.DifficultyIntermediate

/**
 * 难度标签：难度色 12% alpha 浅底 + 16dp 圆角。
 */
@Composable
fun DifficultyTag(difficulty: String, modifier: Modifier = Modifier) {
    val color = when (difficulty) {
        "初级" -> DifficultyBeginner
        "中级" -> DifficultyIntermediate
        "高级" -> DifficultyAdvanced
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Text(
            text = difficulty,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 分类标签：中性灰底（surfaceContainerHighest，与卡片容器形成区分）+ 16dp 圆角。
 */
@Composable
fun CategoryTag(category: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
