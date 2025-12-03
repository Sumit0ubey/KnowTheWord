package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single message in the chat conversation.
 * Supports JSON serialization for persistence.
 */
data class ChatMessage(
    @SerializedName("id")
    val id: Long = 0,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("isUser")
    val isUser: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("metadata")
    val metadata: MessageMetadata? = null,
    
    // For streaming display - not persisted
    @Transient
    val isStreaming: Boolean = false
)

/**
 * Additional metadata associated with a chat message.
 */
data class MessageMetadata(
    @SerializedName("actionIntent")
    val actionIntent: ActionIntent? = null,
    
    @SerializedName("transcriptionConfidence")
    val transcriptionConfidence: Float? = null
)
