package com.runanywhere.startup_hackathon20.domain.service

import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.startup_hackathon20.domain.model.AssistantResponse
import com.runanywhere.startup_hackathon20.domain.model.ChatMessage
import com.runanywhere.startup_hackathon20.domain.model.ActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Interface for the central assistant controller that orchestrates
 * the two-speed response system:
 * 1. Fast path (<80ms): Instant actions via pattern matching
 * 2. Slow path: Knowledge queries via LLM
 * 
 * Requirements: 2.1, 4.1
 */
interface AssistantController {
    /**
     * Processes a text query and returns an assistant response.
     * Routes to fast path for instant actions, slow path for knowledge queries.
     * 
     * @param query The user's text input
     * @return AssistantResponse with the result
     */
    suspend fun processQuery(query: String): AssistantResponse
    
    /**
     * Processes a text query with streaming response for LLM queries.
     * Instant actions return immediately without streaming.
     * 
     * @param query The user's text input
     * @return Flow of partial responses (for streaming) or single response (for instant actions)
     */
    fun processQueryStream(query: String): Flow<AssistantResponse>
    
    /**
     * Gets the current conversation context.
     * 
     * @return List of recent chat messages
     */
    fun getConversationContext(): List<ChatMessage>
    
    /**
     * Clears the conversation context.
     */
    fun clearContext()
    
    /**
     * Checks if a model is currently loaded and ready.
     * 
     * @return true if LLM is ready for queries
     */
    fun isModelLoaded(): Boolean
}


/**
 * Implementation of AssistantController with two-speed routing:
 * - Fast path (<80ms): Pattern-matched instant actions bypass LLM
 * - Slow path: Knowledge queries use on-device LLM
 * 
 * Requirements: 2.1, 4.1
 */
