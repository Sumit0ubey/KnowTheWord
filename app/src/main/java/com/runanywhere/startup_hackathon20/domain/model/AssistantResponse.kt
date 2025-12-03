package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Result of an action execution.
 */
sealed class ActionResult {
    data class Success(
        val message: String,
        val data: Any? = null
    ) : ActionResult()
    
    data class Failure(
        val error: String
    ) : ActionResult()
}

/**
 * Represents a response from the assistant.
 */
data class AssistantResponse(
    @SerializedName("text")
    val text: String,
    
    val actionResult: ActionResult? = null,
    
    @SerializedName("shouldSpeak")
    val shouldSpeak: Boolean = true
)
