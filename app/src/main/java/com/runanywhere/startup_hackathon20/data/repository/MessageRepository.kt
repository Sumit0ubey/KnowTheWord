package com.runanywhere.startup_hackathon20.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.runanywhere.startup_hackathon20.domain.model.ChatMessage

/**
 * Repository interface for managing chat messages.
 * Provides persistence operations for conversation history.
 */
interface MessageRepository {
    suspend fun saveMessage(message: ChatMessage)
    suspend fun getAllMessages(): List<ChatMessage>
    suspend fun clearAll()
}

/**
 * Implementation of MessageRepository using SharedPreferences with JSON serialization.
 * Persists chat messages to local storage for offline access.
 * 
 * Requirements: 8.1, 8.2, 8.3
 */
class MessageRepositoryImpl(
    private val context: Context
) : MessageRepository {
    
    companion object {
        private const val PREFS_NAME = "translexa_messages"
        private const val KEY_MESSAGES = "chat_messages"
    }
    
    private val gson: Gson = GsonBuilder().create()
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Saves a message to local storage.
     * Appends the message to the existing list and persists immediately.
     * 
     * Requirement 8.1: WHEN a new message is added to the conversation, 
     * THE Translexa app SHALL persist it to local storage immediately
     */
    override suspend fun saveMessage(message: ChatMessage) {
        val messages = getAllMessages().toMutableList()
        messages.add(message)
        saveMessages(messages)
    }

    /**
     * Retrieves all messages from local storage.
     * 
     * Requirement 8.2: WHEN the app launches, THE Translexa app SHALL 
     * restore the previous conversation history from local storage
     */
    override suspend fun getAllMessages(): List<ChatMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clears all messages from local storage.
     * 
     * Requirement 8.3: WHEN the user requests to clear conversation history, 
     * THE Translexa app SHALL delete all stored messages and reset the context
     */
    override suspend fun clearAll() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }
    
    private fun saveMessages(messages: List<ChatMessage>) {
        val json = gson.toJson(messages)
        prefs.edit().putString(KEY_MESSAGES, json).apply()
    }
}

/**
 * In-memory implementation of MessageRepository for testing purposes.
 * Does not require Android Context.
 */
class InMemoryMessageRepository : MessageRepository {
    
    private val messages = mutableListOf<ChatMessage>()
    
    override suspend fun saveMessage(message: ChatMessage) {
        messages.add(message)
    }
    
    override suspend fun getAllMessages(): List<ChatMessage> {
        return messages.toList()
    }
    
    override suspend fun clearAll() {
        messages.clear()
    }
}
