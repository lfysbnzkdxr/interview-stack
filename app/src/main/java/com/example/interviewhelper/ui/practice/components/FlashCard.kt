package com.example.interviewhelper.ui.practice.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.ui.common.AppMarkdownText
import com.example.interviewhelper.ui.common.CategoryTag
import com.example.interviewhelper.ui.common.DifficultyTag

@Composable
fun FlashCard(
    question: QuestionEntity,
    isFlipped: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(300),
        label = "cardRotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .testTag("flashCard")
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .pointerInput(question.id) {
                var totalDragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        when {
                            totalDragX < -100 -> onNext()
                            totalDragX > 100 -> onPrev()
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount }
                )
            },
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (rotation <= 90f) {
                // 正面 - 问题
                CardFront(question = question)
            } else {
                // 背面 - 答案（需要镜像翻转）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    CardBack(question = question)
                }
            }
        }
    }
}

@Composable
private fun CardFront(question: QuestionEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标签行
        Row {
            DifficultyTag(question.difficulty)
            Spacer(modifier = Modifier.width(8.dp))
            CategoryTag(question.category)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 问题标题
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CardBack(question: QuestionEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (question.dialog.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无回答",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                AppMarkdownText(markdown = question.dialog)
            }
        }
    }
}
