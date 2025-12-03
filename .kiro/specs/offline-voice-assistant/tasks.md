# Implementation Plan

- [-] 1. Set up project structure and core data models








  - [x] 1.1 Create package structure for domain, data, and UI layers

    - Create directories: `domain/`, `data/`, `data/repository/`, `data/local/`, `domain/model/`, `domain/service/`
    - _Requirements: 8.1, 8.4_
  - [x] 1.2 Implement core data models (ChatMessage, Reminder, Task, AssistantResponse)


    - Create data classes with JSON serialization annotations
    - Include MessageMetadata, TaskPriority enum
    - _Requirements: 8.4, 5.5_
  - [x] 1.3 Write property test for ChatMessage serialization round-trip


    - **Property 1: Conversation Serialization Round-Trip**
    - **Validates: Requirements 8.4, 8.5**

  - [x] 1.4 Implement ActionIntent and related enums (IntentType, ActionType)

    - Create ClassificationResult data class
    - Define instant action vs knowledge query types
    - _Requirements: 4.1, 4.2_
  - [x] 1.5 Write property test for ActionIntent JSON round-trip


    - **Property 2: Action Intent JSON Round-Trip**
    - **Validates: Requirements 4.2**

- [x] 2. Implement local storage repositories



  - [x] 2.1 Create MessageRepository with SharedPreferences/JSON storage


    - Implement saveMessage, getAllMessages, clearAll
    - Use Gson for JSON serialization
    - _Requirements: 8.1, 8.2, 8.3_
  - [x] 2.2 Write property test for message addition increases count


    - **Property 6: Message Addition Increases Count**
    - **Validates: Requirements 8.1**
  - [x] 2.3 Write property test for clear history empties storage

    - **Property 9: Clear History Empties Storage**
    - **Validates: Requirements 8.3**
  - [x] 2.4 Create ReminderRepository with local storage

    - Implement create, getAll, delete, getById
    - _Requirements: 5.2, 5.3, 5.5_
  - [x] 2.5 Write property test for reminder persistence round-trip

    - **Property 4: Reminder Persistence Round-Trip**
    - **Validates: Requirements 5.5**

  - [x] 2.6 Write property test for reminder deletion decreases count
    - **Property 7: Reminder Deletion Decreases Count**
    - **Validates: Requirements 5.3**

  - [x] 2.7 Write property test for list operations return all items
    - **Property 12: List Operations Return All Items**
    - **Validates: Requirements 4.6, 5.2**

  - [x] 2.8 Create TaskRepository with local storage
    - Implement create, getAll, delete, update
    - _Requirements: 4.4, 5.5_

  - [x] 2.9 Write property test for task persistence round-trip

    - **Property 5: Task Persistence Round-Trip**
    - **Validates: Requirements 5.5**

- [x] 3. Checkpoint - Ensure all tests pass







  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement IntentClassifier for instant actions (PS3 - <80ms)





  - [x] 4.1 Create InstantActionPatterns object with regex patterns


    - Patterns for: OPEN_APP, PLAY_MUSIC, SET_TIMER, CREATE_REMINDER, TOGGLE_FLASHLIGHT, TAKE_PHOTO
    - _Requirements: 4.1 (PS3 zero-latency)_
  - [x] 4.2 Implement IntentClassifier with pattern matching


    - Classify input as instant action or knowledge query
    - Extract parameters from matched patterns
    - _Requirements: 4.1, 2.1_
  - [x] 4.3 Write property test for instant action classification consistency



    - **Property 13: Instant Action Classification Consistency**
    - **Validates: Requirements 4.1 (PS3 zero-latency)**
  - [x] 4.4 Write property test for knowledge query classification

    - **Property 14: Knowledge Query Classification**
    - **Validates: Requirements 2.1 (PS1 offline intelligence)**

- [x] 5. Implement InstantActionExecutor





  - [x] 5.1 Create InstantActionExecutor for device actions


    - Implement openApp, playMusic, setTimer, toggleFlashlight, takePhoto
    - Use Android intents for app launching
    - _Requirements: 4.3 (PS3 instant execution)_

  - [x] 5.2 Implement reminder creation via InstantActionExecutor


    - Create reminder from extracted parameters
    - Schedule local notification
    - _Requirements: 4.3, 5.1_

- [x] 6. Implement ContextManager





  - [x] 6.1 Create ContextManager for conversation history


    - Implement addMessage, getRecentContext, buildPromptWithContext, clear
    - Limit context to 10 most recent messages
    - _Requirements: 2.3_

  - [x] 6.2 Write property test for context window bounds


    - **Property 3: Context Window Bounds**
    - **Validates: Requirements 2.3**

- [x] 7. Implement LLMResponseParser





  - [x] 7.1 Create LLMResponseParser for JSON/text parsing


    - Parse JSON action responses
    - Fall back to conversation for invalid JSON
    - _Requirements: 4.2, 4.5_

  - [x] 7.2 Write property test for invalid JSON falls back to conversation


    - **Property 8: Invalid JSON Falls Back to Conversation**
    - **Validates: Requirements 4.5**

<!-- - [ ] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise. -->

