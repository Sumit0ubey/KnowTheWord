package com.runanywhere.startup_hackathon20.domain.service

import com.runanywhere.startup_hackathon20.data.repository.ReminderRepository
import com.runanywhere.startup_hackathon20.data.repository.TaskRepository
import com.runanywhere.startup_hackathon20.domain.model.ActionIntent
import com.runanywhere.startup_hackathon20.domain.model.ActionResult
import com.runanywhere.startup_hackathon20.domain.model.ActionType
import com.runanywhere.startup_hackathon20.domain.model.AssistantResponse
import com.runanywhere.startup_hackathon20.domain.model.Reminder
import com.runanywhere.startup_hackathon20.domain.model.Task
import com.runanywhere.startup_hackathon20.domain.model.TaskPriority
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Interface for executing parsed action intents from LLM responses.
 * Handles complex actions that require LLM understanding.
 * 
 * Requirements: 4.2, 4.3, 4.4
 */
interface LLMActionExecutor {
    /**
     * Executes an action intent parsed from LLM response.
     * 
     * @param intent The parsed action intent
     * @return AssistantResponse with the result
     */
    suspend fun execute(intent: ActionIntent): AssistantResponse
    
    /**
     * Checks if this executor can handle the given action type.
     * 
     * @param actionType The type of action
     * @return true if this executor can handle it
     */
    fun canExecute(actionType: ActionType): Boolean
}

/**
 * Implementation of LLMActionExecutor that delegates to repositories.
 * Handles reminder and task management actions from LLM responses.
 * 
 * Requirements: 4.2, 4.3, 4.4
 */
