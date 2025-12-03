package com.runanywhere.startup_hackathon20.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.runanywhere.startup_hackathon20.domain.model.Reminder

/**
 * Repository interface for managing reminders.
 * Provides CRUD operations for reminder persistence.
 */
interface ReminderRepository {
    suspend fun create(reminder: Reminder): Long
    suspend fun getAll(): List<Reminder>
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): Reminder?
}

/**
 * Implementation of ReminderRepository using SharedPreferences with JSON serialization.
 * Persists reminders to local storage for offline access.
 * 
 * Requirements: 5.2, 5.3, 5.5
 */
class ReminderRepositoryImpl(
    private val context: Context
) : ReminderRepository {
    
    companion object {
        private const val PREFS_NAME = "translexa_reminders"
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_NEXT_ID = "next_id"
    }
    
    private val gson: Gson = GsonBuilder().create()
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Creates a new reminder and persists it to storage.
     * Returns the assigned ID.
     * 
     * Requirement 5.1: WHEN the user says "remind me to [task] at [time]", 
     * THE Translexa app SHALL create a reminder with the parsed task and time
     */
    override suspend fun create(reminder: Reminder): Long {
        val reminders = getAll().toMutableList()
        val newId = getNextId()
        val reminderWithId = reminder.copy(id = newId)
        reminders.add(reminderWithId)
        saveReminders(reminders)
        incrementNextId()
        return newId
    }

    /**
     * Retrieves all reminders from local storage.
     * 
     * Requirement 5.2: WHEN the user says "show my reminders" or "list tasks", 
     * THE Translexa app SHALL display all active reminders and tasks
     */
    override suspend fun getAll(): List<Reminder> {
        val json = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Reminder>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Deletes a reminder by ID.
     * 
     * Requirement 5.3: WHEN the user says "delete reminder [identifier]", 
     * THE Translexa app SHALL remove the specified reminder from storage
     */
    override suspend fun delete(id: Long) {
        val reminders = getAll().toMutableList()
        reminders.removeAll { it.id == id }
        saveReminders(reminders)
    }
    
    /**
     * Retrieves a reminder by ID.
     * 
     * Requirement 5.5: WHEN the app restarts, THE Translexa app SHALL 
     * restore all previously saved reminders and tasks from local storage
     */
    override suspend fun getById(id: Long): Reminder? {
        return getAll().find { it.id == id }
    }
    
    private fun saveReminders(reminders: List<Reminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString(KEY_REMINDERS, json).apply()
    }
    
    private fun getNextId(): Long {
        return prefs.getLong(KEY_NEXT_ID, 1L)
    }
    
    private fun incrementNextId() {
        val nextId = getNextId()
        prefs.edit().putLong(KEY_NEXT_ID, nextId + 1).apply()
    }
}

/**
 * In-memory implementation of ReminderRepository for testing purposes.
 * Does not require Android Context.
 */
class InMemoryReminderRepository : ReminderRepository {
    
    private val reminders = mutableListOf<Reminder>()
    private var nextId = 1L
    
    override suspend fun create(reminder: Reminder): Long {
        val id = nextId++
        reminders.add(reminder.copy(id = id))
        return id
    }
    
    override suspend fun getAll(): List<Reminder> {
        return reminders.toList()
    }
    
    override suspend fun delete(id: Long) {
        reminders.removeAll { it.id == id }
    }
    
    override suspend fun getById(id: Long): Reminder? {
        return reminders.find { it.id == id }
    }
}
