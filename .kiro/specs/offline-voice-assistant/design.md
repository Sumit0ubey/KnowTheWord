# Design Document: Translexa - Offline Voice AI Assistant

## Overview

Translexa is a privacy-first offline voice AI assistant for Android that combines **PS1 (Offline Intelligence)** and **PS3 (Zero-Latency Voice Interface)** from The Claude Challenge. The app leverages the RunAnywhere SDK for on-device LLM inference, Whisper STT, and TTS capabilities.

### Core Philosophy: Two-Speed Response System

The key innovation is a **dual-speed architecture** that distinguishes between:

1. **Instant Actions (<80ms)** - Tasks like "open camera", "play music", "set timer" are detected via lightweight pattern matching and executed immediately WITHOUT waiting for LLM. This achieves sub-80ms latency, beating cloud assistants like Alexa/Siri (200ms+).

2. **Knowledge Queries (LLM-powered)** - Questions like "how do I treat a snake bite?" or "explain photosynthesis" are routed to the on-device LLM for intelligent, contextual responses.

### Specialization: Travel & Survival + Education

The assistant provides offline expertise in:
- **Travel & Survival**: First-aid, disaster response, navigation, survival skills
- **Education**: Concept explanations, tutoring, learning assistance

### Input Modes

Both **voice** and **text chat** are fully supported, giving users flexibility based on their situation.

The architecture follows a clean MVVM pattern with clear separation between UI, business logic, and data layers. All AI processing occurs on-device using optimized GGUF models, ensuring complete privacy.

## Architecture

```mermaid
graph TB
    subgraph UI Layer
        MA[MainActivity]
        CV[ChatView]
        VB[VoiceButton]
        MS[ModelSelector]
    end
    
    subgraph ViewModel Layer
        CVM[ChatViewModel]
        VVM[VoiceViewModel]
        MVM[ModelViewModel]
    end
    
    subgraph Domain Layer
        AC[AssistantController]
        VIH[VoiceInputHandler]
        LRP[LLMResponseParser]
        AE[ActionExecutor]
        CM[ContextManager]
    end
    
    subgraph Data Layer
        MR[MessageRepository]
        RR[ReminderRepository]
        TR[TaskRepository]
        SP[SharedPreferences]
        LS[LocalStorage]
    end
    
    subgraph RunAnywhere SDK
        RA[RunAnywhere Core]
        LLM[LlamaCpp Provider]
        STT[Whisper STT]
        TTS[Android TTS]
    end
    
    MA --> CVM
    MA --> VVM
    MA --> MVM
    
    CVM --> AC
    VVM --> VIH
    
    AC --> LRP
    AC --> AE
    AC --> CM
    
    AE --> RR
    AE --> TR
    
    AC --> MR
    MR --> LS
    RR --> LS
    TR --> LS
    
    VIH --> STT
    AC --> LLM
    AC --> TTS
    
    LLM --> RA
    STT --> RA


## Components and Interfaces

### 1. IntentClassifier (Fast Path - <80ms)

Lightweight pattern-based classifier that instantly detects action intents WITHOUT using LLM. This is the key to achieving sub-80ms response for tasks.

```kotlin
interface IntentClassifier {
    fun classify(input: String): ClassificationResult
    fun isInstantAction(input: String): Boolean
}

data class ClassificationResult(
    val type: IntentType,
    val confidence: Float,
    val extractedParams: Map<String, String>
)

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
    
    UNKNOWN
}
```

### 2. AssistantController

Central orchestrator with **two-speed routing**: instant actions bypass LLM, knowledge queries use LLM.

```kotlin
interface AssistantController {
    suspend fun processQuery(query: String): AssistantResponse
    suspend fun processVoiceInput(audioData: ByteArray): AssistantResponse
    fun getConversationContext(): List<ChatMessage>
    fun clearContext()
}

class AssistantControllerImpl(
    private val intentClassifier: IntentClassifier,  // Fast path
    private val llmService: LLMService,              // Slow path
    private val instantActionExecutor: InstantActionExecutor,
    private val responseParser: LLMResponseParser,
    private val contextManager: ContextManager,
    private val ttsService: TTSService
) : AssistantController {
    
    override suspend fun processQuery(query: String): AssistantResponse {
        val classification = intentClassifier.classify(query)
        
        return if (classification.type.isInstantAction()) {
            // FAST PATH: Execute immediately (<80ms)
            instantActionExecutor.execute(classification)
        } else {
            // SLOW PATH: Use LLM for knowledge/conversation
            processWithLLM(query)
        }
    }
}
```

### 3. InstantActionExecutor (Sub-80ms Execution)

Executes device actions immediately without LLM involvement.

```kotlin
interface InstantActionExecutor {
    suspend fun execute(classification: ClassificationResult): AssistantResponse
    fun canExecute(intentType: IntentType): Boolean
}

