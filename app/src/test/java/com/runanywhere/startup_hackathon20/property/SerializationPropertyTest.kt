package com.runanywhere.startup_hackathon20.property

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.runanywhere.startup_hackathon20.domain.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for serialization round-trips.
 * 
 * **Feature: offline-voice-assistant, Property 1: Conversation Serialization Round-Trip**
 * **Validates: Requirements 8.4, 8.5**
 */
class SerializationPropertyTest : FunSpec({
    
    val gson: Gson = GsonBuilder().create()
    
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
    
    // Arbitrary generator for list of ChatMessages
    val arbChatMessageList = Arb.list(arbChatMessage, 0..20)
    
    test("**Feature: offline-voice-assistant, Property 1: Conversation Serialization Round-Trip**") {
        /**
         * For any list of ChatMessage objects, serializing to JSON and then 
         * deserializing should produce an equivalent list of messages with 
         * identical text, isUser, and timestamp values.
         * 
         * **Validates: Requirements 8.4, 8.5**
         */
        checkAll(100, arbChatMessageList) { messages ->
            // Serialize to JSON
            val json = gson.toJson(messages)
            
            // Deserialize from JSON
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val deserialized: List<ChatMessage> = gson.fromJson(json, type)
            
            // Verify round-trip produces equivalent messages
            deserialized.size shouldBe messages.size
            
            messages.zip(deserialized).forEach { (original, restored) ->
                restored.id shouldBe original.id
                restored.text shouldBe original.text
                restored.isUser shouldBe original.isUser
                restored.timestamp shouldBe original.timestamp
                
                // Verify metadata if present
                if (original.metadata != null) {
                    restored.metadata shouldBe original.metadata
                }
            }
        }
    }
})
