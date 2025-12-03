package com.runanywhere.startup_hackathon20

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.runanywhere.startup_hackathon20.domain.service.GoogleSpeechService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.runanywhere.sdk.public.RunAnywhere

/**
 * Full-screen voice assistant activity.
 * Uses Google Speech Recognition for accurate STT + Android TTS.
 */
class VoiceAssistantActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceAssistant"
    }

    private lateinit var voiceCircle: View
    private lateinit var pulseRing: View
    private lateinit var micIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var transcribedText: TextView
    private lateinit var responseText: TextView
    private lateinit var hintText: TextView
    private lateinit var closeButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressContainer: View

    private var speechService: GoogleSpeechService? = null
    private var isProcessing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeSpeechService()
        } else {
            statusText.text = "Microphone permission required"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_assistant)

        initViews()
        setupClickListeners()
        checkPermissionAndInit()
    }

    private fun initViews() {
        voiceCircle = findViewById(R.id.voiceCircle)
        pulseRing = findViewById(R.id.pulseRing)
        micIcon = findViewById(R.id.micIcon)
        statusText = findViewById(R.id.statusText)
        transcribedText = findViewById(R.id.transcribedText)
        responseText = findViewById(R.id.responseText)
        hintText = findViewById(R.id.hintText)
        closeButton = findViewById(R.id.closeButton)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        progressContainer = findViewById(R.id.progressContainer)
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        voiceCircle.setOnClickListener {
            when (speechService?.state?.value) {
                is GoogleSpeechService.VoiceState.Idle,
                is GoogleSpeechService.VoiceState.Error -> {
                    speechService?.startListening()
                }
                is GoogleSpeechService.VoiceState.Listening -> {
                    speechService?.stopListening()
                }
                is GoogleSpeechService.VoiceState.Speaking -> {
                    speechService?.stopSpeaking()
                    speechService?.startListening()
                }
                else -> {}
            }
        }
    }

    private fun checkPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            initializeSpeechService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initializeSpeechService() {
        statusText.text = "Initializing..."
        progressContainer.visibility = View.GONE
        
        speechService = GoogleSpeechService(this)
        
        // Setup callbacks
        speechService?.onResult = { text ->
            runOnUiThread {
                processVoiceInput(text)
            }
        }
        
        speechService?.onSpeakingDone = {
            runOnUiThread {
                // Auto-restart listening after speaking
                if (!isFinishing && !isProcessing) {
                    speechService?.startListening()
                }
            }
        }

        // Observe state changes
        lifecycleScope.launch {
            speechService?.state?.collectLatest { state ->
                runOnUiThread {
                    updateUI(state)
                }
            }
        }

        // Observe partial results
        lifecycleScope.launch {
            speechService?.partialResult?.collectLatest { partial ->
                runOnUiThread {
                    if (partial.isNotBlank()) {
                        transcribedText.text = partial
                        transcribedText.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Initialize and start listening
        speechService?.initialize {
            runOnUiThread {
                statusText.text = "Tap to speak"
                hintText.visibility = View.VISIBLE
                // Auto-start listening
                speechService?.startListening()
            }
        }
    }

    private fun updateUI(state: GoogleSpeechService.VoiceState) {
        when (state) {
            is GoogleSpeechService.VoiceState.Idle -> {
                statusText.text = "Tap to speak"
                micIcon.setImageResource(R.drawable.audio_icon)
                stopPulseAnimation()
                hintText.visibility = View.VISIBLE
            }
            is GoogleSpeechService.VoiceState.Listening -> {
                statusText.text = "Listening..."
                micIcon.setImageResource(R.drawable.audio_icon)
                startPulseAnimation()
                hintText.visibility = View.GONE
                responseText.visibility = View.GONE
            }
            is GoogleSpeechService.VoiceState.Processing -> {
                statusText.text = "Processing..."
                stopPulseAnimation()
            }
            is GoogleSpeechService.VoiceState.Speaking -> {
                statusText.text = "Speaking..."
                micIcon.setImageResource(R.drawable.audio_icon)
                stopPulseAnimation()
            }
            is GoogleSpeechService.VoiceState.Error -> {
                statusText.text = state.message
                stopPulseAnimation()
                hintText.visibility = View.VISIBLE
            }
        }
    }

    private fun processVoiceInput(text: String) {
        if (isProcessing) return
        if (text.isBlank() || text.length < 2) {
            speechService?.startListening()
            return
        }
        
        isProcessing = true
        
        Log.d(TAG, "Processing: $text")
        transcribedText.text = "You: $text"
        transcribedText.visibility = View.VISIBLE
        statusText.text = "Thinking..."

        lifecycleScope.launch {
            try {
                // Classify intent
                val classifier = MyApplication.instance.intentClassifier
                val classification = classifier.classify(text)

                Log.d(TAG, "Intent: ${classification.type}, Params: ${classification.extractedParams}")

                if (classification.type.isInstantAction()) {
                    // FAST PATH: Execute instant action (commands) - no LLM needed
                    Log.d(TAG, "Executing command...")
                    val executor = MyApplication.instance.instantActionExecutor
                    val result = executor.execute(classification)
                    
                    runOnUiThread {
                        responseText.text = result.text
                        responseText.visibility = View.VISIBLE
                        speechService?.speak(result.text)
                    }
                } else {
                    // PROGRESSIVE PATH: Use LLM with streaming TTS
                    Log.d(TAG, "Using Progressive LLM + TTS...")
                    getLLMResponseWithProgressiveTTS(text)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing", e)
                runOnUiThread {
                    val errorMsg = "Sorry, I couldn't process that. Please try again."
                    responseText.text = errorMsg
                    responseText.visibility = View.VISIBLE
                    speechService?.speak(errorMsg)
                }
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * Smart token selection for voice based on query type
     */
    private fun getSmartVoiceTokens(query: String): Int {
        val lowerQuery = query.lowercase()
        return when {
            // Detailed explanations
            lowerQuery.contains("explain") || 
            lowerQuery.contains("how does") ||
            lowerQuery.contains("why") ||
            lowerQuery.contains("tell me about") -> 250
            
            // Short questions
            lowerQuery.startsWith("what is") ||
            lowerQuery.startsWith("who is") ||
            query.split(" ").size <= 4 -> 100
            
            // Default for voice
            else -> 150
        }
    }
    
    /**
     * Smart LLM Response for Voice
     * Adjusts tokens based on query complexity
     */
    private suspend fun getLLMResponseWithProgressiveTTS(text: String) {
        withContext(Dispatchers.IO) {
            try {
                val smartTokens = getSmartVoiceTokens(text)
                val prompt = "Answer concisely: $text"
                
                Log.d(TAG, "Voice query: $text, SmartTokens: $smartTokens")
                
                val fullResponse = StringBuilder()
                val currentSentence = StringBuilder()
                val sentencesSpoken = mutableListOf<String>()
                var hasStartedSpeaking = false
                var sentenceCount = 0
                val maxSentences = if (smartTokens > 150) 3 else 2
                
                // Smart generation options for voice
                val smartOptions = com.runanywhere.sdk.models.RunAnywhereGenerationOptions(
                    maxTokens = smartTokens,
                    temperature = 0.7f,
                    topP = 0.9f,
                    enableRealTimeTracking = false,
                    stopSequences = listOf("\n\n", "User:", "Q:"),
                    streamingEnabled = true,
                    preferredExecutionTarget = null,
                    structuredOutput = null,
                    systemPrompt = null,
                    topK = 40,
                    repetitionPenalty = 1.1f,
                    frequencyPenalty = null,
                    presencePenalty = null,
                    seed = null,
                    contextLength = 512
                )
                
                try {
                    RunAnywhere.generateStream(prompt, smartOptions).collect { token ->
                        // Stop if we've spoken enough sentences
                        if (sentenceCount >= maxSentences) {
                            return@collect
                        }
                        
                        fullResponse.append(token)
                        currentSentence.append(token)
                        
                        // Update UI with streaming text
                        val displayText = fullResponse.toString()
                            .removePrefix("Assistant:")
                            .trim()
                        
                        runOnUiThread {
                            responseText.text = displayText
                            responseText.visibility = View.VISIBLE
                        }
                        
                        // Check if we have a complete sentence
                        val sentenceText = currentSentence.toString()
                        if (isSentenceComplete(sentenceText)) {
                            val cleanSentence = cleanSentence(sentenceText)
                            
                            if (cleanSentence.length > 5) { // Minimum sentence length
                                sentenceCount++
                                sentencesSpoken.add(cleanSentence)
                                currentSentence.clear()
                                
                                // PROGRESSIVE TTS: Speak immediately!
                                if (!hasStartedSpeaking) {
                                    hasStartedSpeaking = true
                                    runOnUiThread {
                                        statusText.text = "Speaking..."
                                        // Start speaking first sentence immediately
                                        speechService?.speakWithQueue(cleanSentence)
                                    }
                                } else {
                                    // Queue next sentences
                                    runOnUiThread {
                                        speechService?.queueSentence(cleanSentence)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM stream error", e)
                    if (!hasStartedSpeaking) {
                        runOnUiThread {
                            val errorMsg = "Please load an AI model first from the home screen."
                            responseText.text = errorMsg
                            responseText.visibility = View.VISIBLE
                            speechService?.speak(errorMsg)
                        }
                    }
                    return@withContext
                }
                
                // Handle any remaining text that didn't end with punctuation
                val remaining = currentSentence.toString().trim()
                if (remaining.length > 5 && sentenceCount < maxSentences) {
                    val cleanRemaining = cleanSentence(remaining)
                    if (!hasStartedSpeaking) {
                        runOnUiThread {
                            statusText.text = "Speaking..."
                            speechService?.speak(cleanRemaining)
                        }
                    } else {
                        runOnUiThread {
                            speechService?.queueSentence(cleanRemaining)
                        }
                    }
                }
                
                // If nothing was spoken at all
                if (!hasStartedSpeaking && fullResponse.isBlank()) {
                    runOnUiThread {
                        val fallback = "I'm not sure how to answer that."
                        responseText.text = fallback
                        responseText.visibility = View.VISIBLE
                        speechService?.speak(fallback)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Progressive TTS error", e)
                runOnUiThread {
                    val errorMsg = "I had trouble with that. Please try again."
                    responseText.text = errorMsg
                    responseText.visibility = View.VISIBLE
                    speechService?.speak(errorMsg)
                }
            }
        }
    }
    
    private fun isSentenceComplete(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.endsWith(".") || 
               trimmed.endsWith("!") || 
               trimmed.endsWith("?") ||
               trimmed.endsWith("।") // Hindi support
    }
    
    private fun cleanSentence(text: String): String {
        return text
            .removePrefix("Assistant:")
            .trim()
            .trim('"', '\'')
    }
    
    // Keep old method for fallback/commands
    private suspend fun getLLMResponse(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """You are Nova, a helpful AI assistant. Answer briefly and directly.

Q: $text
A:"""
                
                val responseBuilder = StringBuilder()
                var hasResponse = false
                
                try {
                    RunAnywhere.generateStream(prompt).collect { token ->
                        responseBuilder.append(token)
                        hasResponse = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "LLM generate error", e)
                    return@withContext "Please load an AI model first from the home screen."
                }
                
                if (!hasResponse) {
                    return@withContext "Please load an AI model first from the home screen."
                }
                
                var response = responseBuilder.toString().trim()
                response = response.removePrefix("A:").trim()
                response = response.trim('"', '\'')
                
                if (response.isBlank()) {
                    "I'm not sure how to answer that."
                } else {
                    val sentences = response.split(Regex("(?<=[.!?])\\s+"))
                        .filter { it.isNotBlank() }
                        .take(3)
                    
                    if (sentences.isEmpty()) {
                        response.take(200)
                    } else {
                        sentences.joinToString(" ") { it.trim() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM error", e)
                "I had trouble with that. Please try again."
            }
        }
    }

    private fun startPulseAnimation() {
        pulseRing.visibility = View.VISIBLE
        val pulse = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        pulse.repeatCount = -1
        pulse.duration = 1000
        pulseRing.startAnimation(pulse)
    }

    private fun stopPulseAnimation() {
        pulseRing.clearAnimation()
        pulseRing.visibility = View.INVISIBLE
    }

    override fun onPause() {
        super.onPause()
        speechService?.stopListening()
        speechService?.stopSpeaking()
    }
    
    override fun onDestroy() {
        speechService?.onResult = null
        speechService?.onSpeakingDone = null
        
        try {
            speechService?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing speech service", e)
        }
        speechService = null
        
        super.onDestroy()
    }
}