class InstantActionExecutorImpl(
    private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val notificationManager: NotificationManager
) : InstantActionExecutor {
    
    override suspend fun execute(classification: ClassificationResult): AssistantResponse {
        return when (classification.type) {
            IntentType.OPEN_APP -> openApp(classification.extractedParams["appName"])
            IntentType.PLAY_MUSIC -> playMusic(classification.extractedParams["query"])
            IntentType.SET_TIMER -> setTimer(classification.extractedParams["duration"])
            IntentType.CREATE_REMINDER -> createReminder(classification.extractedParams)
            IntentType.TOGGLE_FLASHLIGHT -> toggleFlashlight()
            // ... other instant actions
            else -> AssistantResponse("Action not supported", shouldSpeak = true)
        }
    }
}
```

### 4. VoiceInputHandler

Manages audio recording, VAD detection, and Whisper transcription.

```kotlin
interface VoiceInputHandler {
    fun startRecording(): Flow<RecordingState>
    fun stopRecording(): ByteArray
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    fun isRecording(): Boolean
}

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Processing : RecordingState()
    data class Error(val message: String) : RecordingState()
}

data class TranscriptionResult(
    val text: String,
    val confidence: Float,
    val durationMs: Long
)
```

### 5. LLMResponseParser

Parses LLM output to extract structured actions or conversational text.

```kotlin
interface LLMResponseParser {
    fun parse(response: String): ParsedResponse
    fun isActionResponse(response: String): Boolean
}

sealed class ParsedResponse {
    data class Action(val intent: ActionIntent) : ParsedResponse()
    data class Conversation(val text: String) : ParsedResponse()
}

data class ActionIntent(
    val type: ActionType,
    val parameters: Map<String, Any>
)

enum class ActionType {
    CREATE_REMINDER,
    DELETE_REMINDER,
    LIST_REMINDERS,
    CREATE_TASK,
    DELETE_TASK,
    LIST_TASKS,
    UNKNOWN
}
```

### 6. LLMActionExecutor

Executes parsed action intents by delegating to appropriate repositories.

```kotlin
interface ActionExecutor {
    suspend fun execute(intent: ActionIntent): ActionResult
}

sealed class ActionResult {
    data class Success(val message: String, val data: Any? = null) : ActionResult()
    data class Failure(val error: String) : ActionResult()
}
```

### 7. ContextManager

Manages conversation history and context window for LLM.

```kotlin
interface ContextManager {
    fun addMessage(message: ChatMessage)
    fun getRecentContext(maxMessages: Int = 10): List<ChatMessage>
    fun buildPromptWithContext(userQuery: String): String
    fun clear()
}
```

### 8. TTSService

Wrapper around Android TTS for speaking assistant responses.

```kotlin
interface TTSService {
    fun speak(text: String): Flow<TTSState>
    fun stop()
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

sealed class TTSState {
    object Idle : TTSState()
    object Speaking : TTSState()
    object Completed : TTSState()
    data class Error(val message: String) : TTSState()
}
```

### 9. Repositories

```kotlin
interface MessageRepository {
    suspend fun saveMessage(message: ChatMessage)
    suspend fun getAllMessages(): List<ChatMessage>
    suspend fun clearAll()
}

interface ReminderRepository {
    suspend fun create(reminder: Reminder): Long
    suspend fun getAll(): List<Reminder>
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): Reminder?
}

interface TaskRepository {
    suspend fun create(task: Task): Long
    suspend fun getAll(): List<Task>
    suspend fun delete(id: Long)
    suspend fun update(task: Task)
}
```

## Data Models

```kotlin
data class ChatMessage(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: MessageMetadata? = null
)

data class MessageMetadata(
    val actionIntent: ActionIntent? = null,
    val transcriptionConfidence: Float? = null
)