class AssistantControllerImpl(
    private val intentClassifier: IntentClassifier,
    private val instantActionExecutor: InstantActionExecutor,
    private val responseParser: LLMResponseParser,
    private val contextManager: ContextManager,
    private val llmActionExecutor: LLMActionExecutor? = null,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : AssistantController {
    
    companion object {
        /**
         * Default system prompt for travel/survival/education assistant.
         * Requirements: 2.4, 6.1-6.5
         */
        const val DEFAULT_SYSTEM_PROMPT = """You are Nova, an intelligent AI assistant specializing in:

1. TRAVEL & SURVIVAL:
   - First-aid guidance for injuries and emergencies
   - Disaster response protocols (earthquake, flood, fire)
   - Survival skills (finding water, shelter, navigation)
   - Travel tips and safety precautions

2. EDUCATION:
   - Explain concepts clearly at any level
   - Help with learning and understanding topics
   - Provide examples and analogies
   - Answer knowledge questions

RESPONSE RULES:
- Be concise but thorough
- For medical advice, include safety disclaimers
- Prioritize user safety in survival scenarios
- For educational content, adapt to user's level

NOTE: Simple actions like "open app", "play music", "set timer" are handled instantly by the app - you only receive knowledge questions and complex requests.

For complex action requests that need understanding, respond with JSON:
{"type":"action","action":"CREATE_REMINDER","parameters":{"title":"...","description":"...","triggerTime":"ISO8601"}}

For knowledge/conversation, respond with helpful text directly."""
    }
    
    private var modelLoaded = false
    
    override suspend fun processQuery(query: String): AssistantResponse {
        val trimmedQuery = query.trim()
        
        if (trimmedQuery.isEmpty()) {
            return AssistantResponse(
                text = "I didn't catch that. Could you please repeat?",
                shouldSpeak = true
            )
        }
        
        // Step 1: Classify the intent (fast path check)
        val classification = intentClassifier.classify(trimmedQuery)
        
        // Step 2: Route based on classification
        return if (classification.type.isInstantAction()) {
            // FAST PATH: Execute immediately (<80ms)
            // No LLM needed - direct execution
            val response = instantActionExecutor.execute(classification)
            
            // Add to context for continuity
            addToContext(trimmedQuery, response.text)
            
            response
        } else {
            // SLOW PATH: Use LLM for knowledge/conversation
            processWithLLM(trimmedQuery)
        }
    }
    
    override fun processQueryStream(query: String): Flow<AssistantResponse> = flow {
        val trimmedQuery = query.trim()
        
        if (trimmedQuery.isEmpty()) {
            emit(AssistantResponse(
                text = "I didn't catch that. Could you please repeat?",
                shouldSpeak = true
            ))
            return@flow
        }
        
        // Step 1: Classify the intent (fast path check)
        val classification = intentClassifier.classify(trimmedQuery)
        
        // Step 2: Route based on classification
        if (classification.type.isInstantAction()) {
            // FAST PATH: Execute immediately (<80ms)
            val response = instantActionExecutor.execute(classification)
            addToContext(trimmedQuery, response.text)
            emit(response)
        } else {
            // SLOW PATH: Stream LLM response
            if (!isModelLoaded()) {
                emit(AssistantResponse(
                    text = "Please load an AI model first to answer questions.",
                    actionResult = ActionResult.Failure("Model not loaded"),
                    shouldSpeak = true
                ))
                return@flow
            }
            
            // Build prompt with context
            val promptWithContext = contextManager.buildPromptWithContext(trimmedQuery)
            val fullPrompt = "$systemPrompt\n\n$promptWithContext"
            
            var fullResponse = ""
            
            try {
                RunAnywhere.generateStream(fullPrompt).collect { token ->
                    fullResponse += token
                    emit(AssistantResponse(
                        text = fullResponse,
                        shouldSpeak = false // Don't speak partial responses
                    ))
                }
                
                // Process final response for potential actions
                val finalResponse = processFinalLLMResponse(fullResponse)
                addToContext(trimmedQuery, finalResponse.text)
                
                emit(finalResponse)
            } catch (e: Exception) {
                val errorResponse = AssistantResponse(
                    text = "I encountered an error: ${e.message}",
                    actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                    shouldSpeak = true
                )
                emit(errorResponse)
            }
        }
    }
    
    override fun getConversationContext(): List<ChatMessage> {
        return contextManager.getRecentContext()
    }
    
    override fun clearContext() {
        contextManager.clear()
    }
    
    override fun isModelLoaded(): Boolean {
        return modelLoaded
    }
    
    /**
     * Sets the model loaded state. Called when a model is loaded/unloaded.
     */
    fun setModelLoaded(loaded: Boolean) {
        modelLoaded = loaded
    }

    
    /**
     * Processes a query using the LLM (slow path).
     * Requirements: 2.1, 2.2, 2.3, 2.4
     */
    private suspend fun processWithLLM(query: String): AssistantResponse {
        if (!isModelLoaded()) {
            return AssistantResponse(
                text = "Please load an AI model first to answer questions.",
                actionResult = ActionResult.Failure("Model not loaded"),
                shouldSpeak = true
            )
        }
        
        // Build prompt with conversation context
        val promptWithContext = contextManager.buildPromptWithContext(query)
        val fullPrompt = "$systemPrompt\n\n$promptWithContext"
        
        return try {
            var fullResponse = ""
            
            // Generate response with streaming (collect all tokens)
            RunAnywhere.generateStream(fullPrompt).collect { token ->
                fullResponse += token
            }
            
            // Process the final response
            val response = processFinalLLMResponse(fullResponse)
            
            // Add to context
            addToContext(query, response.text)
            
            response
        } catch (e: Exception) {
            AssistantResponse(
                text = "I encountered an error processing your request: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Processes the final LLM response, checking for action JSON.
     * Requirements: 4.2, 4.5
     */
    private suspend fun processFinalLLMResponse(response: String): AssistantResponse {
        val parsed = responseParser.parse(response)
        
        return when (parsed) {
            is ParsedResponse.Action -> {
                // Execute the parsed action
                executeLLMAction(parsed.intent)
            }
            is ParsedResponse.Conversation -> {
                AssistantResponse(
                    text = parsed.text,
                    shouldSpeak = true
                )
            }
        }
    }
    
    /**
     * Executes an action parsed from LLM response.
     * Delegates to LLMActionExecutor for complex actions.
     * Requirements: 4.2, 4.3, 4.4
     */
    private suspend fun executeLLMAction(intent: com.runanywhere.startup_hackathon20.domain.model.ActionIntent): AssistantResponse {
        // Use LLMActionExecutor if available
        return if (llmActionExecutor != null && llmActionExecutor.canExecute(intent.type)) {
            llmActionExecutor.execute(intent)
        } else {
            // Fallback response if no executor available
            AssistantResponse(
                text = "I'll help you with that: ${intent.type}",
                actionResult = ActionResult.Success("Action recognized: ${intent.type}"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Adds a user query and assistant response to the conversation context.
     */
    private fun addToContext(userQuery: String, assistantResponse: String) {
        contextManager.addMessage(ChatMessage(
            text = userQuery,
            isUser = true
        ))
        contextManager.addMessage(ChatMessage(
            text = assistantResponse,
            isUser = false
        ))
    }
}
