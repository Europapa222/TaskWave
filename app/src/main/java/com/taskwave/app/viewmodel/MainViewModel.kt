package com.taskwave.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taskwave.app.data.Priority
import com.taskwave.app.data.TaskFolder
import com.taskwave.app.data.TaskRepository
import com.taskwave.app.data.TodoItem
import com.taskwave.app.data.UserPreferences
import com.taskwave.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FilterType { ALL, ACTIVE, DONE }

// "system" | "light" | "dark"
data class AppUiState(
    val items: List<TodoItem> = emptyList(),
    val folders: List<TaskFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val filter: FilterType = FilterType.ALL,
    val showAddDialog: Boolean = false,
    val showFolderDialog: Boolean = false,
    val showSettings: Boolean = false,
    val onboardingDone: Boolean = false,
    val darkModeOverride: String = "system",
    val isLoading: Boolean = true
) {
    val filteredItems: List<TodoItem>
        get() {
            val byFolder = selectedFolderId?.let { folderId -> items.filter { it.folderId == folderId } } ?: items
            return when (filter) {
                FilterType.ALL -> byFolder
                FilterType.ACTIVE -> byFolder.filter { !it.isDone }
                FilterType.DONE -> byFolder.filter { it.isDone }
            }
        }
    val doneCount get() = items.count { it.isDone }
    val totalCount get() = items.size
    val activeCount get() = items.count { !it.isDone }
    val overdueCount get() = items.count { !it.isDone && it.dueAt != null && it.dueAt < System.currentTimeMillis() }
    val focusTask: TodoItem?
        get() = items
            .filter { !it.isDone }
            .sortedWith(compareBy<TodoItem> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.priority.ordinal }.thenBy { it.createdAt })
            .firstOrNull()
    val progress get() = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val repo = TaskRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(prefs.onboardingDone, prefs.darkModeOverride, repo.tasks, repo.folders) { done, dark, tasks, folders ->
                AppUiState(onboardingDone = done, darkModeOverride = dark, items = tasks, folders = folders, isLoading = false)
            }.collect { newState ->
                _state.update {
                    newState.copy(
                        filter = it.filter,
                        selectedFolderId = it.selectedFolderId.takeIf { folderId -> newState.folders.any { folder -> folder.id == folderId } },
                        showAddDialog = it.showAddDialog,
                        showFolderDialog = it.showFolderDialog,
                        showSettings = it.showSettings
                    )
                }
            }
        }
        ReminderScheduler.ensureChannels(application)
    }

    private fun persistTasks(tasks: List<TodoItem>) {
        viewModelScope.launch { repo.saveTasks(tasks) }
    }

    private fun persistFolders(folders: List<TaskFolder>) {
        viewModelScope.launch { repo.saveFolders(folders) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone() }
    }

    fun addItem(title: String, description: String, priority: Priority, folderId: String?, dueAt: Long?, reminderAt: Long?) {
        if (title.isBlank()) return
        val task = TodoItem(
            title = title.trim(),
            description = description.trim(),
            priority = priority,
            folderId = folderId,
            dueAt = dueAt,
            reminderAt = reminderAt
        )
        val newItems = listOf(task) + _state.value.items
        _state.update { it.copy(items = newItems, showAddDialog = false) }
        persistTasks(newItems)
        ReminderScheduler.scheduleReminder(getApplication(), task.id, task.title, task.dueAt, task.reminderAt)
    }

    fun toggleDone(id: String) {
        val completedAt = System.currentTimeMillis()
        val newItems = _state.value.items.map {
            if (it.id == id && !it.isDone) {
                ReminderScheduler.cancelReminder(getApplication(), it.id)
                ReminderScheduler.scheduleCleanup(getApplication(), it.id, it.title, completedAt)
                it.copy(isDone = true, completedAt = completedAt)
            } else if (it.id == id) {
                ReminderScheduler.cancelCleanup(getApplication(), it.id)
                ReminderScheduler.scheduleReminder(getApplication(), it.id, it.title, it.dueAt, it.reminderAt)
                it.copy(isDone = false, completedAt = null)
            } else {
                it
            }
        }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun deleteItem(id: String) {
        ReminderScheduler.cancelReminder(getApplication(), id)
        ReminderScheduler.cancelCleanup(getApplication(), id)
        val newItems = _state.value.items.filter { it.id != id }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun addFolder(name: String) {
        if (name.isBlank()) return
        val folder = TaskFolder(
            name = name.trim(),
            colorIndex = _state.value.folders.size % 6
        )
        val folders = _state.value.folders + folder
        _state.update { it.copy(folders = folders, showFolderDialog = false, selectedFolderId = folder.id) }
        persistFolders(folders)
    }

    fun deleteFolder(id: String) {
        if (id == "inbox") return
        val folders = _state.value.folders.filterNot { it.id == id }
        val tasks = _state.value.items.map { if (it.folderId == id) it.copy(folderId = null) else it }
        _state.update { it.copy(folders = folders, items = tasks, selectedFolderId = null) }
        persistFolders(folders)
        persistTasks(tasks)
    }

    fun selectFolder(folderId: String?) = _state.update { it.copy(selectedFolderId = folderId) }
    fun setFilter(filter: FilterType) = _state.update { it.copy(filter = filter) }
    fun showAddDialog() = _state.update { it.copy(showAddDialog = true) }
    fun hideAddDialog() = _state.update { it.copy(showAddDialog = false) }
    fun showFolderDialog() = _state.update { it.copy(showFolderDialog = true) }
    fun hideFolderDialog() = _state.update { it.copy(showFolderDialog = false) }
    fun showSettings() = _state.update { it.copy(showSettings = true) }
    fun hideSettings() = _state.update { it.copy(showSettings = false) }

    fun setDarkModeOverride(value: String) {
        viewModelScope.launch {
            prefs.setDarkModeOverride(value)
            _state.update { it.copy(darkModeOverride = value) }
        }
    }
}
