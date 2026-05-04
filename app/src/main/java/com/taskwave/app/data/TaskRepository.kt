package com.taskwave.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.tasksDataStore by preferencesDataStore(name = "taskwave_tasks")

class TaskRepository(private val context: Context) {
    companion object {
        private val TASKS_KEY = stringPreferencesKey("tasks_json")
        private val FOLDERS_KEY = stringPreferencesKey("folders_json")
    }

    val tasks: Flow<List<TodoItem>> = context.tasksDataStore.data.map { prefs ->
        val json = prefs[TASKS_KEY] ?: return@map defaultTasks()
        try { deserialize(json) } catch (e: Exception) { defaultTasks() }
    }

    val folders: Flow<List<TaskFolder>> = context.tasksDataStore.data.map { prefs ->
        val json = prefs[FOLDERS_KEY] ?: return@map defaultFolders()
        try { deserializeFolders(json) } catch (e: Exception) { defaultFolders() }
    }

    suspend fun loadTasksSnapshot(): List<TodoItem> = tasks.first()

    suspend fun saveTasks(tasks: List<TodoItem>) {
        context.tasksDataStore.edit { it[TASKS_KEY] = serialize(tasks) }
    }

    suspend fun saveFolders(folders: List<TaskFolder>) {
        context.tasksDataStore.edit { it[FOLDERS_KEY] = serializeFolders(folders) }
    }

    private fun defaultTasks(): List<TodoItem> = emptyList()
    private fun defaultFolders(): List<TaskFolder> = emptyList()

    private fun serialize(tasks: List<TodoItem>): String {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("description", task.description)
                    .put("isDone", task.isDone)
                    .put("priority", task.priority.name)
                    .put("createdAt", task.createdAt)
                    .put("folderId", task.folderId)
                    .put("dueAt", task.dueAt)
                    .put("reminderAt", task.reminderAt)
                    .put("completedAt", task.completedAt)
            )
        }
        return array.toString()
    }

    private fun serializeFolders(folders: List<TaskFolder>): String {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("colorIndex", folder.colorIndex)
                    .put("createdAt", folder.createdAt)
            )
        }
        return array.toString()
    }

    private fun deserialize(json: String): List<TodoItem> {
        if (json.isBlank()) return emptyList()
        if (json.trim().startsWith("[")) {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TodoItem(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    description = item.optString("description"),
                    isDone = item.optBoolean("isDone"),
                    priority = runCatching { Priority.valueOf(item.optString("priority")) }.getOrDefault(Priority.MEDIUM),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    folderId = item.optNullableString("folderId"),
                    dueAt = item.optNullableLong("dueAt"),
                    reminderAt = item.optNullableLong("reminderAt"),
                    completedAt = item.optNullableLong("completedAt")
                )
            }
        }
        return json.split("|TASK|").mapNotNull { raw ->
            val parts = raw.split("|F|")
            if (parts.size < 6) return@mapNotNull null
            TodoItem(
                id = parts[0],
                title = parts[1],
                description = parts[2],
                isDone = parts[3].toBoolean(),
                priority = Priority.valueOf(parts[4]),
                createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis(),
                folderId = null
            )
        }
    }

    private fun deserializeFolders(json: String): List<TaskFolder> {
        if (json.isBlank()) return defaultFolders()
        val array = JSONArray(json)
        val folders = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            TaskFolder(
                id = item.getString("id"),
                name = item.getString("name"),
                colorIndex = item.optInt("colorIndex"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            )
        }
        return folders.ifEmpty { defaultFolders() }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private fun JSONObject.optNullableLong(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }
}
