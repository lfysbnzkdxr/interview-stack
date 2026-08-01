package com.example.interviewhelper.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PracticeUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val difficulty: String = "全部",
    val category: String = "全部",
    val categories: List<String> = emptyList(),
    val isFlipped: Boolean = false,
    val currentQuestion: QuestionEntity? = null,
    val queue: List<QuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isFirst: Boolean = true,
    val isLast: Boolean = true,
    val progress: String = "0 / 0"
)

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val QUEUE_SIZE = 20
        private const val REFILL_THRESHOLD = 5
    }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var allQuestions: List<QuestionEntity> = emptyList()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadError = null) }
            try {
                val categories = settingsRepository.getCategories()
                _uiState.update { it.copy(categories = categories) }

                val questions = questionRepository.getVisibleQuestions().first()
                allQuestions = questions

                if (questions.isEmpty()) {
                    _uiState.update {
                        it.copy(loading = false, currentQuestion = null, queue = emptyList())
                    }
                    return@launch
                }

                initQueue()
                _uiState.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, loadError = "数据加载失败: ${e.message}")
                }
            }
        }
    }

    private fun initQueue() {
        val filtered = filterQuestions()
        val shuffled = fisherYatesShuffle(filtered)
        val queue = shuffled.take(QUEUE_SIZE)

        _uiState.update {
            it.copy(
                queue = queue,
                currentIndex = 0,
                currentQuestion = queue.firstOrNull(),
                isFlipped = false,
                isFirst = true,
                isLast = queue.size <= 1,
                progress = if (queue.isEmpty()) "0 / 0" else "1 / ${queue.size}"
            )
        }
    }

    private fun filterQuestions(): List<QuestionEntity> {
        val state = _uiState.value
        return allQuestions.filter { q ->
            (state.difficulty == "全部" || q.difficulty == state.difficulty) &&
            (state.category == "全部" || q.category == state.category)
        }
    }

    private fun fisherYatesShuffle(list: List<QuestionEntity>): List<QuestionEntity> {
        val result = list.toMutableList()
        for (i in result.indices.reversed()) {
            val j = (0..i).random()
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }
        return result
    }

    fun next() {
        val state = _uiState.value
        if (state.currentIndex >= state.queue.size - 1) return

        val newIndex = state.currentIndex + 1
        _uiState.update {
            it.copy(
                currentIndex = newIndex,
                currentQuestion = it.queue[newIndex],
                isFlipped = false,
                isFirst = newIndex == 0,
                isLast = newIndex == it.queue.size - 1,
                progress = "${newIndex + 1} / ${it.queue.size}"
            )
        }

        // 自动补充队列
        if (state.queue.size - newIndex <= REFILL_THRESHOLD) {
            refillQueue()
        }
    }

    fun prev() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return

        val newIndex = state.currentIndex - 1
        _uiState.update {
            it.copy(
                currentIndex = newIndex,
                currentQuestion = it.queue[newIndex],
                isFlipped = false,
                isFirst = newIndex == 0,
                isLast = newIndex == it.queue.size - 1,
                progress = "${newIndex + 1} / ${it.queue.size}"
            )
        }
    }

    fun flip() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun setDifficulty(difficulty: String) {
        _uiState.update { it.copy(difficulty = difficulty) }
        initQueue()
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(category = category) }
        initQueue()
    }

    fun reload() {
        loadData()
    }

    private fun refillQueue() {
        val state = _uiState.value
        val filtered = filterQuestions()
        val currentIds = state.queue.map { it.id }.toSet()
        val remaining = filtered.filter { it.id !in currentIds }

        if (remaining.isNotEmpty()) {
            val newItems = fisherYatesShuffle(remaining).take(QUEUE_SIZE - state.queue.size + REFILL_THRESHOLD)
            val newQueue = state.queue + newItems
            _uiState.update {
                it.copy(
                    queue = newQueue,
                    isLast = it.currentIndex == newQueue.size - 1,
                    progress = "${it.currentIndex + 1} / ${newQueue.size}"
                )
            }
        }
    }
}
