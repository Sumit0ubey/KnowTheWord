package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.domain.model.ActionType
import com.runanywhere.startup_hackathon20.domain.service.LLMResponseParserImpl
import com.runanywhere.startup_hackathon20.domain.service.ParsedResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for LLMResponseParser.
 * 
 * Feature: offline-voice-assistant, Property 8: Invalid JSON Falls Back to Conversation
 * Validates: Requirements 4.5
 */
class LLMResponseParserPropertyTest : FunSpec({
    
    val parser = LLMResponseParserImpl()
    
    // Arbitrary generator for strings that are NOT valid JSON
    // These include: plain text, malformed JSON, incomplete JSON
    val arbInvalidJson = Arb.choice(
        // Plain text without any JSON structure
        Arb.string(1..200).filter { !it.contains("{") && !it.contains("}") },
        // Text with unbalanced braces
        Arb.string(1..100).map { "{ $it" },
        // Text with only closing brace
        Arb.string(1..100).map { "$it }" },
        // Malformed JSON (missing quotes, colons, etc.)
        Arb.string(1..50).map { "{ type: $it }" },
        // JSON with wrong structure (array instead of object content)
        Arb.string(1..50).map { """{ "type": [$it] }""" }
    )
    
    // Arbitrary generator for JSON with unrecognized action types
    val arbUnrecognizedActionJson = Arb.string(5..30)
        .filter { actionName ->
            // Filter out strings that match valid ActionType values
            ActionType.entries.none { it.name.equals(actionName, ignoreCase = true) }
        }
        .filter { it.isNotBlank() && !it.contains("\"") && !it.contains("\\") }
        .map { actionName ->
            """{"type":"action","action":"$actionName","parameters":{}}"""
        }
    
    // Arbitrary generator for JSON missing required "type" field
    val arbMissingTypeJson = Arb.string(1..50)
        .filter { !it.contains("\"") && !it.contains("\\") }
        .map { text ->
            """{"action":"CREATE_REMINDER","text":"$text"}"""
        }


    /**
     * Feature: offline-voice-assistant, Property 8: Invalid JSON Falls Back to Conversation
     * 
     * For any string that is not valid JSON or does not contain a recognized action type,
     * the LLMResponseParser should return a Conversation response containing the original text.
     * 
     * Validates: Requirements 4.5
     */
    test("Property 8 - Invalid JSON Falls Back to Conversation") {
        checkAll(100, arbInvalidJson) { invalidInput ->
            val result = parser.parse(invalidInput)
            
            // Should return Conversation type
            result.shouldBeInstanceOf<ParsedResponse.Conversation>()
            
            // The conversation text should contain the original input (trimmed)
            val conversation = result as ParsedResponse.Conversation
            conversation.text shouldBe invalidInput.trim()
        }
    }
    
    test("Unrecognized action types fall back to conversation") {
        checkAll(100, arbUnrecognizedActionJson) { jsonWithUnknownAction ->
            val result = parser.parse(jsonWithUnknownAction)
            
            // Should return Conversation type since action is unrecognized
            result.shouldBeInstanceOf<ParsedResponse.Conversation>()
        }
    }
    
    test("JSON missing type field falls back to conversation") {
        checkAll(100, arbMissingTypeJson) { jsonMissingType ->
            val result = parser.parse(jsonMissingType)
            
            // Should return Conversation type since type is missing
            result.shouldBeInstanceOf<ParsedResponse.Conversation>()
        }
    }
    
    test("Valid action JSON is parsed correctly") {
        val validActionJson = """
            {
                "type": "action",
                "action": "CREATE_REMINDER",
                "parameters": {
                    "title": "Test reminder",
                    "description": "Test description"
                }
            }
        """.trimIndent()
        
        val result = parser.parse(validActionJson)
        
        result.shouldBeInstanceOf<ParsedResponse.Action>()
        val action = result as ParsedResponse.Action
        action.intent.type shouldBe ActionType.CREATE_REMINDER
        action.intent.parameters["title"] shouldBe "Test reminder"
        action.intent.parameters["description"] shouldBe "Test description"
    }
    
    test("Valid conversation JSON is parsed correctly") {
        val validConversationJson = """
            {
                "type": "conversation",
                "text": "Here are some first-aid tips for treating a sprained ankle..."
            }
        """.trimIndent()
        
        val result = parser.parse(validConversationJson)
        
        result.shouldBeInstanceOf<ParsedResponse.Conversation>()
        val conversation = result as ParsedResponse.Conversation
        conversation.text shouldBe "Here are some first-aid tips for treating a sprained ankle..."
    }
    
    test("Empty string returns empty conversation") {
        val result = parser.parse("")
        
        result.shouldBeInstanceOf<ParsedResponse.Conversation>()
        val conversation = result as ParsedResponse.Conversation
        conversation.text shouldBe ""
    }
    
    test("isActionResponse returns false for invalid JSON") {
        checkAll(100, arbInvalidJson) { invalidInput ->
            parser.isActionResponse(invalidInput) shouldBe false
        }
    }
    
    test("isActionResponse returns true for valid action JSON") {
        val validActionJson = """{"type":"action","action":"LIST_TASKS","parameters":{}}"""
        parser.isActionResponse(validActionJson) shouldBe true
    }
    
    test("JSON embedded in text is extracted and parsed") {
        val textWithJson = """
            Here is the action I'll perform:
            {"type":"action","action":"CREATE_TASK","parameters":{"title":"Buy groceries"}}
            Let me know if you need anything else.
        """.trimIndent()
        
        val result = parser.parse(textWithJson)
        
        result.shouldBeInstanceOf<ParsedResponse.Action>()
        val action = result as ParsedResponse.Action
        action.intent.type shouldBe ActionType.CREATE_TASK
    }
})
