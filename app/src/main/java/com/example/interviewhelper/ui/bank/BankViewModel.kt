package com.example.interviewhelper.ui.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.interviewhelper.data.local.QuestionEntity
import com.example.interviewhelper.data.remote.llm.LlmService
import com.example.interviewhelper.data.repository.QuestionRepository
import com.example.interviewhelper.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditForm(
    val question: String = "",
    val category: String = "",
    val difficulty: String = "",
    val dialog: String = ""
)

data class BankUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val searchQuery: String = "",
    val filterCategory: String = "全部",
    val filterDifficulty: String = "全部",
    val expandedId: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val editId: String? = null,
    val editForm: EditForm = EditForm(),
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    val aiPolishResult: String? = null,
    val showSubQuestion: Boolean = false,
    val subQuestionText: String = "",
    val totalCount: Int = 0
) {
    companion object {
        const val PAGE_SIZE = 20
    }
}

@HiltViewModel
class BankViewModel @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository,
    private val llmService: LlmService
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankUiState())
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private val filterCategoryFlow = MutableStateFlow("全部")
    private val filterDifficultyFlow = MutableStateFlow("全部")

    /** Paging 3 分页数据源，响应筛选条件变化 */
    val pagedQuestions: Flow<PagingData<QuestionEntity>> = combine(
        searchQueryFlow,
        filterCategoryFlow,
        filterDifficultyFlow
    ) { query, category, difficulty ->
        Triple(query, category, difficulty)
    }.flatMapLatest { (query, category, difficulty) ->
        questionRepository.getPagedQuestions(
            category = category.takeIf { it != "全部" },
            difficulty = difficulty.takeIf { it != "全部" },
            query = query.takeIf { it.isNotBlank() }
        )
    }.cachedIn(viewModelScope)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadError = null) }
            try {
                val categories = settingsRepository.getCategories()
                val questions = questionRepository.getAllQuestions().first()
                _uiState.update {
                    it.copy(loading = false, categories = categories, questions = questions, totalCount = questions.size)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, loadError = "加载失败: ${e.message}") }
            }
        }
    }

    fun reload() = load()

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterCategory(cat: String) {
        filterCategoryFlow.value = cat
        _uiState.update { it.copy(filterCategory = cat) }
    }

    fun setFilterDifficulty(diff: String) {
        filterDifficultyFlow.value = diff
        _uiState.update { it.copy(filterDifficulty = diff) }
    }

    fun toggleExpand(id: String) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun toggleSelect(id: String) {
        _uiState.update {
            val newSet = it.selectedIds.toMutableSet()
            if (id in newSet) newSet.remove(id) else newSet.add(id)
            it.copy(selectedIds = newSet)
        }
    }

    fun selectAll(visibleIds: List<String>) {
        _uiState.update {
            val allSelected = visibleIds.isNotEmpty() && visibleIds.all { id -> id in it.selectedIds }
            if (allSelected) {
                it.copy(selectedIds = emptySet())
            } else {
                it.copy(selectedIds = visibleIds.toSet())
            }
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selectedIds = emptySet()) }

    fun toggleHideQuestion(id: String) {
        viewModelScope.launch {
            questionRepository.toggleHidden(id)
            load()
        }
    }

    fun deleteQuestion(id: String) {
        viewModelScope.launch {
            questionRepository.deleteQuestion(id)
            load()
        }
    }

    fun batchDelete() {
        viewModelScope.launch {
            questionRepository.batchDelete(_uiState.value.selectedIds.toList())
            _uiState.update { it.copy(selectedIds = emptySet()) }
            load()
        }
    }

    fun batchHide() {
        viewModelScope.launch {
            questionRepository.batchHide(_uiState.value.selectedIds.toList())
            _uiState.update { it.copy(selectedIds = emptySet()) }
            load()
        }
    }

    fun batchUnhide() {
        viewModelScope.launch {
            questionRepository.batchUnhide(_uiState.value.selectedIds.toList())
            _uiState.update { it.copy(selectedIds = emptySet()) }
            load()
        }
    }

    fun batchMoveCategory(category: String) {
        viewModelScope.launch {
            questionRepository.batchMoveCategory(_uiState.value.selectedIds.toList(), category)
            _uiState.update { it.copy(selectedIds = emptySet()) }
            load()
        }
    }

    // 编辑
    fun startEdit(item: QuestionEntity) {
        _uiState.update {
            it.copy(
                editId = item.id,
                editForm = EditForm(
                    question = item.question,
                    category = item.category,
                    difficulty = item.difficulty,
                    dialog = item.dialog
                ),
                expandedId = item.id
            )
        }
    }

    fun updateEditForm(form: EditForm) = _uiState.update { it.copy(editForm = form) }

    fun saveEdit() {
        val state = _uiState.value
        val editId = state.editId ?: return
        viewModelScope.launch {
            val existing = questionRepository.getQuestionById(editId) ?: return@launch
            questionRepository.updateQuestion(
                existing.copy(
                    question = state.editForm.question,
                    category = state.editForm.category,
                    difficulty = state.editForm.difficulty,
                    dialog = state.editForm.dialog
                )
            )
            _uiState.update { it.copy(editId = null) }
            load()
        }
    }

    fun cancelEdit() = _uiState.update { it.copy(editId = null) }

    // AI 润色
    fun handleAiPolish() {
        val state = _uiState.value
        if (state.editForm.dialog.isBlank()) {
            _uiState.update { it.copy(aiError = "对话内容为空，无法润色") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true, aiError = null) }
            val result = llmService.polishDialog(state.editForm.question, state.editForm.dialog)
            result.fold(
                onSuccess = { llmResult ->
                    _uiState.update { it.copy(aiLoading = false, aiPolishResult = llmResult.dialog) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(aiLoading = false, aiError = e.message) }
                }
            )
        }
    }

    fun acceptPolish() {
        _uiState.update { state ->
            state.aiPolishResult?.let { polished ->
                state.copy(
                    editForm = state.editForm.copy(dialog = polished),
                    aiPolishResult = null
                )
            } ?: state
        }
    }

    fun rejectPolish() = _uiState.update { it.copy(aiPolishResult = null) }
    fun clearAiError() = _uiState.update { it.copy(aiError = null) }

    // 子问题
    fun setShowSubQuestion(show: Boolean) = _uiState.update { it.copy(showSubQuestion = show, subQuestionText = "") }
    fun setSubQuestionText(text: String) = _uiState.update { it.copy(subQuestionText = text) }

    fun handleAppendSub() {
        val state = _uiState.value
        if (state.subQuestionText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true, aiError = null) }
            val result = llmService.appendSubQA(state.editForm.question, state.editForm.dialog, state.subQuestionText)
            result.fold(
                onSuccess = { llmResult ->
                    _uiState.update {
                        it.copy(
                            aiLoading = false,
                            editForm = it.editForm.copy(dialog = llmResult.dialog),
                            showSubQuestion = false,
                            subQuestionText = ""
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(aiLoading = false, aiError = e.message) }
                }
            )
        }
    }
}
