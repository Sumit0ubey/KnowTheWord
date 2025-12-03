package com.runanywhere.startup_hackathon20.property

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.runanywhere.startup_hackathon20.domain.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for ActionIntent JSON serialization.
 * 
 * **Feature: offline-voice-assistant, Property 2: Action Intent JSON Round-Trip**
 * **Validates: Requirements 4.2**
 */
class ActionIntentPropertyTest : FunSpec({
    
    val gson: Gson = GsonBuilder().create()
    
    // Arbitrary generator for ActionType
    val arbActionType = Arb.enum<ActionType>()
    
    // Arbitrary generator for parameter values (strings only for JSON compatibility)
    val arbParameterValue: Arb<Any> = Arb.choice(
        Arb.string(0..50),
        Arb.int(),
        Arb.long(),
        Arb.boolean()
    )
    
    // Arbitrary generator for ActionIntent with string-only parameters for reliable JSON round-trip
    val arbActionIntent = arbitrary {
        ActionIntent(
            type = arbActionType.bind(),
            parameters = Arb.map(
                Arb.string(1..20).filter { it.isNotBlank() && !it.contains("\"") },
                Arb.string(0..50),
                minSize = 0,
                maxSize = 5
            ).bind()
        )
    }
    
    test("**Feature: offline-voice-assistant, Property 2: Action Intent JSON Round-Trip**") {
        /**
         * For any valid ActionIntent object, serializing to JSON and then parsing 
         * should produce an equivalent ActionIntent with the same type and parameters.
         * 
         * **Validates: Requirements 4.2**
         */
        checkAll(100, arbActionIntent) { actionIntent ->
            // Serialize to JSON
            val json = gson.toJson(actionIntent)
            
            // Deserialize from JSON
            val deserialized = gson.fromJson(json, ActionIntent::class.java)
            
            // Verify round-trip produces equivalent ActionIntent
            deserialized.type shouldBe actionIntent.type
            deserialized.parameters.size shouldBe actionIntent.parameters.size
            
            // Verify all parameters are preserved
            actionIntent.parameters.forEach { (key, value) ->
                deserialized.parameters[key] shouldBe value
            }
        }
    }
    
    test("ActionIntent with empty parameters serializes correctly") {
        val actionIntent = ActionIntent(
            type = ActionType.LIST_REMINDERS,
            parameters = emptyMap()
        )
        
        val json = gson.toJson(actionIntent)
        val deserialized = gson.fromJson(json, ActionIntent::class.java)
        
        deserialized.type shouldBe actionIntent.type
        deserialized.parameters shouldBe emptyMap()
    }
    
    test("ActionIntent with complex parameters serializes correctly") {
        val actionIntent = ActionIntent(
            type = ActionType.CREATE_REMINDER,
            parameters = mapOf(
                "title" to "Pack hiking gear",
                "description" to "Don't forget water bottles",
                "triggerTime" to "2025-12-02T08:00:00"
            )
        )
        
        val json = gson.toJson(actionIntent)
        val deserialized = gson.fromJson(json, ActionIntent::class.java)
        
        deserialized.type shouldBe actionIntent.type
        deserialized.parameters["title"] shouldBe "Pack hiking gear"
        deserialized.parameters["description"] shouldBe "Don't forget water bottles"
        deserialized.parameters["triggerTime"] shouldBe "2025-12-02T08:00:00"
    }
})
