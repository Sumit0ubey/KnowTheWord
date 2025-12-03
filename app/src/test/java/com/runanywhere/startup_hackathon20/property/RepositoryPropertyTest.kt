package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.data.repository.InMemoryMessageRepository
import com.runanywhere.startup_hackathon20.data.repository.InMemoryReminderRepository
import com.runanywhere.startup_hackathon20.data.repository.InMemoryTaskRepository
import com.runanywhere.startup_hackathon20.domain.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking

/**
 * Property-based tests for repository operations.
 */
class RepositoryPropertyTest : FunSpec({
    
    // Arbitrary generator for ActionType
    val arbActionType = Arb.enum<ActionType>()
    
    // Arbitrary generator for ActionIntent
    val arbActionIntent = arbitrary {
        ActionIntent(
            type = arbActionType.bind(),
            parameters = Arb.map(
                Arb.string(1..20).filter { it.isNotBlank() },
                Arb.string(0..50),
                minSize = 0,
                maxSize = 5
            ).bind()
        )
    }
    
    // Arbitrary generator for MessageMetadata
    val arbMessageMetadata = arbitrary {
        val hasActionIntent = Arb.boolean().bind()
        val hasConfidence = Arb.boolean().bind()
        MessageMetadata(
            actionIntent = if (hasActionIntent) arbActionIntent.bind() else null,
            transcriptionConfidence = if (hasConfidence) Arb.float(0f..1f).bind() else null
        )
    }

    // Arbitrary generator for ChatMessage
    val arbChatMessage = arbitrary {
        val hasMetadata = Arb.boolean().bind()
        ChatMessage(
            id = Arb.long(0L..Long.MAX_VALUE).bind(),
            text = Arb.string(0..500).bind(),
            isUser = Arb.boolean().bind(),
            timestamp = Arb.long(0L..System.currentTimeMillis()).bind(),
            metadata = if (hasMetadata) arbMessageMetadata.bind() else null
        )
    }
    
    // Arbitrary generator for TaskPriority
    val arbTaskPriority = Arb.enum<TaskPriority>()
    
    // Arbitrary generator for Reminder
    val arbReminder = arbitrary {
        Reminder(
            id = Arb.long(1L..Long.MAX_VALUE).bind(),
            title = Arb.string(1..100).filter { it.isNotBlank() }.bind(),
            description = Arb.string(0..200).bind(),
            triggerTimeMs = Arb.long(System.currentTimeMillis()..System.currentTimeMillis() + 86400000L * 365).bind(),
            isCompleted = Arb.boolean().bind(),
            createdAt = Arb.long(0L..System.currentTimeMillis()).bind()
        )
    }
    
    // Arbitrary generator for Task
    val arbTask = arbitrary {
        Task(
            id = Arb.long(1L..Long.MAX_VALUE).bind(),
            title = Arb.string(1..100).filter { it.isNotBlank() }.bind(),
            description = Arb.string(0..200).bind(),
            isCompleted = Arb.boolean().bind(),
            priority = arbTaskPriority.bind(),
            createdAt = Arb.long(0L..System.currentTimeMillis()).bind()
        )
    }
    
    // Arbitrary generator for list of ChatMessages
    val arbChatMessageList = Arb.list(arbChatMessage, 0..10)
    
    // Arbitrary generator for list of Reminders
    val arbReminderList = Arb.list(arbReminder, 1..10)
    
    // Arbitrary generator for list of Tasks
    val arbTaskList = Arb.list(arbTask, 1..10)


    /**
     * **Feature: offline-voice-assistant, Property 6: Message Addition Increases Count**
     * 
     * For any MessageRepository with N messages, adding a new message should result 
     * in the repository containing N+1 messages, and the new message should be retrievable.
     * 
     * **Validates: Requirements 8.1**
     */
    test("**Feature: offline-voice-assistant, Property 6: Message Addition Increases Count**") {
        checkAll(100, arbChatMessageList, arbChatMessage) { initialMessages, newMessage ->
            runBlocking {
                val repository = InMemoryMessageRepository()
                
                // Add initial messages
                initialMessages.forEach { repository.saveMessage(it) }
                val initialCount = repository.getAllMessages().size
                
                // Add new message
                repository.saveMessage(newMessage)
                
                // Verify count increased by 1
                val finalCount = repository.getAllMessages().size
                finalCount shouldBe initialCount + 1
                
                // Verify the new message is retrievable
                val allMessages = repository.getAllMessages()
                allMessages.last().text shouldBe newMessage.text
                allMessages.last().isUser shouldBe newMessage.isUser
            }
        }
    }
    
    /**
     * **Feature: offline-voice-assistant, Property 9: Clear History Empties Storage**
     * 
     * For any MessageRepository with N > 0 messages, calling clearAll() should 
     * result in getAllMessages() returning an empty list.
     * 
     * **Validates: Requirements 8.3**
     */
    test("**Feature: offline-voice-assistant, Property 9: Clear History Empties Storage**") {
        checkAll(100, Arb.list(arbChatMessage, 1..20)) { messages ->
            runBlocking {
                val repository = InMemoryMessageRepository()
                
                // Add messages
                messages.forEach { repository.saveMessage(it) }
                
                // Verify messages exist
                repository.getAllMessages().size shouldBe messages.size
                
                // Clear all
                repository.clearAll()
                
                // Verify empty
                repository.getAllMessages() shouldBe emptyList()
            }
        }
    }


    /**
     * **Feature: offline-voice-assistant, Property 4: Reminder Persistence Round-Trip**
     * 
     * For any Reminder object, saving to storage and then retrieving by ID should 
     * return an equivalent Reminder with identical title, description, and triggerTimeMs.
     * 
     * **Validates: Requirements 5.5**
     */
    test("**Feature: offline-voice-assistant, Property 4: Reminder Persistence Round-Trip**") {
        checkAll(100, arbReminder) { reminder ->
            runBlocking {
                val repository = InMemoryReminderRepository()
                
                // Create reminder
                val id = repository.create(reminder)
                
                // Retrieve by ID
                val retrieved = repository.getById(id)
                
                // Verify round-trip produces equivalent reminder
                retrieved shouldNotBe null
                retrieved!!.title shouldBe reminder.title
                retrieved.description shouldBe reminder.description
                retrieved.triggerTimeMs shouldBe reminder.triggerTimeMs
                retrieved.isCompleted shouldBe reminder.isCompleted
            }
        }
    }
    
    /**
     * **Feature: offline-voice-assistant, Property 7: Reminder Deletion Decreases Count**
     * 
     * For any ReminderRepository with N reminders where N > 0, deleting an existing 
     * reminder should result in N-1 reminders, and the deleted reminder should not be retrievable.
     * 
     * **Validates: Requirements 5.3**
     */
    test("**Feature: offline-voice-assistant, Property 7: Reminder Deletion Decreases Count**") {
        checkAll(100, arbReminderList) { reminders ->
            runBlocking {
                val repository = InMemoryReminderRepository()
                
                // Create all reminders and collect their IDs
                val ids = reminders.map { repository.create(it) }
                val initialCount = repository.getAll().size
                
                // Pick a random reminder to delete
                val idToDelete = ids.random()
                
                // Delete the reminder
                repository.delete(idToDelete)
                
                // Verify count decreased by 1
                val finalCount = repository.getAll().size
                finalCount shouldBe initialCount - 1
                
                // Verify deleted reminder is not retrievable
                repository.getById(idToDelete) shouldBe null
            }
        }
    }
    
    /**
     * **Feature: offline-voice-assistant, Property 12: List Operations Return All Items**
     * 
     * For any set of N reminders created via the ReminderRepository, 
     * calling getAll() should return exactly N reminders.
     * 
     * **Validates: Requirements 4.6, 5.2**
     */
    test("**Feature: offline-voice-assistant, Property 12: List Operations Return All Items**") {
        checkAll(100, arbReminderList) { reminders ->
            runBlocking {
                val repository = InMemoryReminderRepository()
                
                // Create all reminders
                reminders.forEach { repository.create(it) }
                
                // Verify getAll returns exactly N reminders
                val allReminders = repository.getAll()
                allReminders.size shouldBe reminders.size
            }
        }
    }
    
    /**
     * **Feature: offline-voice-assistant, Property 5: Task Persistence Round-Trip**
     * 
     * For any Task object, saving to storage and then retrieving should return 
     * an equivalent Task with identical title, description, and priority.
     * 
     * **Validates: Requirements 5.5**
     */
    test("**Feature: offline-voice-assistant, Property 5: Task Persistence Round-Trip**") {
        checkAll(100, arbTask) { task ->
            runBlocking {
                val repository = InMemoryTaskRepository()
                
                // Create task
                val id = repository.create(task)
                
                // Retrieve by ID
                val retrieved = repository.getById(id)
                
                // Verify round-trip produces equivalent task
                retrieved shouldNotBe null
                retrieved!!.title shouldBe task.title
                retrieved.description shouldBe task.description
                retrieved.priority shouldBe task.priority
                retrieved.isCompleted shouldBe task.isCompleted
            }
        }
    }
})
