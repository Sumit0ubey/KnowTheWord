package com.runanywhere.startup_hackathon20.domain.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.runanywhere.startup_hackathon20.domain.model.ActionIntent
import com.runanywhere.startup_hackathon20.domain.model.ActionType

/**
 * Represents a parsed response from the LLM.
 */
sealed class ParsedResponse {
    /**
     * An action response containing an intent to execute.
     */
    data class Action(val intent: ActionIntent) : ParsedResponse()
    
    /**
     * A conversational response containing text to display.
     */
    data class Conversation(val text: String) : ParsedResponse()
}

/**
 * Internal data class for parsing JSON responses from LLM.
 */
internal data class LLMJsonResponse(
    @SerializedName("type")
    val type: String? = null,
    
    @SerializedName("action")
    val action: String? = null,
    
    @SerializedName("parameters")
    val parameters: Map<String, Any>? = null,
    
    @SerializedName("text")
    val text: String? = null
)

/**
 * Interface for parsing LLM responses.
 */
interface LLMResponseParser {
    /**
     * Parses an LLM response string into a structured ParsedResponse.
     * 
     * @param response The raw response string from the LLM
     * @return ParsedResponse.Action if valid JSON action, ParsedResponse.Conversation otherwise
     */
    fun parse(response: String): ParsedResponse
    
    /**
     * Checks if a response string appears to be an action response.
     * 
     * @param response The raw response string from the LLM
     * @return true if the response is a valid JSON action response
     */
    fun isActionResponse(response: String): Boolean
}


/**
 * Implementation of LLMResponseParser that parses JSON action responses
 * and falls back to conversation for invalid JSON.
 * 
 * Expected JSON format for actions:
 * ```json
 * {
 *   "type": "action",
 *   "action": "CREATE_REMINDER",
 *   "parameters": {
 *     "title": "Pack hiking gear",
 *     "description": "Don't forget water bottles",
 *     "triggerTime": "2025-12-02T08:00:00"
 *   }
 * }
 * ```
 * 
 * Expected JSON format for conversation:
 * ```json
 * {
 *   "type": "conversation",
 *   "text": "Here are some first-aid tips..."
 * }
 * ```
 * 
 * Requirements: 4.2, 4.5
 */
class LLMResponseParserImpl : LLMResponseParser {
    
    private val gson: Gson = GsonBuilder().create()
    
    override fun parse(response: String): ParsedResponse {
        val trimmedResponse = response.trim()
        
        // Empty response falls back to conversation
        if (trimmedResponse.isEmpty()) {
            return ParsedResponse.Conversation("")
        }
        
        // Try to extract JSON from the response (may be embedded in text)
        val jsonString = extractJson(trimmedResponse)
        
        if (jsonString == null) {
            // No JSON found, treat as conversation
            return ParsedResponse.Conversation(trimmedResponse)
        }
        
        return try {
            val jsonResponse = gson.fromJson(jsonString, LLMJsonResponse::class.java)
            parseJsonResponse(jsonResponse, trimmedResponse)
        } catch (e: JsonSyntaxException) {
            // Invalid JSON, fall back to conversation
            ParsedResponse.Conversation(trimmedResponse)
        } catch (e: Exception) {
            // Any other parsing error, fall back to conversation
            ParsedResponse.Conversation(trimmedResponse)
        }
    }
    
    override fun isActionResponse(response: String): Boolean {
        val parsed = parse(response)
        return parsed is ParsedResponse.Action
    }
    
    /**
     * Extracts JSON object from a string that may contain surrounding text.
     * Looks for content between { and } brackets.
     */
    private fun extractJson(text: String): String? {
        val startIndex = text.indexOf('{')
        if (startIndex == -1) return null
        
        var braceCount = 0
        var endIndex = -1
        
        for (i in startIndex until text.length) {
            when (text[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        endIndex = i
                        break
                    }
                }
            }
        }
        
        return if (endIndex != -1) {
            text.substring(startIndex, endIndex + 1)
        } else {
            null
        }
    }
    
    /**
     * Parses a JSON response object into a ParsedResponse.
     */
    private fun parseJsonResponse(jsonResponse: LLMJsonResponse, originalText: String): ParsedResponse {
        return when (jsonResponse.type?.lowercase()) {
            "action" -> parseActionResponse(jsonResponse, originalText)
            "conversation" -> {
                val text = jsonResponse.text ?: originalText
                ParsedResponse.Conversation(text)
            }
            else -> {
                // Unknown type or missing type, fall back to conversation
                ParsedResponse.Conversation(originalText)
            }
        }
    }
    
    /**
     * Parses an action response from JSON.
     */
    private fun parseActionResponse(jsonResponse: LLMJsonResponse, originalText: String): ParsedResponse {
        val actionString = jsonResponse.action ?: return ParsedResponse.Conversation(originalText)
        
        val actionType = try {
            ActionType.valueOf(actionString.uppercase())
        } catch (e: IllegalArgumentException) {
            // Unrecognized action type, fall back to conversation
            return ParsedResponse.Conversation(originalText)
        }
        
        val parameters = jsonResponse.parameters ?: emptyMap()
        
        return ParsedResponse.Action(
            ActionIntent(
                type = actionType,
                parameters = parameters
            )
        )
    }
}
