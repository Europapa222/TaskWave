package com.taskwave.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taskwave.app.data.Priority
import com.taskwave.app.data.ProductivityStats
import com.taskwave.app.data.SubTask
import com.taskwave.app.data.TaskFolder
import com.taskwave.app.data.TaskRepository
import com.taskwave.app.data.TodoItem
import com.taskwave.app.data.UserPreferences
import com.taskwave.app.notifications.ReminderScheduler
import com.taskwave.app.widget.TaskWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class FilterType { TODAY, ALL, ACTIVE, DONE }

// "system" | "light" | "dark"
data class AppUiState(
    val items: List<TodoItem> = emptyList(),
    val folders: List<TaskFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val filter: FilterType = FilterType.ALL,
    val searchQuery: String = "",
    val productivity: ProductivityStats = ProductivityStats(),
    val showAddDialog: Boolean = false,
    val showFolderDialog: Boolean = false,
    val showSettings: Boolean = false,
    val onboardingDone: Boolean = false,
    val darkModeOverride: String = "system",
    val smartAiEnabled: Boolean = true,
    val isLoading: Boolean = true
) {
    val filteredItems: List<TodoItem>
        get() {
            val byFolder = selectedFolderId?.let { folderId -> items.filter { it.folderId == folderId } } ?: items
            val byFilter = when (filter) {
                FilterType.TODAY -> byFolder.filter { !it.isDone }.sortedWith(todayTaskComparator()).let {
                    if (showAntiOverload) it.take(3) else it
                }
                FilterType.ALL -> byFolder
                FilterType.ACTIVE -> byFolder.filter { !it.isDone }
                FilterType.DONE -> byFolder.filter { it.isDone }
            }
            val query = searchQuery.trim()
            if (query.isBlank()) return byFilter
            return byFilter.filter { task ->
                val folderName = folders.firstOrNull { it.id == task.folderId }?.name.orEmpty()
                task.title.contains(query, ignoreCase = true) ||
                    task.description.contains(query, ignoreCase = true) ||
                    folderName.contains(query, ignoreCase = true)
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
    val productivityLevel get() = productivity.points / 100 + 1
    val topThreeToday get() = items.filter { !it.isDone }.sortedWith(todayTaskComparator()).take(3)
    val showAntiOverload get() = filter == FilterType.TODAY && items.count { !it.isDone } > 3
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val repo = TaskRepository(application)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val appPrefs = combine(prefs.onboardingDone, prefs.darkModeOverride, prefs.smartAiEnabled) { done, dark, smartAiEnabled ->
                Triple(done, dark, smartAiEnabled)
            }
            combine(appPrefs, repo.tasks, repo.folders, repo.productivity) { appPrefsValue, tasks, folders, productivity ->
                AppUiState(
                    onboardingDone = appPrefsValue.first,
                    darkModeOverride = appPrefsValue.second,
                    smartAiEnabled = appPrefsValue.third,
                    items = tasks,
                    folders = folders,
                    productivity = productivity,
                    isLoading = false
                )
            }.collect { newState ->
                _state.update {
                    newState.copy(
                        filter = it.filter,
                        searchQuery = it.searchQuery,
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
        viewModelScope.launch {
            repo.saveTasks(tasks)
            TaskWidgetProvider.refresh(getApplication())
        }
    }

    private fun persistFolders(folders: List<TaskFolder>) {
        viewModelScope.launch { repo.saveFolders(folders) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone() }
    }

    fun addItem(title: String, description: String, priority: Priority, folderId: String?, dueAt: Long?, reminderAt: Long?) {
        if (title.isBlank()) return
        val trimmedDescription = description.trim()
        val parsed = if (_state.value.smartAiEnabled) {
            parseSmartInput(title, trimmedDescription)
        } else {
            SmartInputResult(title.trim(), null, null)
        }
        val finalDueAt = dueAt ?: parsed.dueAt
        val finalReminderAt = reminderAt ?: parsed.reminderAt
        val task = TodoItem(
            title = parsed.title,
            description = trimmedDescription,
            priority = priority,
            folderId = folderId,
            dueAt = finalDueAt,
            reminderAt = finalReminderAt
        )
        val newItems = listOf(task) + _state.value.items
        _state.update { it.copy(items = newItems, showAddDialog = false) }
        persistTasks(newItems)
        ReminderScheduler.scheduleReminder(getApplication(), task.id, task.title, task.dueAt, task.reminderAt)
    }

    fun toggleDone(id: String) {
        val completedAt = System.currentTimeMillis()
        var completedNow = false
        val newItems = _state.value.items.map {
            if (it.id == id && !it.isDone) {
                ReminderScheduler.cancelReminder(getApplication(), it.id)
                ReminderScheduler.scheduleCleanup(getApplication(), it.id, it.title, completedAt)
                completedNow = true
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
        if (completedNow) viewModelScope.launch { repo.recordCompletion(dayIndex(completedAt)) }
    }

    fun deleteItem(id: String) {
        ReminderScheduler.cancelReminder(getApplication(), id)
        ReminderScheduler.cancelCleanup(getApplication(), id)
        val newItems = _state.value.items.filter { it.id != id }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun splitTask(id: String) {
        val newItems = _state.value.items.map { task ->
            if (task.id == id && task.subtasks.isEmpty()) {
                task.copy(subtasks = SmartSubtaskGenerator.generate(task.title, task.description, _state.value.smartAiEnabled))
            } else {
                task
            }
        }
        _state.update { it.copy(items = newItems) }
        persistTasks(newItems)
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        val newItems = _state.value.items.map { task ->
            if (task.id == taskId) {
                task.copy(subtasks = task.subtasks.map { subtask ->
                    if (subtask.id == subtaskId) subtask.copy(isDone = !subtask.isDone) else subtask
                })
            } else {
                task
            }
        }
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
        val folders = _state.value.folders.filterNot { it.id == id }
        val tasks = _state.value.items.map { if (it.folderId == id) it.copy(folderId = null) else it }
        _state.update { it.copy(folders = folders, items = tasks, selectedFolderId = null) }
        persistFolders(folders)
        persistTasks(tasks)
    }

    fun selectFolder(folderId: String?) = _state.update { it.copy(selectedFolderId = folderId) }
    fun setFilter(filter: FilterType) = _state.update { it.copy(filter = filter) }
    fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }
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

    fun setSmartAiEnabled(value: Boolean) {
        viewModelScope.launch {
            prefs.setSmartAiEnabled(value)
            _state.update { it.copy(smartAiEnabled = value) }
        }
    }
}

private fun todayTaskComparator(): Comparator<TodoItem> {
    val now = System.currentTimeMillis()
    val endOfToday = endOfTodayMillis(now)
    return compareBy<TodoItem> {
        when {
            it.dueAt != null && it.dueAt < now -> 0
            it.priority == Priority.HIGH -> 1
            it.dueAt != null && it.dueAt <= endOfToday -> 2
            it.dueAt != null -> 3
            else -> 4
        }
    }.thenBy { it.dueAt ?: Long.MAX_VALUE }
        .thenBy { priorityRank(it.priority) }
        .thenBy { it.createdAt }
}

private fun priorityRank(priority: Priority): Int {
    return when (priority) {
        Priority.HIGH -> 0
        Priority.MEDIUM -> 1
        Priority.LOW -> 2
    }
}

private fun endOfTodayMillis(now: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}

private data class SmartInputResult(
    val title: String,
    val dueAt: Long?,
    val reminderAt: Long?
)

private fun parseSmartInput(raw: String, description: String): SmartInputResult {
    val lowerTitle = raw.lowercase(Locale.getDefault())
    val lowerAll = "$raw $description".lowercase(Locale.getDefault())
    val hasTime = Regex("""\b\d{1,2}[:.]\d{2}\b""").containsMatchIn(lowerTitle)
    val date = when {
        containsAny(lowerTitle, listOf("послезавтра", "after tomorrow")) -> daysFromNow(2)
        containsAny(lowerTitle, listOf("завтра", "tomorrow")) -> daysFromNow(1)
        containsAny(lowerTitle, listOf("сегодня", "today")) -> daysFromNow(0)
        containsAny(lowerTitle, listOf("на неделе", "this week")) -> daysFromNow(7)
        containsAny(lowerTitle, listOf("срок", "дедлайн", "deadline", "due", "до ", "к ", "на ", "by ", "until ")) -> daysFromNow(0)
        hasTime -> daysFromNow(0)
        else -> null
    }
    val time = Regex("""(\d{1,2})[:.](\d{2})""").find(lowerTitle)?.let {
        it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull()
    }
    val dueAt = date?.let { calendar ->
        if (time?.first != null && time.second != null) {
            calendar.set(Calendar.HOUR_OF_DAY, time.first ?: 9)
            calendar.set(Calendar.MINUTE, time.second ?: 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 18)
            calendar.set(Calendar.MINUTE, 0)
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    val reminderAt = dueAt?.takeIf {
        containsAny(lowerAll, listOf("напомни", "напомнить", "напоминание", "remind", "notification", "уведом"))
    }?.let { it - 60 * 60 * 1000L }
    return SmartInputResult(if (dueAt == null) raw.trim() else cleanSmartTitle(raw), dueAt, reminderAt)
}

private fun cleanSmartTitle(raw: String): String {
    return raw
        .replace(Regex("""(?i)\b(today|tomorrow|after tomorrow|this week|remind me|remind)\b"""), "")
        .replace(Regex("""(?i)\b(сегодня|завтра|послезавтра|на неделе|напомни|напоминание)\b"""), "")
        .replace(Regex("""(?i)\b(at|в)\s+\d{1,2}[:.]\d{2}\b"""), "")
        .replace(Regex("""\b\d{1,2}[:.]\d{2}\b"""), "")
        .replace(Regex("""(?i)\b(at|в)\s*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { raw.trim() }
}

private fun daysFromNow(days: Int): Calendar {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, days)
    return calendar
}

private fun containsAny(value: String, options: List<String>): Boolean = options.any { value.contains(it) }

private fun dayIndex(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    return calendar.get(Calendar.YEAR) * 400L + calendar.get(Calendar.DAY_OF_YEAR)
}

private object SmartSubtaskGenerator {
    fun generate(title: String, description: String, smartAiEnabled: Boolean): List<SubTask> {
        val text = title.trim()
        val lower = "$title $description".lowercase(Locale.getDefault())
        val steps = if (smartAiEnabled) smartSteps(text, description, lower) else fallbackSteps(text)
        return steps.map { SubTask(title = it) }
    }

    private fun smartSteps(title: String, description: String, lower: String): List<String> {
        val descriptionSteps = extractDescriptionSteps(description)
        if (descriptionSteps.size >= 2) {
            return buildList {
                add("Понять результат задачи: $title")
                addAll(descriptionSteps.take(5))
                add("Проверить итог и отметить задачу выполненной")
            }.take(6)
        }

        val categories = listOf(
            AiCategory(listOf("куп", "магаз", "продукт", "shopping", "grocer", "buy"), listOf(
                "Проверить, что именно нужно купить",
                "Составить список и отметить самое важное",
                "Выбрать магазин или способ доставки",
                "Купить нужное и проверить чек",
                "Разложить покупки по местам"
            )),
            AiCategory(listOf("убор", "убрать", "clean", "room", "квартир", "комнат", "кухн", "ванн"), listOf(
                "Убрать лишние вещи с поверхностей",
                "Разобрать мусор и вещи не на месте",
                "Протереть поверхности",
                "Пропылесосить или помыть пол",
                "Проверить, что зона выглядит чисто"
            )),
            AiCategory(listOf("уч", "экзам", "урок", "study", "learn", "exam", "конспект", "дз", "домаш"), listOf(
                "Определить тему и что нужно сдать/понять",
                "Разобрать теорию или материалы",
                "Сделать практические задания",
                "Проверить ошибки и непонятные места",
                "Кратко повторить главное"
            )),
            AiCategory(listOf("трен", "спорт", "зал", "workout", "gym", "run", "пробеж"), listOf(
                "Подготовить форму и воду",
                "Сделать разминку",
                "Выполнить основной блок тренировки",
                "Сделать заминку и растяжку",
                "Записать результат"
            )),
            AiCategory(listOf("проект", "project", "релиз", "app", "сайт", "код", "дизайн", "презент"), listOf(
                "Сформулировать конечный результат",
                "Разбить работу на маленькие части",
                "Собрать нужные материалы или требования",
                "Сделать первый рабочий кусок",
                "Проверить результат и записать следующий шаг"
            )),
            AiCategory(listOf("напис", "write", "essay", "письм", "текст", "стать", "пост"), listOf(
                "Собрать основные мысли",
                "Составить короткий план",
                "Написать черновик",
                "Убрать лишнее и улучшить структуру",
                "Проверить текст перед отправкой"
            )),
            AiCategory(listOf("позвон", "call", "звон", "встре", "meeting", "созвон"), listOf(
                "Понять цель разговора",
                "Подготовить вопросы и факты",
                "Связаться с человеком",
                "Обсудить главное без отвлечений",
                "Записать договорённости и следующий шаг"
            )),
            AiCategory(listOf("оплат", "заплат", "pay", "bill", "счёт", "счет", "квитанц"), listOf(
                "Проверить сумму и срок",
                "Найти правильный счёт или реквизиты",
                "Открыть нужный сервис оплаты",
                "Оплатить и сохранить подтверждение",
                "Проверить, что платёж прошёл"
            )),
            AiCategory(listOf("готов", "cook", "еда", "ужин", "обед", "завтрак", "рецепт"), listOf(
                "Выбрать блюдо или рецепт",
                "Проверить продукты",
                "Подготовить ингредиенты",
                "Приготовить по шагам",
                "Убрать кухню после готовки"
            )),
            AiCategory(listOf("почин", "ремонт", "fix", "repair", "слом", "баг", "bug"), listOf(
                "Понять, что именно не работает",
                "Найти причину проблемы",
                "Подготовить нужные инструменты или файлы",
                "Исправить проблему",
                "Проверить, что всё работает"
            )),
            AiCategory(listOf("документ", "паспорт", "справк", "заявл", "document", "form"), listOf(
                "Понять, какой документ нужен",
                "Собрать данные и файлы",
                "Заполнить форму без ошибок",
                "Проверить перед отправкой",
                "Отправить или сохранить документ"
            )),
            AiCategory(listOf("поезд", "путеше", "trip", "travel", "билет", "отель"), listOf(
                "Определить даты и место",
                "Проверить билеты/маршрут",
                "Подготовить документы и вещи",
                "Забронировать нужное",
                "Сохранить подтверждения"
            )),
            AiCategory(listOf("врач", "doctor", "аптек", "лекар", "здоров"), listOf(
                "Понять симптом или цель визита",
                "Найти врача/аптеку и удобное время",
                "Подготовить документы и вопросы",
                "Записаться или купить нужное",
                "Записать рекомендации"
            ))
        )
        return categories
            .map { it to it.keywords.count { keyword -> lower.contains(keyword) } }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }
            ?.first
            ?.steps
            ?: fallbackSteps(title)
    }

    private fun fallbackSteps(title: String): List<String> {
        val action = title.split(" ").firstOrNull().orEmpty().replaceFirstChar { it.lowercase(Locale.getDefault()) }
        val objectName = title.removePrefix(title.split(" ").firstOrNull().orEmpty()).trim().ifBlank { title }
        return listOf(
            "Понять, какой результат нужен",
            "Подготовить всё для задачи: $objectName",
            if (action.isNotBlank()) "Начать: $action" else "Сделать первый маленький шаг",
            "Проверить результат и завершить"
        )
    }

    private fun extractDescriptionSteps(description: String): List<String> {
        return description
            .split("\n", ";", ",")
            .map { it.trim().trim('-', '•', '*', '—', ' ') }
            .filter { it.length >= 3 }
            .map { item ->
                val cleaned = item.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                if (cleaned.startsWith("Сделать", ignoreCase = true) || cleaned.startsWith("Проверить", ignoreCase = true)) {
                    cleaned
                } else {
                    "Сделать: $cleaned"
                }
            }
            .distinct()
    }

    private data class AiCategory(val keywords: List<String>, val steps: List<String>)
}
