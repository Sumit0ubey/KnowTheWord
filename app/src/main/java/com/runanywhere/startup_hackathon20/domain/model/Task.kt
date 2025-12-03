package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Priority levels for tasks.
 */
enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Represents a task created by the user.
 * Supports JSON serialization for persistence.
 */
data class Task(
    @SerializedName("id")
    val id: Long = 0,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String = "",
    
    @SerializedName("isCompleted")
    val isCompleted: Boolean = false,
    
    @SerializedName("priority")
    val priority: TaskPriority = TaskPriority.MEDIUM,
    
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
