package com.example.interviewhelper.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// 难度筛选项，与现有页面文案保持一致
private val DIFFICULTY_OPTIONS = listOf("全部", "初级", "中级", "高级")

/**
 * HyperOS 风格统一筛选栏，从练题页/题库页的私有实现提取。
 *
 * 参数化差异：
 * - 分类下拉：传入 selectedCategory 时显示（练题页使用）
 * - 难度下拉：传入 selectedDifficulty 时显示（练题/题库页使用）
 * - 搜索框 / 可见性开关：按需开关（题库页使用）
 *
 * 容器为 surfaceContainer 底色 + 24dp 圆角；"仅看可见"开关保留 visibleOnlySwitch testTag。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    categories: List<String> = emptyList(),
    selectedCategory: String? = null,
    onCategoryChange: ((String) -> Unit)? = null,
    selectedDifficulty: String? = null,
    onDifficultyChange: ((String) -> Unit)? = null,
    showSearch: Boolean = false,
    searchQuery: String = "",
    onSearchChange: ((String) -> Unit)? = null,
    showVisibleSwitch: Boolean = false,
    visibleOnly: Boolean = false,
    onVisibleOnlyChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 搜索框（可选）
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onSearchChange?.invoke(it) },
                    placeholder = { Text("搜索题目...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // 筛选行：分类 + 难度 + 可见性开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类下拉（可选，传入 selectedCategory 时显示）
                if (selectedCategory != null && onCategoryChange != null) {
                    DropdownField(
                        value = selectedCategory,
                        label = "分类",
                        options = listOf("全部") + categories,
                        onSelect = onCategoryChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 难度下拉（可选）
                if (selectedDifficulty != null && onDifficultyChange != null) {
                    DropdownField(
                        value = selectedDifficulty,
                        label = "难度",
                        options = DIFFICULTY_OPTIONS,
                        onSelect = onDifficultyChange,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 仅看可见开关（可选）
                if (showVisibleSwitch) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("仅看可见", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = visibleOnly,
                            onCheckedChange = { onVisibleOnlyChange?.invoke(it) },
                            modifier = Modifier.testTag("visibleOnlySwitch")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
