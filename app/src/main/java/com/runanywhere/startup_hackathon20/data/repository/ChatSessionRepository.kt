package com.runanywhere.startup_hackathon20.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.runanywhere.startup_hackathon20.domain.model.ChatMessage
import com.runanywhere.startup_hackathon20.domain.model.ChatSession
import com.runanywhere.startup_hackathon20.domain.model.UserSettings

/**
 * Repository for managing chat sessions and user settings.
 */
class ChatSessionRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("translexa_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_CURRENT_SESSION = "current_session_id"
        private const val KEY_USER_SETTINGS = "user_settings"
    }
    
    // ===== Chat Sessions =====
    
    fun getAllSessions(): List<ChatSession> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<ChatSession>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveSession(session: ChatSession) {
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        
        if (index >= 0) {
            sessions[index] = session.copy(updatedAt = System.currentTimeMillis())
        } else {
            sessions.add(0, session)
        }
        
        saveSessions(sessions)
    }
    
    fun deleteSession(sessionId: String) {
        val sessions = getAllSessions().filter { it.id != sessionId }
        saveSessions(sessions)
    }
    
    fun clearAllSessions() {
        saveSessions(emptyList())
        prefs.edit().remove(KEY_CURRENT_SESSION).apply()
    }
    
    private fun saveSessions(sessions: List<ChatSession>) {
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()
    }
    
    fun getSession(sessionId: String): ChatSession? {
        return getAllSessions().find { it.id == sessionId }
    }
    
    fun getCurrentSessionId(): String? {
        return prefs.getString(KEY_CURRENT_SESSION, null)
    }
    
    fun setCurrentSessionId(sessionId: String?) {
        if (sessionId != null) {
            prefs.edit().putString(KEY_CURRENT_SESSION, sessionId).apply()
        } else {
            prefs.edit().remove(KEY_CURRENT_SESSION).apply()
        }
    }
    
    fun addMessageToSession(sessionId: String, message: ChatMessage) {
        val session = getSession(sessionId) ?: return
        val updatedMessages = session.messages + message
        
        // Update title from first user message if still default
        val title = if (session.title == "New Chat" && message.isUser) {
            message.text.take(30) + if (message.text.length > 30) "..." else ""
        } else {
            session.title
        }
        
        saveSession(session.copy(
            messages = updatedMessages,
            title = title,
            updatedAt = System.currentTimeMillis()
        ))
    }
    
    // ===== User Settings =====
    
    fun getUserSettings(): UserSettings {
        val json = prefs.getString(KEY_USER_SETTINGS, null) ?: return UserSettings()
        return try {
            gson.fromJson(json, UserSettings::class.java) ?: UserSettings()
        } catch (e: Exception) {
            UserSettings()
        }
    }
    
    fun saveUserSettings(settings: UserSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_USER_SETTINGS, json).apply()
    }
}
