package com.runanywhere.startup_hackathon20

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.runanywhere.startup_hackathon20.domain.model.IntentType
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Full-screen voice assistant dialog.
 * - Continuous listening (stays open)
 * - Offline speech recognition
 * - Voice response via TTS
 * - Instant action execution
 */
class VoiceInputDialog(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    companion object {
        private const val TAG = "VoiceInputDialog"
    }

    private lateinit var voiceCircle: View
    private lateinit var pulseRing: View
    private lateinit var micIcon: ImageView
    private lateinit var voiceStatusText: TextView
    private lateinit var transcribedText: TextView
    private lateinit var hintText: TextView
    private lateinit var cancelButton: MaterialButton

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var pulseAnimator: AnimatorSet? = null
    private var isListening = false
    private var isSpeaking = false
    private var ttsReady = false
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_voice_input)
        setCancelable(false) // Don't dismiss on outside touch

        initViews()
        initTTS()
        setupClickListeners()
        startListening()
    }

    private fun initViews() {
        voiceCircle = findViewById(R.id.voiceCircle)
        pulseRing = findViewById(R.id.pulseRing)
        micIcon = findViewById(R.id.micIcon)
        voiceStatusText = findViewById(R.id.voiceStatusText)
        transcribedText = findViewById(R.id.transcribedText)
        hintText = findViewById(R.id.hintText)
        cancelButton = findViewById(R.id.cancelButton)
    }
    
    private fun initTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.1f)
                ttsReady = true
                Log.d(TAG, "TTS initialized")
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        // Resume listening after speaking
                        voiceCircle.post {
                            if (!isListening) {
                                voiceStatusText.text = "Listening..."
                                startListening()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            }
        }
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            cleanup()
            onCancel()
            dismiss()
        }

        // Tap on circle to restart listening or stop speaking
        voiceCircle.setOnClickListener {
            if (isSpeaking) {
                tts?.stop()
                isSpeaking = false
                startListening()
            } else if (!isListening) {
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceStatusText.text = "Speech not available"
            return
        }
        
        if (isSpeaking) return // Don't listen while speaking

        isListening = true
        voiceStatusText.text = "Listening..."
        hintText.visibility = View.VISIBLE
        startPulseAnimation()

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefer offline
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start", e)
            voiceStatusText.text = "Tap to retry"
        }
    }
    
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready")
            voiceStatusText.text = "Listening..."
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
            hintText.visibility = View.GONE
        }

        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB / 15f).coerceIn(0f, 0.4f)
            voiceCircle.scaleX = scale
            voiceCircle.scaleY = scale
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
            voiceStatusText.text = "Processing..."
            stopPulseAnimation()
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Error: $error")
            isListening = false
            stopPulseAnimation()
            
            // Auto-restart listening for most errors
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    voiceStatusText.text = "Listening..."
                    voiceCircle.postDelayed({ startListening() }, 300)
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    voiceStatusText.text = "Offline mode..."
                    voiceCircle.postDelayed({ startListening() }, 300)
                }
                else -> {
                    voiceStatusText.text = "Tap to retry"
                    hintText.visibility = View.VISIBLE
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            stopPulseAnimation()

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            var text = matches?.firstOrNull { it.isNotBlank() } ?: ""
            
            // Fallback to partial
            if (text.isBlank()) {
                text = transcribedText.text.toString()
            }

            Log.d(TAG, "Result: '$text'")

            if (text.isNotBlank()) {
                transcribedText.text = "You: $text"
                processVoiceInput(text)
            } else {
                voiceStatusText.text = "Listening..."
                voiceCircle.postDelayed({ startListening() }, 500)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull { it.isNotBlank() } ?: ""
            if (text.isNotBlank()) {
                transcribedText.text = text
                voiceStatusText.text = "Hearing..."
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    /**
     * Process voice input - detect intent and respond
     */
    private fun processVoiceInput(text: String) {
        voiceStatusText.text = "Thinking..."
        
        scope.launch {
            try {
                // Classify intent
                val classifier = MyApplication.instance.intentClassifier
                val classification = classifier.classify(text)
                
                Log.d(TAG, "Intent: ${classification.type}, Params: ${classification.extractedParams}")
                
                if (classification.type.isInstantAction()) {
                    // Execute instant action
                    val executor = MyApplication.instance.instantActionExecutor
                    val response = executor.execute(classification)
                    
                    Log.d(TAG, "Action response: ${response.text}")
                    speak(response.text)
                } else {
                    // For conversation/knowledge queries - use LLM or simple response
                    val response = getSimpleResponse(text, classification.type)
                    speak(response)
                }
                
                // Also send to main chat for history
                onResult(text)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing", e)
                speak("Sorry, something went wrong")
            }
        }
    }
    
    /**
     * Simple responses for common queries (offline fallback)
     */
    private fun getSimpleResponse(text: String, intentType: IntentType): String {
        val lower = text.lowercase()
        
        return when {
            lower.contains("hello") || lower.contains("hi") -> "Hello! How can I help you?"
            lower.contains("how are you") -> "I'm doing great, thanks for asking!"
            lower.contains("your name") -> "I'm Nova, your AI assistant"
            lower.contains("thank") -> "You're welcome!"
            lower.contains("bye") || lower.contains("goodbye") -> "Goodbye! Have a great day!"
            lower.contains("time") -> "I can't tell the time offline, but you can check your phone"
            lower.contains("weather") -> "I need internet to check the weather"
            lower.contains("help") -> "You can say things like: Open WhatsApp, Turn on flashlight, Set timer for 5 minutes, or Play music"
            intentType == IntentType.KNOWLEDGE_QUERY -> "I need to be online to answer that question. Try asking something simpler or give me a command like 'open camera'"
            else -> "I heard: $text. Try saying a command like 'open whatsapp' or 'turn on flashlight'"
        }
    }
    
    /**
     * Speak response using TTS
     */
    private fun speak(text: String) {
        voiceStatusText.text = "Speaking..."
        transcribedText.text = "AI: $text"
        
        if (ttsReady) {
            isSpeaking = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "response")
        } else {
            // TTS not ready, just restart listening
            voiceCircle.postDelayed({
                voiceStatusText.text = "Listening..."
                startListening()
            }, 2000)
        }
    }

    private fun stopListening() {
        isListening = false
        stopPulseAnimation()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        val scaleX = ObjectAnimator.ofFloat(pulseRing, "scaleX", 1f, 1.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(pulseRing, "scaleY", 1f, 1.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val alpha = ObjectAnimator.ofFloat(pulseRing, "alpha", 0.3f, 0.1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseRing.scaleX = 1f
        pulseRing.scaleY = 1f
        pulseRing.alpha = 0.3f
        voiceCircle.scaleX = 1f
        voiceCircle.scaleY = 1f
    }
    
    private fun cleanup() {
        scope.cancel()
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun dismiss() {
        cleanup()
        super.dismiss()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        cleanup()
        onCancel()
        super.onBackPressed()
    }
}
