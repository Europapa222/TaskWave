package com.taskwave.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tasksDataStore by preferencesDataStore(name = "taskwave_tasks")

class TaskRepository(private val context: Context) {
    companion object {
        private val TASKS_KEY = stringPreferencesKey("tasks_json")
    }

    val tasks: Flow<List<TodoItem>> = context.tasksDataStore.data.map { prefs ->
        val json = prefs[TASKS_KEY] ?: return@map defaultTasks()
        try { deserialize(json) } catch (e: Exception) { defaultTasks() }
    }

    suspend fun saveTasks(tasks: List<TodoItem>) {
        context.tasksDataStore.edit { it[TASKS_KEY] = serialize(tasks) }
    }

    private fun defaultTasks(): List<TodoItem> = emptyList()

    private fun serialize(tasks: List<TodoItem>): String {
        return tasks.joinToString("|TASK|") { t ->
            listOf(t.id, t.title, t.description, t.isDone.toString(), t.priority.name, t.createdAt.toString())
                .joinToString("|F|")
        }
    }

    private fun deserialize(json: String): List<TodoItem> {
        if (json.isBlank()) return emptyList()
        return json.split("|TASK|").mapNotNull { raw ->
            val parts = raw.split("|F|")
            if (parts.size < 6) return@mapNotNull null
            TodoItem(
                id = parts[0],
                title = parts[1],
                description = parts[2],
                isDone = parts[3].toBoolean(),
                priority = Priority.valueOf(parts[4]),
                createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }
}
