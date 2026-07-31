package com.example.interviewhelper.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun AppMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isSelectable: Boolean = false
) {
    if (markdown.isBlank()) return

    MarkdownText(
        markdown = markdown,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        isTextSelectable = isSelectable
    )
}
