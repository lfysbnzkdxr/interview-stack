package com.example.interviewhelper.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewData(
    val optimizedQuestion: String,
    val dialog: String,
    val difficulty: String,
    val isManual: Boolean = false
)

data class CreateUiState(
    val question: String = "",
    val answer: String = "",
    val selectedCategory: String = "",
    val categories: List<String> = emptyList(),
    val loading: Boolean = false,
    val step: String = "",
    val error: String? = null,
    val preview: PreviewData? = null,
    val showNewCategory: Boolean = false,
    val newCategoryName: String = "",
    val saveSuccess: Boolean = false
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val llmService: LlmService,
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val categories = settingsRepository.getCategories()
            _uiState.update { it.copy(categories = categories) }
        }
    }

    fun setQuestion(q: String) = _uiState.update { it.copy(question = q, error = null) }
    fun setAnswer(a: String) = _uiState.update { it.copy(answer = a, error = null) }
    fun setCategory(c: String) = _uiState.update { it.copy(selectedCategory = c, error = null) }
    fun setNewCategoryName(n: String) = _uiState.update { it.copy(newCategoryName = n) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }

    fun handleGenerate() {
        val state = _uiState.value
        if (state.question.isBlank()) {
            _uiState.update { it.copy(error = "请输入面试问题") }
            return
        }
        if (state.selectedCategory.isBlank()) {
            _uiState.update { it.copy(error = "请选择分类") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, step = "AI 正在生成回答...", error = null) }
            val result = llmService.generateQA(state.question)
            result.fold(
                onSuccess = { llmResult ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            step = "",
                            preview = PreviewData(
                                optimizedQuestion = llmResult.optimizedQuestion ?: state.question,
                                dialog = llmResult.dialog,
                                difficulty = llmResult.difficulty ?: "中级"
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(loading = false, step = "", error = e.message) }
                }
            )
        }
    }

    fun handleOptimize() {
        val state = _uiState.value
        if (state.question.isBlank()) {
            _uiState.update { it.copy(error = "请输入面试问题") }
            return
        }
        if (state.answer.isBlank()) {
            _uiState.update { it.copy(error = "请输入答案要点") }
            return
        }
        if (state.selectedCategory.isBlank()) {
            _uiState.update { it.copy(error = "请选择分类") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, step = "AI 正在优化回答...", error = null) }
            val result = llmService.optimizeQA(state.question, state.answer)
            result.fold(
                onSuccess = { llmResult ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            step = "",
                            preview = PreviewData(
                                optimizedQuestion = llmResult.optimizedQuestion ?: state.question,
                                dialog = llmResult.dialog,
                                difficulty = llmResult.difficulty ?: "中级"
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(loading = false, step = "", error = e.message) }
                }
            )
        }
    }

    fun handleManualSave() {
        val state = _uiState.value
        if (state.question.isBlank()) {
            _uiState.update { it.copy(error = "请输入面试问题") }
            return
        }
        if (state.selectedCategory.isBlank()) {
            _uiState.update { it.copy(error = "请选择分类") }
            return
        }

        // 直接保存，不经过 AI
        val dialog = if (state.answer.isNotBlank()) {
            "**Q**：${state.question}\n\n**A**：${state.answer}"
        } else ""

        _uiState.update {
            it.copy(
                preview = PreviewData(
                    optimizedQuestion = state.question,
                    dialog = dialog,
                    difficulty = "中级",
                    isManual = true
                )
            )
        }
    }

    fun updatePreviewDialog(dialog: String) {
        _uiState.update { state ->
            state.preview?.let { p ->
                state.copy(preview = p.copy(dialog = dialog))
            } ?: state
        }
    }

    fun updatePreviewDifficulty(difficulty: String) {
        _uiState.update { state ->
            state.preview?.let { p ->
                state.copy(preview = p.copy(difficulty = difficulty))
            } ?: state
        }
    }

    fun handleSave() {
        val state = _uiState.value
        val preview = state.preview ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, step = "保存中...") }
            try {
                questionRepository.addQuestion(
                    question = preview.optimizedQuestion,
                    dialog = preview.dialog,
                    category = state.selectedCategory,
                    difficulty = preview.difficulty,
                    source = if (preview.isManual) "manual" else "ai"
                )
                _uiState.update {
                    it.copy(
                        loading = false,
                        step = "",
                        saveSuccess = true,
                        question = "",
                        answer = "",
                        preview = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, step = "", error = "保存失败: ${e.message}") }
            }
        }
    }

    fun cancelPreview() = _uiState.update { it.copy(preview = null) }

    fun showNewCategoryInput() = _uiState.update { it.copy(showNewCategory = true, newCategoryName = "") }

    fun confirmNewCategory() {
        val name = _uiState.value.newCategoryName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val categories = settingsRepository.getCategories().toMutableList()
            if (name !in categories) {
                categories.add(categories.size - 1, name) // 在"未分类"前插入
                settingsRepository.saveCategories(categories)
            }
            _uiState.update {
                it.copy(
                    categories = categories,
                    selectedCategory = name,
                    showNewCategory = false,
                    newCategoryName = ""
                )
            }
        }
    }

    fun cancelNewCategory() = _uiState.update { it.copy(showNewCategory = false, newCategoryName = "") }
}
