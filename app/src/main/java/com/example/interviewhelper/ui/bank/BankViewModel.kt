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
    val categories: List<String> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    /** 分类×难度组合计数（分类 → 难度 → 题数），供分类按钮的难度联动题数 */
    val categoryDifficultyCounts: Map<String, Map<String, Int>> = emptyMap(),
    val visibleOnly: Boolean = false,
    val searchQuery: String = "",
    val filterCategory: String = "全部",
    val filterDifficulty: String = "全部",
    /** 各分类独立记忆的难度选择（分类 → 难度），切换分类互不影响 */
    val perCategoryDifficulty: Map<String, String> = emptyMap(),
    /** 当前展开难度选择的分类（null = 未展开） */
    val difficultyMenuOpenFor: String? = null,
    val expandedId: String? = null,
    /** 多选模式（长按题目行进入） */
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val editId: String? = null,
    val editForm: EditForm = EditForm(),
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    val aiPolishResult: String? = null,
    val showSubQuestion: Boolean = false,
    val subQuestionText: String = "",
    val totalCount: Int = 0
)

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
    private val visibleOnlyFlow = MutableStateFlow(false)

    private data class FilterState(
        val query: String,
        val category: String,
        val difficulty: String,
        val visibleOnly: Boolean
    )

    /** Paging 3 分页数据源，响应筛选条件变化 */
    val pagedQuestions: Flow<PagingData<QuestionEntity>> = combine(
        searchQueryFlow,
        filterCategoryFlow,
        filterDifficultyFlow,
        visibleOnlyFlow
    ) { query, category, difficulty, visibleOnly ->
        FilterState(query, category, difficulty, visibleOnly)
    }.flatMapLatest { (query, category, difficulty, visibleOnly) ->
        questionRepository.getPagedQuestions(
            category = category.takeIf { it != "全部" },
            difficulty = difficulty.takeIf { it != "全部" },
            query = query.takeIf { it.isNotBlank() },
            visibleOnly = visibleOnly
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
                _uiState.update { it.copy(loading = false, categories = categories) }
                refreshStats(_uiState.value.visibleOnly)
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, loadError = "加载失败: ${e.message}") }
            }
        }
    }

    /** 刷新总数、分类计数与分类×难度组合计数（依赖 visibleOnly），不触发 loading 闪烁 */
    private suspend fun refreshStats(visibleOnly: Boolean) {
        val totalCount = if (visibleOnly) {
            questionRepository.getVisibleCount().first()
        } else {
            questionRepository.getQuestionCount().first()
        }
        val categoryCounts = questionRepository.getCategoryCounts(visibleOnly).first()
            .associate { it.category to it.count }
        // 分类×难度组合计数：按组合过滤计算，供分类按钮的难度联动题数
        val all = questionRepository.getAllQuestions().first()
        val visible = if (visibleOnly) all.filter { !it.hidden } else all
        val categoryDifficultyCounts = visible.groupBy { it.category }.mapValues { (_, list) ->
            list.groupingBy { it.difficulty }.eachCount()
        }
        _uiState.update {
            it.copy(
                totalCount = totalCount,
                categoryCounts = categoryCounts,
                categoryDifficultyCounts = categoryDifficultyCounts
            )
        }
    }

    fun setVisibleOnly(visible: Boolean) {
        _uiState.update { it.copy(visibleOnly = visible) }
        visibleOnlyFlow.value = visible
        viewModelScope.launch {
            try {
                refreshStats(visible)
            } catch (e: Exception) {
                // 统计刷新失败不影响分页数据，忽略
            }
        }
    }

    fun reload() = load()

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    // 分类交互（难度选择内嵌于分类按钮）
    fun onCategoryClick(category: String) {
        val state = _uiState.value
        if (category == state.filterCategory) {
            // 再次点击当前分类：展开/收起该分类的难度选择
            _uiState.update {
                it.copy(difficultyMenuOpenFor = if (it.difficultyMenuOpenFor == category) null else category)
            }
        } else {
            // 切换分类：应用该分类独立记忆的难度（无记忆则全部）
            val difficulty = state.perCategoryDifficulty[category] ?: "全部"
            filterCategoryFlow.value = category
            filterDifficultyFlow.value = difficulty
            _uiState.update {
                it.copy(
                    filterCategory = category,
                    filterDifficulty = difficulty,
                    difficultyMenuOpenFor = null
                )
            }
        }
    }

    /** 选择当前展开分类的难度，各分类独立记忆互不影响 */
    fun setCategoryDifficulty(difficulty: String) {
        val state = _uiState.value
        val category = state.difficultyMenuOpenFor ?: state.filterCategory
        filterDifficultyFlow.value = difficulty
        _uiState.update {
            it.copy(
                perCategoryDifficulty = it.perCategoryDifficulty + (category to difficulty),
                filterDifficulty = difficulty
            )
        }
    }

    // 多选模式
    fun enterSelectionMode(id: String? = null) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedIds = if (id != null) it.selectedIds + id else it.selectedIds,
                expandedId = null
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
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
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            load()
        }
    }

    fun batchHide() {
        viewModelScope.launch {
            questionRepository.batchHide(_uiState.value.selectedIds.toList())
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            load()
        }
    }

    fun batchUnhide() {
        viewModelScope.launch {
            questionRepository.batchUnhide(_uiState.value.selectedIds.toList())
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            load()
        }
    }

    fun batchMoveCategory(category: String) {
        viewModelScope.launch {
            questionRepository.batchMoveCategory(_uiState.value.selectedIds.toList(), category)
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
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
