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

    fun initialize(onReady: () -> Unit) {
        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(voiceSpeed)
                tts?.setPitch(voicePitch)
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
        _state.value = VoiceState.Speaking
        pendingUtterances = 1
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        Log.d(TAG, "Speaking: $text")
    }
    
    /**
     * Start speaking with queue support for progressive TTS.
     * First sentence starts immediately, subsequent ones are queued.
     */
    fun speakWithQueue(text: String) {
        if (!ttsReady || text.isBlank()) return
        
        stopListening()
        clearQueue()
        isQueueSpeaking = true
        _state.value = VoiceState.Speaking
        pendingUtterances = 1
        
        // Speak first sentence immediately with QUEUE_FLUSH
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_first_${System.currentTimeMillis()}")
        Log.d(TAG, "Speaking first: $text")
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
