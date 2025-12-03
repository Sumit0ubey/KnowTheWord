package com.runanywhere.startup_hackathon20.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.runanywhere.startup_hackathon20.domain.model.Task

/**
 * Repository interface for managing tasks.
 * Provides CRUD operations for task persistence.
 */
interface TaskRepository {
    suspend fun create(task: Task): Long
    suspend fun getAll(): List<Task>
    suspend fun delete(id: Long)
    suspend fun update(task: Task)
    suspend fun getById(id: Long): Task?
}

/**
 * Implementation of TaskRepository using SharedPreferences with JSON serialization.
 * Persists tasks to local storage for offline access.
 * 
 * Requirements: 4.4, 5.5
 */
class TaskRepositoryImpl(
    private val context: Context
) : TaskRepository {
    
    companion object {
        private const val PREFS_NAME = "translexa_tasks"
        private const val KEY_TASKS = "tasks"
        private const val KEY_NEXT_ID = "next_id"
    }
    
    private val gson: Gson = GsonBuilder().create()
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Creates a new task and persists it to storage.
     * Returns the assigned ID.
     * 
     * Requirement 4.4: WHEN a task action is parsed, THE Translexa app SHALL 
     * store the task in local storage and confirm creation to the user
     */
    override suspend fun create(task: Task): Long {
        val tasks = getAll().toMutableList()
        val newId = getNextId()
        val taskWithId = task.copy(id = newId)
        tasks.add(taskWithId)
        saveTasks(tasks)
        incrementNextId()
        return newId
    }

    /**
     * Retrieves all tasks from local storage.
     * 
     * Requirement 4.6: WHEN the user requests to list reminders or tasks, 
     * THE Translexa app SHALL retrieve and display all stored items
     */
    override suspend fun getAll(): List<Task> {
        val json = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Task>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Deletes a task by ID.
     */
    override suspend fun delete(id: Long) {
        val tasks = getAll().toMutableList()
        tasks.removeAll { it.id == id }
        saveTasks(tasks)
    }
    
    /**
     * Updates an existing task.
     */
    override suspend fun update(task: Task) {
        val tasks = getAll().toMutableList()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(tasks)
        }
    }
    
    /**
     * Retrieves a task by ID.
     * 
     * Requirement 5.5: WHEN the app restarts, THE Translexa app SHALL 
     * restore all previously saved reminders and tasks from local storage
     */
    override suspend fun getById(id: Long): Task? {
        return getAll().find { it.id == id }
    }
    
    private fun saveTasks(tasks: List<Task>) {
        val json = gson.toJson(tasks)
        prefs.edit().putString(KEY_TASKS, json).apply()
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
 * In-memory implementation of TaskRepository for testing purposes.
 * Does not require Android Context.
 */
class InMemoryTaskRepository : TaskRepository {
    
    private val tasks = mutableListOf<Task>()
    private var nextId = 1L
    
    override suspend fun create(task: Task): Long {
        val id = nextId++
        tasks.add(task.copy(id = id))
        return id
    }
    
    override suspend fun getAll(): List<Task> {
        return tasks.toList()
    }
    
    override suspend fun delete(id: Long) {
        tasks.removeAll { it.id == id }
    }
    
    override suspend fun update(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
        }
    }
    
    override suspend fun getById(id: Long): Task? {
        return tasks.find { it.id == id }
    }
}