class LLMActionExecutorImpl(
    private val reminderRepository: ReminderRepository,
    private val taskRepository: TaskRepository
) : LLMActionExecutor {
    
    override suspend fun execute(intent: ActionIntent): AssistantResponse {
        return try {
            when (intent.type) {
                ActionType.CREATE_REMINDER -> createReminder(intent.parameters)
                ActionType.DELETE_REMINDER -> deleteReminder(intent.parameters)
                ActionType.LIST_REMINDERS -> listReminders()
                ActionType.CREATE_TASK -> createTask(intent.parameters)
                ActionType.DELETE_TASK -> deleteTask(intent.parameters)
                ActionType.LIST_TASKS -> listTasks()
                ActionType.UNKNOWN -> AssistantResponse(
                    text = "I'm not sure how to help with that action.",
                    actionResult = ActionResult.Failure("Unknown action type"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            AssistantResponse(
                text = "I encountered an error: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    override fun canExecute(actionType: ActionType): Boolean {
        return actionType != ActionType.UNKNOWN
    }

    
    /**
     * Creates a reminder from LLM-parsed parameters.
     * Requirement 4.3: WHEN a reminder action is parsed, THE Translexa app SHALL 
     * create a local notification scheduled for the specified time
     */
    private suspend fun createReminder(parameters: Map<String, Any>): AssistantResponse {
        val title = parameters["title"]?.toString()
        val description = parameters["description"]?.toString() ?: ""
        val triggerTime = parameters["triggerTime"]?.toString()
        
        if (title.isNullOrBlank()) {
            return AssistantResponse(
                text = "I need to know what to remind you about.",
                actionResult = ActionResult.Failure("Missing reminder title"),
                shouldSpeak = true
            )
        }
        
        val triggerTimeMs = parseTriggerTime(triggerTime)
        
        val reminder = Reminder(
            title = title,
            description = description,
            triggerTimeMs = triggerTimeMs
        )
        
        val id = reminderRepository.create(reminder)
        
        val timeDescription = formatTimeDescription(triggerTimeMs)
        
        return AssistantResponse(
            text = "I've set a reminder to \"$title\" $timeDescription.",
            actionResult = ActionResult.Success("Reminder created", id),
            shouldSpeak = true
        )
    }
    
    /**
     * Deletes a reminder by ID.
     * Requirement 5.3: WHEN the user says "delete reminder [identifier]", 
     * THE Translexa app SHALL remove the specified reminder from storage
     */
    private suspend fun deleteReminder(parameters: Map<String, Any>): AssistantResponse {
        val id = extractId(parameters)
        
        if (id == null) {
            return AssistantResponse(
                text = "I need to know which reminder to delete. Please specify the reminder number.",
                actionResult = ActionResult.Failure("Missing reminder ID"),
                shouldSpeak = true
            )
        }
        
        val reminder = reminderRepository.getById(id)
        
        return if (reminder != null) {
            reminderRepository.delete(id)
            AssistantResponse(
                text = "I've deleted the reminder \"${reminder.title}\".",
                actionResult = ActionResult.Success("Reminder deleted", id),
                shouldSpeak = true
            )
        } else {
            AssistantResponse(
                text = "I couldn't find a reminder with that ID.",
                actionResult = ActionResult.Failure("Reminder not found"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Lists all reminders.
     * Requirement 4.6: WHEN the user requests to list reminders or tasks, 
     * THE Translexa app SHALL retrieve and display all stored items
     */
    private suspend fun listReminders(): AssistantResponse {
        val reminders = reminderRepository.getAll()
        
        return if (reminders.isEmpty()) {
            AssistantResponse(
                text = "You don't have any reminders set.",
                actionResult = ActionResult.Success("No reminders", emptyList<Reminder>()),
                shouldSpeak = true
            )
        } else {
            val reminderList = reminders.mapIndexed { index, reminder ->
                "${index + 1}. ${reminder.title}"
            }.joinToString("\n")
            
            val countText = if (reminders.size == 1) "1 reminder" else "${reminders.size} reminders"
            
            AssistantResponse(
                text = "You have $countText:\n$reminderList",
                actionResult = ActionResult.Success("Listed reminders", reminders),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Creates a task from LLM-parsed parameters.
     * Requirement 4.4: WHEN a task action is parsed, THE Translexa app SHALL 
     * store the task in local storage and confirm creation to the user
     */
    private suspend fun createTask(parameters: Map<String, Any>): AssistantResponse {
        val title = parameters["title"]?.toString()
        val description = parameters["description"]?.toString() ?: ""
        val priorityStr = parameters["priority"]?.toString()
        
        if (title.isNullOrBlank()) {
            return AssistantResponse(
                text = "I need to know what task to create.",
                actionResult = ActionResult.Failure("Missing task title"),
                shouldSpeak = true
            )
        }
        
        val priority = parsePriority(priorityStr)
        
        val task = Task(
            title = title,
            description = description,
            priority = priority
        )
        
        val id = taskRepository.create(task)
        
        return AssistantResponse(
            text = "I've created a task: \"$title\".",
            actionResult = ActionResult.Success("Task created", id),
            shouldSpeak = true
        )
    }
    
    /**
     * Deletes a task by ID.
     */
    private suspend fun deleteTask(parameters: Map<String, Any>): AssistantResponse {
        val id = extractId(parameters)
        
        if (id == null) {
            return AssistantResponse(
                text = "I need to know which task to delete. Please specify the task number.",
                actionResult = ActionResult.Failure("Missing task ID"),
                shouldSpeak = true
            )
        }
        
        val task = taskRepository.getById(id)
        
        return if (task != null) {
            taskRepository.delete(id)
            AssistantResponse(
                text = "I've deleted the task \"${task.title}\".",
                actionResult = ActionResult.Success("Task deleted", id),
                shouldSpeak = true
            )
        } else {
            AssistantResponse(
                text = "I couldn't find a task with that ID.",
                actionResult = ActionResult.Failure("Task not found"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Lists all tasks.
     * Requirement 4.6: WHEN the user requests to list reminders or tasks, 
     * THE Translexa app SHALL retrieve and display all stored items
     */
    private suspend fun listTasks(): AssistantResponse {
        val tasks = taskRepository.getAll()
        
        return if (tasks.isEmpty()) {
            AssistantResponse(
                text = "You don't have any tasks.",
                actionResult = ActionResult.Success("No tasks", emptyList<Task>()),
                shouldSpeak = true
            )
        } else {
            val taskList = tasks.mapIndexed { index, task ->
                val status = if (task.isCompleted) "✓" else "○"
                "$status ${index + 1}. ${task.title}"
            }.joinToString("\n")
            
            val countText = if (tasks.size == 1) "1 task" else "${tasks.size} tasks"
            
            AssistantResponse(
                text = "You have $countText:\n$taskList",
                actionResult = ActionResult.Success("Listed tasks", tasks),
                shouldSpeak = true
            )
        }
    }

    
    /**
     * Extracts an ID from parameters (supports various formats).
     */
    private fun extractId(parameters: Map<String, Any>): Long? {
        val idValue = parameters["id"] ?: parameters["reminderId"] ?: parameters["taskId"]
        
        return when (idValue) {
            is Number -> idValue.toLong()
            is String -> idValue.toLongOrNull()
            else -> null
        }
    }
    
    /**
     * Parses a trigger time string to milliseconds.
     * Supports ISO8601 format and relative times.
     */
    private fun parseTriggerTime(triggerTime: String?): Long {
        if (triggerTime.isNullOrBlank()) {
            // Default to 1 hour from now
            return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        }
        
        // Try ISO8601 format first
        try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            )
            
            for (format in formats) {
                try {
                    val date = format.parse(triggerTime)
                    if (date != null) {
                        return date.time
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }
        } catch (e: Exception) {
            // Fall through to relative time parsing
        }
        
        // Try relative time parsing
        val lowerTime = triggerTime.lowercase()
        val now = System.currentTimeMillis()
        
        // Handle "in X minutes/hours"
        val inPattern = Regex("in\\s+(\\d+)\\s*(minutes?|hours?|days?)")
        inPattern.find(lowerTime)?.let { match ->
            val value = match.groupValues[1].toLongOrNull() ?: 0
            val unit = match.groupValues[2]
            val millis = when {
                unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(value)
                unit.startsWith("hour") -> TimeUnit.HOURS.toMillis(value)
                unit.startsWith("day") -> TimeUnit.DAYS.toMillis(value)
                else -> TimeUnit.MINUTES.toMillis(value)
            }
            return now + millis
        }
        
        // Default to 1 hour from now
        return now + TimeUnit.HOURS.toMillis(1)
    }
    
    /**
     * Formats a timestamp into a human-readable description.
     */
    private fun formatTimeDescription(timeMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = timeMs - now
        
        return when {
            diffMs < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
                "in $minutes minutes"
            }
            diffMs < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
                "in $hours hours"
            }
            else -> {
                val format = SimpleDateFormat("MMM d 'at' h:mm a", Locale.US)
                "on ${format.format(timeMs)}"
            }
        }
    }
    
    /**
     * Parses a priority string to TaskPriority enum.
     */
    private fun parsePriority(priorityStr: String?): TaskPriority {
        return when (priorityStr?.lowercase()) {
            "high", "urgent", "important" -> TaskPriority.HIGH
            "low", "minor" -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }
    }
}
