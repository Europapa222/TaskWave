package com.taskwave.app.data

import java.util.UUID

enum class Priority { LOW, MEDIUM, HIGH }

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isDone: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val createdAt: Long = System.currentTimeMillis()
)
