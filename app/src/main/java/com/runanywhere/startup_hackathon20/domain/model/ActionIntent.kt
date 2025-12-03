package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Types of intents that can be classified from user input.
 * Divided into instant actions (no LLM needed) and knowledge queries (LLM needed).
 */
enum class IntentType {
    // Instant Actions (<80ms) - No LLM needed
    OPEN_APP,
    PLAY_MUSIC,
    SET_TIMER,
    SET_ALARM,
    CREATE_REMINDER,
    TAKE_PHOTO,
    CALL_CONTACT,
    SEND_MESSAGE,
    TOGGLE_FLASHLIGHT,
    
    // Knowledge Queries (LLM needed)
    KNOWLEDGE_QUERY,
    CONVERSATION,
    
    UNKNOWN;
    
    /**
     * Returns true if this intent type can be executed instantly without LLM.
     */
    fun isInstantAction(): Boolean = when (this) {
        OPEN_APP, PLAY_MUSIC, SET_TIMER, SET_ALARM, CREATE_REMINDER,
        TAKE_PHOTO, CALL_CONTACT, SEND_MESSAGE, TOGGLE_FLASHLIGHT -> true
        else -> false
    }
}

/**
 * Types of actions that can be executed by the LLM action executor.
 */
enum class ActionType {
    CREATE_REMINDER,
    DELETE_REMINDER,
    LIST_REMINDERS,
    CREATE_TASK,
    DELETE_TASK,
    LIST_TASKS,
    UNKNOWN
}

/**
 * Result of intent classification.
 */
data class ClassificationResult(
    @SerializedName("type")
    val type: IntentType,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("extractedParams")
    val extractedParams: Map<String, String> = emptyMap()
)

/**
 * Represents a parsed action intent from LLM output.
 * Supports JSON serialization for persistence and parsing.
 */
data class ActionIntent(
    @SerializedName("type")
    val type: ActionType,
    
    @SerializedName("parameters")
    val parameters: Map<String, Any> = emptyMap()
)
