package com.runanywhere.startup_hackathon20.domain.model

import java.util.UUID

/**
 * Represents a chat session with multiple messages.
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val preview: String
        get() = messages.lastOrNull()?.text?.take(100) ?: "No messages"
    
    val messageCount: Int
        get() = messages.size
}

/**
 * User settings for personalization.
 */
data class UserSettings(
    val name: String = "",
    val age: Int = 0,
    val gender: String = "male",
    val profession: String = "",
    val interests: String = "",
    val customInstructions: String = "",
    val ttsEnabled: Boolean = true,
    val voiceCharacter: String = "Nova (Default)",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f
) {
    /**
     * Builds a personalization prompt for the AI.
     */
    fun buildPersonalizationPrompt(): String {
        val parts = mutableListOf<String>()
        
        if (name.isNotBlank()) {
            parts.add("User's name is $name")
        }
        if (age > 0) {
            parts.add("User is $age years old")
        }
        if (profession.isNotBlank()) {
            parts.add("User works in $profession")
        }
        if (interests.isNotBlank()) {
            parts.add("User is interested in: $interests")
        }
        if (customInstructions.isNotBlank()) {
            parts.add("Special instructions: $customInstructions")
        }
        
        return if (parts.isNotEmpty()) {
            "Context about the user: ${parts.joinToString(". ")}. "
        } else {
            ""
        }
    }
}
