package com.runanywhere.startup_hackathon20.domain.service

import com.runanywhere.startup_hackathon20.domain.model.ChatMessage

/**
 * Interface for managing conversation history and context window for LLM.
 * Maintains the last N messages for contextual responses.
 * 
 * Requirements: 2.3
 */
interface ContextManager {
    /**
     * Adds a message to the conversation history.
     * @param message The ChatMessage to add
     */
    fun addMessage(message: ChatMessage)
    
    /**
     * Gets the most recent messages from conversation history.
     * @param maxMessages Maximum number of messages to return (default: 10)
     * @return List of recent messages in chronological order (oldest first)
     */
    fun getRecentContext(maxMessages: Int = 10): List<ChatMessage>
    
    /**
     * Builds a prompt string with conversation context for LLM.
     * @param userQuery The current user query to append
     * @return Formatted prompt string with context
     */
    fun buildPromptWithContext(userQuery: String): String
    
    /**
     * Clears all conversation history.
     */
    fun clear()
    
    /**
     * Returns the total number of messages in history.
     */
    fun messageCount(): Int
}

/**
 * Implementation of ContextManager that maintains conversation history in memory.
 * Limits context to the most recent messages for efficient LLM prompting.
 */
class ContextManagerImpl(
    private val maxContextSize: Int = 10
) : ContextManager {
    
    private val messages = mutableListOf<ChatMessage>()
    
    override fun addMessage(message: ChatMessage) {
        messages.add(message)
    }
    
    override fun getRecentContext(maxMessages: Int): List<ChatMessage> {
        val limit = minOf(maxMessages, maxContextSize)
        return if (messages.size <= limit) {
            messages.toList()
        } else {
            messages.takeLast(limit)
        }
    }
    
    override fun buildPromptWithContext(userQuery: String): String {
        val recentMessages = getRecentContext()
        
        if (recentMessages.isEmpty()) {
            return userQuery
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Previous conversation:\n")
        
        for (message in recentMessages) {
            val role = if (message.isUser) "User" else "Assistant"
            contextBuilder.append("$role: ${message.text}\n")
        }
        
        contextBuilder.append("\nCurrent query:\nUser: $userQuery")
        
        return contextBuilder.toString()
    }
    
    override fun clear() {
        messages.clear()
    }
    
    override fun messageCount(): Int {
        return messages.size
    }
}
