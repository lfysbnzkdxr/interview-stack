package com.example.interviewhelper.ui.browse

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

data class BrowseUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val categories: List<String> = emptyList(),
    val activeCategory: String? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val expandedId: String? = null
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadError = null) }
            try {
                val categories = settingsRepository.getCategories()
                val questions = questionRepository.getVisibleQuestions().first()
                _uiState.update {
                    it.copy(
                        loading = false,
                        categories = categories,
                        questions = questions
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, loadError = "数据加载失败: ${e.message}")
                }
            }
        }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(activeCategory = category, expandedId = null) }
    }

    fun toggleExpand(id: String) {
        _uiState.update {
            it.copy(expandedId = if (it.expandedId == id) null else id)
        }
    }

    fun reload() = load()

    val filteredQuestions: List<QuestionEntity>
        get() {
            val state = _uiState.value
            return if (state.activeCategory == null) {
                state.questions
            } else {
                state.questions.filter { it.category == state.activeCategory }
            }
        }
}
