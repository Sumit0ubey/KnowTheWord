# Requirements Document

## Introduction

Translexa is a privacy-first offline voice AI assistant for Android that combines the RunAnywhere SDK's on-device LLM capabilities with Whisper-based speech-to-text and text-to-speech to create a zero-latency, fully offline intelligent assistant. The app specializes as a **Offline educational tutor** and also **Travel or survival assistant** - providing critical guidance for travelers, hikers, and emergency responders in areas with no internet connectivity. The assistant handles voice commands, answers queries using local reasoning, executes in-app actions via structured JSON output, and manages reminders/tasks - all without any cloud dependency.

## Glossary

- **Translexa**: The Android application being developed - an offline voice AI assistant
- **RunAnywhere SDK**: On-device AI SDK providing LLM inference, Whisper STT, VAD, and TTS capabilities
- **LLM**: Large Language Model - AI model for text generation running locally via llama.cpp
- **STT**: Speech-to-Text - converts voice input to text using Whisper model
- **TTS**: Text-to-Speech - converts assistant responses to spoken audio
- **VAD**: Voice Activity Detection - detects when user starts/stops speaking
- **GGUF**: Model file format used by llama.cpp for efficient on-device inference
- **Structured Output**: JSON-formatted responses from LLM for executing in-app actions
- **Wake Command**: Voice phrase or button tap that activates the assistant
- **Context Memory**: Storage of recent conversation history for contextual responses
- **Action Intent**: Parsed user intention mapped to executable app functionality
- **Offline Knowledge Pack**: Pre-loaded domain-specific information for survival/travel guidance

## Requirements

### Requirement 1: Voice Input Processing

**User Story:** As a user, I want to speak to the assistant using my voice, so that I can interact hands-free in situations where typing is impractical.

#### Acceptance Criteria

1. WHEN the user taps the microphone button, THE Translexa app SHALL begin recording audio and display a visual recording indicator
2. WHEN the user stops speaking (detected via VAD) or taps the stop button, THE Translexa app SHALL stop recording and process the audio
3. WHEN audio recording completes, THE Translexa app SHALL transcribe the audio to text using the on-device Whisper model within 3 seconds for typical utterances
4. WHEN transcription completes, THE Translexa app SHALL display the transcribed text in the chat interface
5. IF audio recording fails due to permission denial, THEN THE Translexa app SHALL display a clear permission request message and guide the user to settings

### Requirement 2: LLM Response Generation

**User Story:** As a user, I want the assistant to understand my queries and generate intelligent responses, so that I can get helpful information without internet access.

#### Acceptance Criteria

1. WHEN the user submits a text or voice query, THE Translexa app SHALL send the query to the on-device LLM for processing
2. WHEN the LLM generates a response, THE Translexa app SHALL stream tokens to the UI in real-time for immediate feedback
3. WHEN generating responses, THE Translexa app SHALL include the last 10 conversation exchanges as context for coherent multi-turn dialogue
4. WHEN the user asks travel or survival-related questions, THE Translexa app SHALL provide domain-specific guidance using the specialized system prompt
5. IF the LLM model is not loaded, THEN THE Translexa app SHALL display a status message and prompt the user to download/load a model

### Requirement 3: Text-to-Speech Output

**User Story:** As a user, I want the assistant to speak its responses aloud, so that I can receive information hands-free while driving, hiking, or in emergencies.

#### Acceptance Criteria

1. WHEN the LLM completes generating a response, THE Translexa app SHALL convert the response text to speech using the on-device TTS engine
2. WHEN TTS playback begins, THE Translexa app SHALL display a visual indicator showing the assistant is speaking
3. WHEN the user taps a stop button during TTS playback, THE Translexa app SHALL immediately stop audio output
4. WHEN TTS is enabled in settings, THE Translexa app SHALL automatically speak all assistant responses
5. IF TTS initialization fails, THEN THE Translexa app SHALL fall back to text-only mode and notify the user

### Requirement 4: Structured Action Execution

**User Story:** As a user, I want to perform in-app actions through voice commands, so that I can create reminders, manage tasks, and control the app hands-free.

#### Acceptance Criteria

1. WHEN the user issues an action command (e.g., "create a reminder"), THE Translexa app SHALL instruct the LLM to output a structured JSON response
2. WHEN the LLM outputs valid JSON with an action type, THE Translexa app SHALL parse the JSON and execute the corresponding action
3. WHEN a reminder action is parsed, THE Translexa app SHALL create a local notification scheduled for the specified time
4. WHEN a task action is parsed, THE Translexa app SHALL store the task in local storage and confirm creation to the user
5. IF the LLM outputs invalid JSON or unrecognized action, THEN THE Translexa app SHALL treat the response as conversational text and display it normally
6. WHEN the user requests to list reminders or tasks, THE Translexa app SHALL retrieve and display all stored items