- [x] 9. Implement AssistantController with two-speed routing



  - [x] 9.1 Create AssistantController interface and implementation


    - Route instant actions to InstantActionExecutor (<80ms path)
    - Route knowledge queries to LLM (slow path)
    - _Requirements: 2.1, 4.1_


  - [x] 9.2 Integrate with RunAnywhere SDK for LLM generation
    - Use streaming generation for real-time feedback
    - Apply system prompt for travel/survival/education

    - _Requirements: 2.2, 2.4_
  - [x] 9.3 Implement LLMActionExecutor for complex actions

    - Execute parsed ActionIntent from LLM responses
    - Delegate to repositories
    - _Requirements: 4.2, 4.3, 4.4_

- [x] 10. Implement TTSService




  - [x] 10.1 Create TTSService wrapper around Android TextToSpeech

    - Implement speak, stop, isEnabled, setEnabled
    - Emit TTSState flow for UI updates
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 10.2 Write property test for TTS state consistency


    - **Property 11: TTS State Consistency**
    - **Validates: Requirements 3.1, 3.4**

- [x] 11. Implement VoiceInputHandler





  - [x] 11.1 Create VoiceInputHandler for audio recording
    - Implement startRecording, stopRecording with AudioRecord
    - Emit RecordingState flow for UI updates
    - _Requirements: 1.1, 1.2_
  - [x] 11.2 Integrate Whisper STT for transcription

    - Use RunAnywhere SDK Whisper model (when available) or Android SpeechRecognizer fallback
    - Return TranscriptionResult with text and confidence
    - _Requirements: 1.3, 1.4_
  - [x] 11.3 Write property test for recording state transitions


    - **Property 10: Recording State Transitions**
    - **Validates: Requirements 1.1, 1.2**

<!-- - [ ] 12. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise. -->

- [x] 13. Update ChatViewModel with new architecture




  - [x] 13.1 Refactor ChatViewModel to use AssistantController

    - Inject AssistantController, MessageRepository
    - Handle both text and voice input
    - _Requirements: 2.1, 8.1_
  - [x] 13.2 Add conversation persistence to ChatViewModel

    - Save messages on send/receive
    - Restore messages on init

    - _Requirements: 8.1, 8.2_
  - [x] 13.3 Integrate TTS for assistant responses
    - Auto-speak responses when TTS enabled
    - Provide stop button functionality
    - _Requirements: 3.1, 3.4_

- [x] 14. Create VoiceViewModel




  - [x] 14.1 Implement VoiceViewModel for voice input state

    - Manage recording state, transcription results
    - Coordinate with VoiceInputHandler
    - _Requirements: 1.1, 1.2, 1.4_

- [x] 15. Update UI components



  - [x] 15.1 Update MainActivity to integrate voice functionality


    - Add microphone button click handler
    - Show recording indicator
    - Display transcription results
    - _Requirements: 1.1, 1.4, 9.1_

  - [x] 15.2 Create MessagesAdapter and ModelsAdapter if missing
    - RecyclerView adapters for chat messages and model list
    - _Requirements: 9.1, 7.1_

  - [x] 15.3 Add TTS toggle and stop button to UI
    - Settings toggle for auto-TTS
    - Stop button during playback
    - _Requirements: 3.3, 3.4_

  - [x] 15.4 Add visual indicators for assistant states
    - Recording indicator (pulsing mic)
    - Processing indicator (loading)
    - Speaking indicator (sound waves)
    - _Requirements: 1.1, 3.2, 9.2_

- [x] 16. Implement notification system for reminders




  - [x] 16.1 Create NotificationHelper for local notifications

    - Schedule notifications using AlarmManager
    - Display reminder content in notification
    - _Requirements: 5.4_
  - [x] 16.2 Add notification permission handling


    - Request POST_NOTIFICATIONS permission on Android 13+
    - _Requirements: 5.4_

- [x] 17. Add Android permissions



  - [x] 17.1 Update AndroidManifest.xml with required permissions

    - RECORD_AUDIO for voice input
    - POST_NOTIFICATIONS for reminders
    - SCHEDULE_EXACT_ALARM for timed reminders
    - _Requirements: 1.1, 5.4_

  - [x] 17.2 Implement runtime permission requests
    - Request microphone permission before recording
    - Handle permission denial gracefully
    - _Requirements: 1.5_

- [x] 18. Final integration and testing




  - [x] 18.1 Wire all components together in MyApplication

    - Initialize repositories, services, controllers
    - Ensure proper dependency injection
    - _Requirements: All_

  - [x] 18.2 Test instant action flow end-to-end
    - Verify <80ms response for pattern-matched actions
    - Test: "open camera", "set timer 5 minutes", "turn on flashlight"
    - _Requirements: 4.1 (PS3)_

  - [x] 18.3 Test knowledge query flow end-to-end
    - Verify LLM responses for travel/survival/education queries
    - Test: "how to treat a snake bite", "explain photosynthesis"
    - _Requirements: 2.1, 2.4 (PS1)_

  - [x] 18.4 Test voice input flow end-to-end
    - Verify recording → transcription → processing → response → TTS
    - _Requirements: 1.1-1.4, 3.1-3.4_

- [x] 19. Final Checkpoint - Ensure all tests pass







  - Ensure all tests pass, ask the user if questions arise.
