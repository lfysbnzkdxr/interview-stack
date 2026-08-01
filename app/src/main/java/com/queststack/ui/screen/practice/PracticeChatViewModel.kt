package com.queststack.ui.screen.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.queststack.data.DataContainer
import com.queststack.data.db.QuestionWithRounds
import com.queststack.data.repository.CategoryRepository
import com.queststack.data.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val question: QuestionWithRounds? = null,
    val revealed: Int = 0,
    val history: List<Long> = emptyList(),
    val currentId: Long,
    val loading: Boolean = true,
)

/**
 * 答题聊天页状态机：
 * - [ChatUiState.revealed]：已显示答案的轮次数。气泡流规则：
 *   面试官问题显示 rounds[0..revealed]，用户答案显示 rounds[0 until revealed]
 *   （rounds 按 orderIndex 升序），即每点一次"查看答案"追加一组 答案 + 下一问。
 * - [ChatUiState.history]：上一题栈，"上一问"时 pop。
 */
class PracticeChatViewModel(
    questionId: Long,
    private val questionRepository: QuestionRepository = DataContainer.questionRepository,
    categoryRepository: CategoryRepository = DataContainer.categoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(currentId = questionId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        load(questionId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val question = questionRepository.getQuestion(id)
            _uiState.update {
                it.copy(
                    question = question,
                    currentId = id,
                    revealed = 0,
                    loading = false,
                )
            }
        }
    }

    /** 揭示当前轮次答案；追问链耗尽后 no-op */
    fun revealAnswer() {
        val state = _uiState.value
        val size = state.question?.rounds?.size ?: 0
        if (state.revealed < size) {
            _uiState.update { it.copy(revealed = it.revealed + 1) }
        }
    }

    /** 随机取一个新题：当前 id 入 history，重新加载，revealed 重置 0 */
    fun nextQuestion() {
        val state = _uiState.value
        viewModelScope.launch {
            val ids = questionRepository.randomQuestionIds(null, null)
            val next = ids.firstOrNull { it != state.currentId } ?: return@launch
            _uiState.update { it.copy(history = it.history + it.currentId) }
            load(next)
        }
    }

    /** 上一问：history 非空时 pop 并重新加载，revealed 重置 0 */
    fun prevQuestion() {
        val state = _uiState.value
        if (state.history.isEmpty()) return
        val prev = state.history.last()
        _uiState.update { it.copy(history = it.history.dropLast(1)) }
        load(prev)
    }
}
