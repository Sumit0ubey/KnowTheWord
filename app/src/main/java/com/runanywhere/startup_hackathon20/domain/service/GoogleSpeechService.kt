package com.runanywhere.startup_hackathon20.domain.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Fast and accurate speech service using Android's built-in SpeechRecognizer.
 * Uses Google's speech recognition for best accuracy.
 * NOW reads voice settings from UserSettings for consistent voice!
 */
class GoogleSpeechService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSpeechService"
    }

    sealed class VoiceState {
        object Idle : VoiceState()
        object Listening : VoiceState()
        object Processing : VoiceState()
        object Speaking : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false

    // Callbacks
    var onResult: ((String) -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null

    // Queue for progressive TTS
    private val sentenceQueue = mutableListOf<String>()
    private var isQueueSpeaking = false
    private var pendingUtterances = 0

    // Voice settings
    private var voiceSpeed = 1.0f
    private var voicePitch = 1.0f
    private var savedActualVoiceName: String = ""
    private var savedVoiceCharacter: String = ""

    // For reading UserSettings
    private val userPrefs by lazy {
        context.getSharedPreferences("translexa_data", Context.MODE_PRIVATE)
    }
    private val gson by lazy { com.google.gson.Gson() }

    fun initialize(onReady: () -> Unit) {
        // Load saved voice settings FIRST
        loadVoiceSettings()

        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Apply saved voice settings
                applyVoiceSettings()
                ttsReady = true
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = VoiceState.Speaking
                    }
                    override fun onDone(utteranceId: String?) {
                        pendingUtterances--
                        
                        // Check if more sentences in queue
                        if (pendingUtterances <= 0 && sentenceQueue.isEmpty()) {
                            isQueueSpeaking = false
                            _state.value = VoiceState.Idle
                            onSpeakingDone?.invoke()
                        } else {
                            // Speak next queued sentence
                            speakNextInQueue()
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        pendingUtterances--
                        if (pendingUtterances <= 0) {
                            isQueueSpeaking = false
                            _state.value = VoiceState.Idle
                        }
                    }
                })
            }
        }

        // Check if speech recognition is available
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupRecognitionListener()
            Log.d(TAG, "Speech recognition initialized")
            onReady()
        } else {
            Log.e(TAG, "Speech recognition not available")
            _state.value = VoiceState.Error("Speech recognition not available on this device")
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
                _state.value = VoiceState.Listening
                _partialResult.value = ""
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can use for visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
                _state.value = VoiceState.Processing
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap to try again."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error - trying offline"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    else -> "Recognition error: $error"
                }
                Log.e(TAG, "Recognition error: $errorMessage")
                _state.value = VoiceState.Error(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val result = matches?.firstOrNull() ?: ""
                
                Log.d(TAG, "Final result: $result")
                _state.value = VoiceState.Idle
                
                if (result.isNotBlank()) {
                    onResult?.invoke(result)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                
                if (partial.isNotBlank()) {
                    _partialResult.value = partial
                    Log.d(TAG, "Partial: $partial")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        // Stop TTS if speaking
        tts?.stop()

        isListening = true
        _partialResult.value = ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Prefer offline if available
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recognition", e)
            isListening = false
            _state.value = VoiceState.Error("Failed to start listening")
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            _state.value = VoiceState.Idle
        }
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        
        stopListening()
        clearQueue()

        // Reload voice settings before speaking to use latest settings
        Log.d(TAG, "🔊 speak() called, reloading voice settings...")
        reloadVoiceSettings()

        // Log the ACTUAL voice that will be used
        val currentVoice = tts?.voice
        Log.d(TAG, "🔊 SPEAKING with voice: ${currentVoice?.name ?: "DEFAULT"}")
        Log.d(TAG, "🔊 Voice locale: ${currentVoice?.locale}")
        Log.d(TAG, "🔊 Speed: $voiceSpeed, Pitch: $voicePitch")

        _state.value = VoiceState.Speaking
        pendingUtterances = 1

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        Log.d(TAG, "🔊 Speaking: ${text.take(50)}...")
    }

    /**
     * Start speaking with queue support for progressive TTS.
     * First sentence starts immediately, subsequent ones are queued.
     */
    fun speakWithQueue(text: String) {
        if (!ttsReady || text.isBlank()) return

        stopListening()
        clearQueue()

        // Reload voice settings before speaking to use latest settings
        Log.d(TAG, "🔊 speakWithQueue() called, reloading voice settings...")
        reloadVoiceSettings()

        // Log the ACTUAL voice that will be used
        val currentVoice = tts?.voice
        Log.d(TAG, "🔊 SPEAKING (queue) with voice: ${currentVoice?.name ?: "DEFAULT"}")
        Log.d(TAG, "🔊 Voice locale: ${currentVoice?.locale}")
        Log.d(TAG, "🔊 Speed: $voiceSpeed, Pitch: $voicePitch")

        isQueueSpeaking = true
        _state.value = VoiceState.Speaking
        pendingUtterances = 1

        // Speak first sentence immediately with QUEUE_FLUSH
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "utterance_first_${System.currentTimeMillis()}"
        )
        Log.d(TAG, "🔊 Speaking first: ${text.take(50)}...")
    }
    
    /**
     * Queue a sentence to be spoken after current one finishes.
     * Used for progressive TTS - sentences are added as LLM generates them.
     */
    fun queueSentence(text: String) {
        if (!ttsReady || text.isBlank()) return
        
        if (isQueueSpeaking) {
            // Add to TTS queue directly for seamless playback
            pendingUtterances++
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "utterance_queue_${System.currentTimeMillis()}")
            Log.d(TAG, "Queued: $text")
        } else {
            // Not speaking yet, add to our queue
            sentenceQueue.add(text)
            Log.d(TAG, "Added to queue: $text")
        }
    }
    
    private fun speakNextInQueue() {
        if (sentenceQueue.isNotEmpty()) {
            val next = sentenceQueue.removeAt(0)
            pendingUtterances++
            tts?.speak(next, TextToSpeech.QUEUE_ADD, null, "utterance_next_${System.currentTimeMillis()}")
            Log.d(TAG, "Speaking next from queue: $next")
        }
    }
    
    private fun clearQueue() {
        sentenceQueue.clear()
        pendingUtterances = 0
        isQueueSpeaking = false
    }

    fun stopSpeaking() {
        tts?.stop()
        clearQueue()
        if (_state.value == VoiceState.Speaking) {
            _state.value = VoiceState.Idle
        }
    }
    
    /**
     * Load voice settings from UserSettings (same source as Settings preview)
     */
    private fun loadVoiceSettings() {
        try {
            val settingsJson = userPrefs.getString("user_settings", null)
            Log.d(TAG, "📖 Loading voice settings, json exists: ${settingsJson != null}")
            
            if (settingsJson != null) {
                val settings = gson.fromJson(
                    settingsJson,
                    com.runanywhere.startup_hackathon20.domain.model.UserSettings::class.java
                )
                if (settings != null) {
                    voiceSpeed = settings.voiceSpeed.coerceIn(0.5f, 2.0f)
                    voicePitch = settings.voicePitch.coerceIn(0.5f, 2.0f)
                    savedActualVoiceName = settings.actualVoiceName
                    savedVoiceCharacter = settings.voiceCharacter

                    Log.d(
                        TAG,
                        "✅ Loaded settings: voice='$savedVoiceCharacter', actual='$savedActualVoiceName', speed=$voiceSpeed, pitch=$voicePitch"
                    )
                } else {
                    Log.w(TAG, "⚠️ Settings parsed as null")
                }
            } else {
                Log.w(TAG, "⚠️ No user_settings found in SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading voice settings: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Apply voice settings to TTS engine
     * Matches the EXACT same voice as Settings preview!
     */
    private fun applyVoiceSettings() {
        Log.d(TAG, "🎤 Applying voice settings to TTS...")
        Log.d(TAG, "🎤 savedActualVoiceName='$savedActualVoiceName', savedVoiceCharacter='$savedVoiceCharacter'")

        // Apply speed and pitch FIRST
        tts?.setSpeechRate(voiceSpeed)
        tts?.setPitch(voicePitch)
        Log.d(TAG, "🎤 Applied speed=$voiceSpeed, pitch=$voicePitch")

        val availableVoices = tts?.voices
        if (availableVoices == null || availableVoices.isEmpty()) {
            Log.w(TAG, "⚠️ No voices available yet, using default locale")
            tts?.language = Locale.US
            return
        }

        Log.d(TAG, "🎤 Available voices count: ${availableVoices.size}")
        
        // Log first 5 available voices for debugging
        availableVoices.take(5).forEach { v ->
            Log.d(TAG, "   Voice: ${v.name} [${v.locale}]")
        }

        // PRIORITY 1: Use exact saved voice name (from Settings preview)
        if (savedActualVoiceName.isNotBlank()) {
            Log.d(TAG, "🔍 Looking for exact match: '$savedActualVoiceName'")
            val exactVoice = availableVoices.find { it.name == savedActualVoiceName }
            if (exactVoice != null) {
                tts?.voice = exactVoice
                // Verify voice was set
                val currentVoice = tts?.voice
                Log.d(TAG, "✅ SUCCESS! Set voice to: ${exactVoice.name}")
                Log.d(TAG, "✅ Verified current voice: ${currentVoice?.name}")
                return
            } else {
                Log.w(TAG, "⚠️ Exact voice not found: $savedActualVoiceName")

                // Log all available voice names for debugging
                Log.d(TAG, "Available voice names:")
                availableVoices.forEach { v ->
                    Log.d(TAG, "   - ${v.name}")
                }

                // Try partial match - more flexible matching
                val savedName = savedActualVoiceName.lowercase()
                Log.d(TAG, "🔍 Trying partial match for: '$savedName'")

                val partialMatch = availableVoices.find { voice ->
                    val vName = voice.name.lowercase()
                    // Try multiple matching strategies
                    vName == savedName ||
                            vName.contains(savedName.substringBefore("#")) ||
                            savedName.contains(vName.substringBefore("#")) ||
                            vName.contains(savedName.substringBefore("-")) ||
                            savedName.contains(vName.substringBefore("-"))
                }
                if (partialMatch != null) {
                    tts?.voice = partialMatch
                    Log.d(TAG, "✅ SUCCESS! Using partial match voice: ${partialMatch.name}")
                    Log.d(TAG, "✅ Verified current voice: ${tts?.voice?.name}")
                    return
                } else {
                    Log.w(TAG, "⚠️ No partial match found either")
                }
            }
        } else {
            Log.d(TAG, "📝 savedActualVoiceName is blank, skipping exact match")
        }

        // PRIORITY 2: Match by voice character name (locale + gender)
        if (savedVoiceCharacter.isNotBlank()) {
            Log.d(TAG, "🔍 Trying to match by character: '$savedVoiceCharacter'")
            val matchedVoice = findVoiceByCharacterName(savedVoiceCharacter, availableVoices)
            if (matchedVoice != null) {
                tts?.voice = matchedVoice
                Log.d(TAG, "✅ SUCCESS! Using matched voice: ${matchedVoice.name}")
                Log.d(TAG, "✅ Verified current voice: ${tts?.voice?.name}")
                return
            } else {
                Log.w(TAG, "⚠️ No voice matched for character: $savedVoiceCharacter")
            }
        } else {
            Log.d(TAG, "📝 savedVoiceCharacter is blank")
        }

        // PRIORITY 3: Default to US English  
        Log.w(TAG, "⚠️ FALLBACK: Using default US English")
        tts?.language = Locale.US
        Log.d(TAG, "⚠️ Final voice: ${tts?.voice?.name}")
    }

    /**
     * Find best matching voice by character name (locale + gender)
     */
    private fun findVoiceByCharacterName(
        charName: String,
        availableVoices: Set<android.speech.tts.Voice>
    ): android.speech.tts.Voice? {
        val voiceChar = charName.lowercase()

        // Extract locale and gender from character name
        val wantFemale = voiceChar.contains("female")
        val wantMale = voiceChar.contains("male") && !wantFemale

        // Determine target locale from character name
        val targetLocale = when {
            voiceChar.contains("indian") || voiceChar.contains("🇮🇳") -> Locale("en", "IN")
            voiceChar.contains("us ") || voiceChar.contains("us english") || voiceChar.contains("🇺🇸") -> Locale.US
            voiceChar.contains("british") || voiceChar.contains("uk ") || voiceChar.contains("🇬🇧") -> Locale.UK
            voiceChar.contains("australia") || voiceChar.contains("🇦🇺") -> Locale("en", "AU")
            voiceChar.contains("canad") || voiceChar.contains("🇨🇦") -> Locale.CANADA
            voiceChar.contains("spanish") || voiceChar.contains("🇪🇸") -> Locale("es", "ES")
            voiceChar.contains("french") || voiceChar.contains("🇫🇷") -> Locale.FRANCE
            voiceChar.contains("german") || voiceChar.contains("🇩🇪") -> Locale.GERMANY
            voiceChar.contains("italian") || voiceChar.contains("🇮🇹") -> Locale.ITALY
            voiceChar.contains("japanese") || voiceChar.contains("🇯🇵") -> Locale.JAPAN
            voiceChar.contains("korean") || voiceChar.contains("🇰🇷") -> Locale.KOREA
            voiceChar.contains("chinese") || voiceChar.contains("🇨🇳") -> Locale.CHINA
            else -> Locale.US
        }

        Log.d(TAG, "🎯 Target: locale=$targetLocale, female=$wantFemale, male=$wantMale")

        // Find best matching voice
        var bestVoice: android.speech.tts.Voice? = null
        var bestScore = -1

        for (voice in availableVoices) {
            var score = 0
            val vName = voice.name.lowercase()
            val vLang = voice.locale.language
            val vCountry = voice.locale.country

            // Language must match
            if (vLang != targetLocale.language) continue
            score += 10

            // Country match bonus
            if (vCountry == targetLocale.country) {
                score += 5
            }

            // Gender matching
            val isFemaleVoice = vName.contains("female") || vName.contains("#female") ||
                    vName.contains("-f-") || vName.contains("_f_")
            val isMaleVoice = vName.contains("male") && !vName.contains("female") ||
                    vName.contains("#male") || vName.contains("-m-") || vName.contains("_m_")

            if (wantFemale && isFemaleVoice) {
                score += 8
            } else if (wantMale && isMaleVoice) {
                score += 8
            } else if (wantFemale && !isMaleVoice) {
                score += 2
            } else if (wantMale && !isFemaleVoice) {
                score += 2
            }

            // Prefer local voices
            if (!voice.isNetworkConnectionRequired) {
                score += 3
            }

            if (score > bestScore) {
                bestScore = score
                bestVoice = voice
            }
        }

        return bestVoice
    }

    /**
     * Reload voice settings (call before speaking to get latest settings)
     */
    fun reloadVoiceSettings() {
        loadVoiceSettings()
        if (ttsReady) {
            applyVoiceSettings()
        }
    }

    /**
     * Update voice settings (speed and pitch)
     */
    fun setVoiceSettings(speed: Float, pitch: Float) {
        voiceSpeed = speed.coerceIn(0.5f, 2.0f)
        voicePitch = pitch.coerceIn(0.5f, 2.0f)
        
        tts?.setSpeechRate(voiceSpeed)
        tts?.setPitch(voicePitch)
        
        Log.d(TAG, "Voice settings updated: speed=$voiceSpeed, pitch=$voicePitch")
    }

    fun release() {
        Log.d(TAG, "Releasing speech service")
        
        onResult = null
        onSpeakingDone = null
        
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = null
        
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        ttsReady = false
        isListening = false
    }
}
