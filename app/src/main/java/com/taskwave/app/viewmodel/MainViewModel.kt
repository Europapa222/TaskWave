package com.taskwave.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taskwave.app.data.Priority
import com.taskwave.app.data.TaskRepository
import com.taskwave.app.data.TodoItem
import com.taskwave.app.data.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FilterType { ALL, ACTIVE, DONE }

// "system" | "light" | "dark"
data class AppUiState(
    val items: List<TodoItem> = emptyList(),
    val filter: FilterType = FilterType.ALL,
    val showAddDialog: Boolean = false,
    val showSettings: Boolean = false,
    val onboardingDone: Boolean = false,
    val darkModeOverride: String = "system",
    val isLoading: Boolean = true
) {
    val filteredItems: List<TodoItem>
        get() = when (filter) {
            FilterType.ALL -> items
            FilterType.ACTIVE -> items.filter { !it.isDone }
            FilterType.DONE -> items.filter { it.isDone }
        }
    val doneCount get() = items.count { it.isDone }
    val totalCount get() = items.size
    val progress get() = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val repo = TaskRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.onboardingDone, prefs.darkModeOverride, repo.tasks) { done, dark, tasks ->
                Triple(done, dark, tasks)
            }.collect { (done, dark, tasks) ->
                _state.update {
                    it.copy(
                        onboardingDone = done,
                        darkModeOverride = dark,
                        items = tasks,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun persistTasks(tasks: List<TodoItem>) {
        viewModelScope.launch { repo.saveTasks(tasks) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone() }
    }

    fun addItem(title: String, description: String, priority: Priority) {
        if (title.isBlank()) return
        val newItems = listOf(TodoItem(title = title.trim(), description = description.trim(), priority = priority)) + _state.value.items
        _state.update { it.copy(items = newItems, showAddDialog = false) }
        persistTasks(newItems)
    }

    fun toggleDone(id: String) {
        val newItems = _state.value.items.map { if (it.id == id) it.copy(isDone = !it.isDone) else it }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun deleteItem(id: String) {
        val newItems = _state.value.items.filter { it.id != id }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun setFilter(filter: FilterType) = _state.update { it.copy(filter = filter) }
    fun showAddDialog() = _state.update { it.copy(showAddDialog = true) }
    fun hideAddDialog() = _state.update { it.copy(showAddDialog = false) }
    fun showSettings() = _state.update { it.copy(showSettings = true) }
    fun hideSettings() = _state.update { it.copy(showSettings = false) }

    fun setDarkModeOverride(value: String) {
        viewModelScope.launch {
            prefs.setDarkModeOverride(value)
            _state.update { it.copy(darkModeOverride = value) }
        }
    }
}
