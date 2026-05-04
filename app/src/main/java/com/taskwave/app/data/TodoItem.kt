package com.taskwave.app.data

import java.util.UUID

enum class Priority { LOW, MEDIUM, HIGH }

data class TaskFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val createdAt: Long = System.currentTimeMillis(),
    val folderId: String? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val completedAt: Long? = null
)