data class Reminder(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val triggerTimeMs: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TaskPriority { LOW, MEDIUM, HIGH }

data class AssistantResponse(
    val text: String,
    val actionResult: ActionResult? = null,
    val shouldSpeak: Boolean = true
)
```

### Instant Action Patterns (Pattern Matching - No LLM)

These patterns enable <80ms response by bypassing LLM entirely:

```kotlin
object InstantActionPatterns {
    val OPEN_APP = listOf(
        Regex("(?i)open\\s+(.+)"),
        Regex("(?i)launch\\s+(.+)"),
        Regex("(?i)start\\s+(.+)")
    )
    
    val PLAY_MUSIC = listOf(
        Regex("(?i)play\\s+(.+)"),
        Regex("(?i)play\\s+music"),
        Regex("(?i)play\\s+song\\s+(.+)")
    )
    
    val SET_TIMER = listOf(
        Regex("(?i)set\\s+(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(minutes?|seconds?|hours?)"),
        Regex("(?i)timer\\s+(\\d+)\\s*(minutes?|seconds?|hours?)")
    )
    
    val CREATE_REMINDER = listOf(
        Regex("(?i)remind\\s+me\\s+(?:to\\s+)?(.+?)\\s+(?:at|in|on)\\s+(.+)"),
        Regex("(?i)set\\s+(?:a\\s+)?reminder\\s+(?:for\\s+)?(.+)")
    )
    
    val TOGGLE_FLASHLIGHT = listOf(
        Regex("(?i)(?:turn\\s+)?(?:on|off)\\s+(?:the\\s+)?(?:flashlight|torch)"),
        Regex("(?i)flashlight\\s+(?:on|off)")
    )
    
    val TAKE_PHOTO = listOf(
        Regex("(?i)take\\s+(?:a\\s+)?(?:photo|picture|selfie)"),
        Regex("(?i)open\\s+camera")
    )
}
```

### JSON Schema for LLM Structured Output

For complex actions that need LLM understanding:

```json
{
  "type": "action",
  "action": "CREATE_REMINDER",
  "parameters": {
    "title": "Pack hiking gear",
    "description": "Don't forget water bottles",
    "triggerTime": "2025-12-02T08:00:00"
  }
}
```

For conversational/knowledge responses:
```json
{
  "type": "conversation",
  "text": "Here are some first-aid tips for treating a sprained ankle..."
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Conversation Serialization Round-Trip

*For any* list of ChatMessage objects, serializing to JSON and then deserializing should produce an equivalent list of messages with identical text, isUser, and timestamp values.

**Validates: Requirements 8.4, 8.5**

### Property 2: Action Intent JSON Round-Trip

*For any* valid ActionIntent object, serializing to JSON and then parsing should produce an equivalent ActionIntent with the same type and parameters.

**Validates: Requirements 4.2**

### Property 3: Context Window Bounds

*For any* conversation history of N messages where N > 10, calling getRecentContext(10) should return exactly 10 messages, and they should be the 10 most recent messages in chronological order.

**Validates: Requirements 2.3**

### Property 4: Reminder Persistence Round-Trip

*For any* Reminder object, saving to storage and then retrieving by ID should return an equivalent Reminder with identical title, description, and triggerTimeMs.

**Validates: Requirements 5.5**

### Property 5: Task Persistence Round-Trip

*For any* Task object, saving to storage and then retrieving should return an equivalent Task with identical title, description, and priority.

**Validates: Requirements 5.5**

### Property 6: Message Addition Increases Count

*For any* MessageRepository with N messages, adding a new message should result in the repository containing N+1 messages, and the new message should be retrievable.

**Validates: Requirements 8.1**

### Property 7: Reminder Deletion Decreases Count

*For any* ReminderRepository with N reminders where N > 0, deleting an existing reminder should result in N-1 reminders, and the deleted reminder should not be retrievable.

**Validates: Requirements 5.3**

### Property 8: Invalid JSON Falls Back to Conversation

*For any* string that is not valid JSON or does not contain a recognized action type, the LLMResponseParser should return a Conversation response containing the original text.

**Validates: Requirements 4.5**

### Property 9: Clear History Empties Storage

*For any* MessageRepository with N > 0 messages, calling clearAll() should result in getAllMessages() returning an empty list.

**Validates: Requirements 8.3**

### Property 10: Recording State Transitions

*For any* VoiceInputHandler in Idle state, calling startRecording() should transition to Recording state, and calling stopRecording() should transition back to Idle or Processing state.

**Validates: Requirements 1.1, 1.2**

### Property 11: TTS State Consistency

*For any* TTSService, if isEnabled() returns true and speak() is called, the state should transition to Speaking and eventually to Completed or Error.

**Validates: Requirements 3.1, 3.4**

### Property 12: List Operations Return All Items

*For any* set of N reminders created via the ReminderRepository, calling getAll() should return exactly N reminders.

**Validates: Requirements 4.6, 5.2**

### Property 13: Instant Action Classification Consistency

*For any* input string that matches an instant action pattern (e.g., "open camera", "play music"), the IntentClassifier should return an IntentType that is marked as instant action (isInstantAction() returns true).

**Validates: Requirements 4.1 (PS3 zero-latency)**

### Property 14: Knowledge Query Classification

*For any* input string that is a question (contains "what", "how", "why", "explain", etc.) and does NOT match instant action patterns, the IntentClassifier should return IntentType.KNOWLEDGE_QUERY or IntentType.CONVERSATION.

**Validates: Requirements 2.1 (PS1 offline intelligence)**

## Error Handling

### Voice Input Errors

| Error | Handling | User Feedback |
|-------|----------|---------------|
| Microphone permission denied | Show permission rationale dialog | "Microphone access needed for voice input" |
| Audio recording failure | Retry once, then show error | "Could not start recording. Please try again." |
| Transcription failure | Fall back to text input | "Voice recognition failed. Please type your message." |
| VAD timeout (no speech detected) | Stop recording, prompt user | "No speech detected. Tap to try again." |

### LLM Errors

| Error | Handling | User Feedback |
|-------|----------|---------------|
| Model not loaded | Prompt to load model | "Please load an AI model first" |
| Out of memory | Suggest smaller model | "Memory low. Try a smaller model." |
| Generation timeout | Cancel and retry | "Response taking too long. Please try again." |
| Invalid response | Treat as conversation | Display raw response text |

### Storage Errors

| Error | Handling | User Feedback |
|-------|----------|---------------|
| Storage full | Clear old data, warn user | "Storage full. Some old messages cleared." |
| Database corruption | Reset database | "Data reset required. Previous history lost." |
| Read/write failure | Retry with exponential backoff | Silent retry, log error |

### Network Errors (Model Download)

| Error | Handling | User Feedback |
|-------|----------|---------------|
| No internet | Block download, show message | "Internet required to download models" |
| Download interrupted | Resume from checkpoint | "Download paused. Will resume when connected." |
| Server unavailable | Retry with backoff | "Download server unavailable. Retrying..." |

## Testing Strategy

### Property-Based Testing Framework

The project will use **Kotest** with the **kotest-property** module for property-based testing in Kotlin. This provides:
- Arbitrary generators for custom data types
- Shrinking for minimal failing examples
- Integration with JUnit 5

```kotlin
// build.gradle.kts
testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
testImplementation("io.kotest:kotest-assertions-core:5.8.0")
testImplementation("io.kotest:kotest-property:5.8.0")
```

### Test Configuration

- Each property-based test MUST run a minimum of 100 iterations
- Each property-based test MUST be tagged with a comment referencing the correctness property: `**Feature: offline-voice-assistant, Property {number}: {property_text}**`

### Unit Tests

Unit tests will cover:
- Individual component behavior
- Edge cases (empty inputs, boundary values)
- Error conditions and exception handling
- Mock-based integration points

### Test Structure

```
app/src/test/java/com/runanywhere/startup_hackathon20/
├── domain/
│   ├── AssistantControllerTest.kt
│   ├── LLMResponseParserTest.kt
│   ├── ActionExecutorTest.kt
│   └── ContextManagerTest.kt
├── data/
│   ├── MessageRepositoryTest.kt
│   ├── ReminderRepositoryTest.kt
│   └── TaskRepositoryTest.kt
├── property/
│   ├── SerializationPropertyTest.kt
│   ├── RepositoryPropertyTest.kt
│   └── ParserPropertyTest.kt
└── viewmodel/
    ├── ChatViewModelTest.kt
    └── VoiceViewModelTest.kt
```

### Sample Commands for Testing

1. "Remind me to pack my bags tomorrow at 8 AM"
2. "What should I do if I get lost in the wilderness?"
3. "Show my reminders"
4. "How do I treat a snake bite?"
5. "Create a task to check weather forecast"
6. "What are the signs of dehydration?"
7. "Delete reminder 1"
8. "How do I start a fire without matches?"
9. "List all my tasks"
10. "What should I do during an earthquake?"

## System Prompts

### Travel, Survival & Education Assistant Prompt

```
You are Translexa, an offline AI assistant specializing in:

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

For knowledge/conversation, respond with helpful text directly.
```

### Response Time Targets

| Request Type | Target Latency | Processing Path |
|--------------|----------------|-----------------|
| Instant Actions (open app, play music, timer) | <80ms | Pattern matching → Direct execution |
| Simple Reminders/Tasks | <200ms | Pattern matching → Repository |
| Knowledge Queries | 1-5s | LLM inference → Streaming response |
| Complex Actions | 1-3s | LLM parsing → Action execution |
