package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.domain.model.*
import com.runanywhere.startup_hackathon20.domain.service.ContextManagerImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for ContextManager operations.
 */
class ContextManagerPropertyTest : FunSpec({
    
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
    
    /**
     * **Feature: offline-voice-assistant, Property 3: Context Window Bounds**
     * 
     * For any conversation history of N messages where N > 10, calling getRecentContext(10) 
     * should return exactly 10 messages, and they should be the 10 most recent messages 
     * in chronological order.
     * 
     * **Validates: Requirements 2.3**
     */
    test("Feature offline-voice-assistant Property 3 - Context Window Bounds") {
        checkAll(100, Arb.list(arbChatMessage, 11..50)) { messages ->
            val contextManager = ContextManagerImpl(maxContextSize = 10)
            
            // Add all messages to context manager
            messages.forEach { contextManager.addMessage(it) }
            
            // Get recent context with limit of 10
            val recentContext = contextManager.getRecentContext(10)
            
            // Verify exactly 10 messages are returned
            recentContext.size shouldBe 10
            
            // Verify they are the 10 most recent messages (last 10 added)
            val expectedMessages = messages.takeLast(10)
            recentContext.map { it.id } shouldContainExactly expectedMessages.map { it.id }
            recentContext.map { it.text } shouldContainExactly expectedMessages.map { it.text }
            recentContext.map { it.isUser } shouldContainExactly expectedMessages.map { it.isUser }
            recentContext.map { it.timestamp } shouldContainExactly expectedMessages.map { it.timestamp }
        }
    }
    
    /**
     * Additional property test: Context returns all messages when count is less than limit
     */
    test("Context returns all messages when count is less than limit") {
        checkAll(100, Arb.list(arbChatMessage, 1..9)) { messages ->
            val contextManager = ContextManagerImpl(maxContextSize = 10)
            
            // Add all messages to context manager
            messages.forEach { contextManager.addMessage(it) }
            
            // Get recent context with limit of 10
            val recentContext = contextManager.getRecentContext(10)
            
            // Verify all messages are returned since count < limit
            recentContext.size shouldBe messages.size
            recentContext.map { it.id } shouldContainExactly messages.map { it.id }
        }
    }
    
    /**
     * Additional property test: Clear empties context
     */
    test("Clear empties context") {
        checkAll(100, Arb.list(arbChatMessage, 1..20)) { messages ->
            val contextManager = ContextManagerImpl(maxContextSize = 10)
            
            // Add all messages
            messages.forEach { contextManager.addMessage(it) }
            
            // Verify messages exist
            contextManager.messageCount() shouldBe messages.size
            
            // Clear context
            contextManager.clear()
            
            // Verify empty
            contextManager.messageCount() shouldBe 0
            contextManager.getRecentContext() shouldBe emptyList()
        }
    }
})
