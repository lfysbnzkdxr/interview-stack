package com.queststack.ui.screen.practice

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.queststack.ui.component.GlassTopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一条聊天气泡：面试官问题 / 用户参考答案 */
private sealed interface ChatMessage {
    data class Interviewer(val text: String, val round: Int) : ChatMessage
    data class Answer(val text: String, val round: Int) : ChatMessage
}

@Composable
fun PracticeChatScreen(
    questionId: Long,
    onBack: () -> Unit,
    viewModel: PracticeChatViewModel =
        viewModel(key = "practice_chat_$questionId") { PracticeChatViewModel(questionId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val question = uiState.question
    val rounds = remember(question) { question?.rounds.orEmpty().sortedBy { it.orderIndex } }
    val revealed = uiState.revealed.coerceIn(0, rounds.size)

    // 气泡流：revealed 组「问题+答案」对，若链未耗尽再追加一个当前问题
    val messages = remember(rounds, revealed) {
        buildList {
            for (i in 0 until revealed) {
                val round = rounds[i]
                add(ChatMessage.Interviewer(round.question, i + 1))
                add(ChatMessage.Answer(round.answer, i + 1))
            }
            if (revealed < rounds.size) {
                add(ChatMessage.Interviewer(rounds[revealed].question, revealed + 1))
            }
        }
    }

    // 新消息出现时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassTopAppBar(
            title = question?.question?.title ?: "答题",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        when {
            uiState.loading -> ChatLoadingPlaceholder()
            question == null -> QuestionNotExistPlaceholder()
            else -> {
                ChatMessageList(
                    messages = messages,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                ChatActionBar(
                    canPrev = uiState.history.isNotEmpty(),
                    exhausted = rounds.isNotEmpty() && revealed >= rounds.size,
                    onTip = {
                        Toast.makeText(context, "先尝试自己组织答案，再看参考答案", Toast.LENGTH_SHORT).show()
                    },
                    onReveal = viewModel::revealAnswer,
                    onPrev = viewModel::prevQuestion,
                    onNext = viewModel::nextQuestion,
                )
            }
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages.size, key = { it }) { index ->
            when (val message = messages[index]) {
                is ChatMessage.Interviewer -> InterviewerBubble(message)
                is ChatMessage.Answer -> AnswerBubble(message)
            }
        }
    }
}

/** 面试官问题气泡：左侧对齐，surfaceContainer 背景，右下小圆角（微信风格） */
@Composable
private fun InterviewerBubble(message: ChatMessage.Interviewer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                text = "面试官 · 第 ${message.round} 问",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomEnd = 12.dp,
                            bottomStart = 4.dp,
                        ),
                    )
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text.ifBlank { "（问题为空）" },
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

/** 参考答案气泡：右侧对齐，primary 背景，左下小圆角（微信风格） */
@Composable
private fun AnswerBubble(message: ChatMessage.Answer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "参考答案",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
            )
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomEnd = 4.dp,
                            bottomStart = 12.dp,
                        ),
                    )
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text.ifBlank { "（答案为空）" },
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onPrimary,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

/** 底部操作区：链未耗尽时「提示 + 查看答案」，耗尽后「上一问 + 下一问」 */
@Composable
private fun ChatActionBar(
    canPrev: Boolean,
    exhausted: Boolean,
    onTip: () -> Unit,
    onReveal: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (exhausted) {
                Button(
                    onClick = onPrev,
                    enabled = canPrev,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "上一问", fontSize = 14.sp)
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "下一问", fontSize = 14.sp)
                }
            } else {
                Button(onClick = onTip, modifier = Modifier.weight(1f)) {
                    Text(text = "提示", fontSize = 14.sp)
                }
                Button(
                    onClick = onReveal,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "查看答案", fontSize = 14.sp)
                }
            }
        }
        if (exhausted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本问完成 ✓",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ChatLoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "加载中…",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun QuestionNotExistPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = MiuixIcons.Help,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "题目不存在",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}
