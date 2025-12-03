package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a reminder created by the user.
 * Supports JSON serialization for persistence.
 */
data class Reminder(
    @SerializedName("id")
    val id: Long = 0,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("description")
    val description: String = "",
    
    @SerializedName("triggerTimeMs")
    val triggerTimeMs: Long,
    
    @SerializedName("isCompleted")
    val isCompleted: Boolean = false,
    
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