### Requirement 5: Reminder and Task Management

**User Story:** As a user, I want to create, view, and delete reminders and tasks using voice or text, so that I can stay organized during travel without manual input.

#### Acceptance Criteria

1. WHEN the user says "remind me to [task] at [time]", THE Translexa app SHALL create a reminder with the parsed task and time
2. WHEN the user says "show my reminders" or "list tasks", THE Translexa app SHALL display all active reminders and tasks
3. WHEN the user says "delete reminder [identifier]", THE Translexa app SHALL remove the specified reminder from storage
4. WHEN a reminder time arrives, THE Translexa app SHALL display a local notification with the reminder content
5. WHEN the app restarts, THE Translexa app SHALL restore all previously saved reminders and tasks from local storage

### Requirement 6: Travel and Survival Guidance

**User Story:** As a traveler or emergency responder, I want specialized offline guidance for survival situations, so that I can get critical help when internet is unavailable.

#### Acceptance Criteria

1. WHEN the user asks about first-aid procedures, THE Translexa app SHALL provide step-by-step medical guidance based on the system prompt knowledge
2. WHEN the user asks about disaster response (earthquake, flood, fire), THE Translexa app SHALL provide emergency protocol guidance
3. WHEN the user asks about survival skills (finding water, shelter, navigation), THE Translexa app SHALL provide practical survival instructions
4. WHEN the user asks for travel tips or local information, THE Translexa app SHALL provide general travel guidance using LLM reasoning
5. WHEN providing critical safety information, THE Translexa app SHALL include appropriate disclaimers about seeking professional help

### Requirement 7: Model Management

**User Story:** As a user, I want to download and manage AI models within the app, so that I can use the assistant offline after initial setup.

#### Acceptance Criteria

1. WHEN the app launches for the first time, THE Translexa app SHALL display available models with their sizes and capabilities
2. WHEN the user initiates a model download, THE Translexa app SHALL show download progress percentage in real-time
3. WHEN a model download completes, THE Translexa app SHALL automatically make the model available for loading
4. WHEN the user selects a model to load, THE Translexa app SHALL load it into memory and display ready status
5. WHEN the app detects previously downloaded models on startup, THE Translexa app SHALL list them as available for immediate loading

### Requirement 8: Conversation Persistence

**User Story:** As a user, I want my conversation history to be saved, so that I can reference previous interactions and maintain context across app sessions.

#### Acceptance Criteria

1. WHEN a new message is added to the conversation, THE Translexa app SHALL persist it to local storage immediately
2. WHEN the app launches, THE Translexa app SHALL restore the previous conversation history from local storage
3. WHEN the user requests to clear conversation history, THE Translexa app SHALL delete all stored messages and reset the context
4. WHEN serializing conversation history, THE Translexa app SHALL encode messages using JSON format
5. WHEN deserializing conversation history, THE Translexa app SHALL parse the JSON and restore equivalent message objects

### Requirement 9: User Interface

**User Story:** As a user, I want a clean, intuitive interface that works well in various conditions, so that I can use the assistant easily even in stressful situations.

#### Acceptance Criteria

1. WHEN displaying the main screen, THE Translexa app SHALL show a chat message list, input field, and action buttons (microphone, send, model selector)
2. WHEN the assistant is processing, THE Translexa app SHALL display a loading indicator to show activity
3. WHEN new messages arrive, THE Translexa app SHALL auto-scroll to show the latest message
4. WHEN the user long-presses a message, THE Translexa app SHALL offer options to copy or delete the message
5. WHEN displaying in low-light conditions, THE Translexa app SHALL support a dark theme for readability

### Requirement 10: Offline Operation

**User Story:** As a user, I want the app to function completely offline after initial model download, so that I can rely on it in areas without connectivity.

#### Acceptance Criteria

1. WHEN the device has no internet connection, THE Translexa app SHALL continue to process voice input, generate responses, and execute actions using local resources
2. WHEN all required models are downloaded, THE Translexa app SHALL not require any network requests for core functionality
3. WHEN the app attempts a network operation that fails, THE Translexa app SHALL handle the failure gracefully without crashing
4. WHEN displaying offline status, THE Translexa app SHALL indicate that the app is operating in offline mode
5. WHEN the user attempts to download a model without internet, THE Translexa app SHALL display a clear message explaining internet is required for downloads only
