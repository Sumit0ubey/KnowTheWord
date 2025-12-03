package com.runanywhere.startup_hackathon20.domain.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
// StorageService removed - using manual asset extraction
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Complete offline voice service with:
 * - VAD (Voice Activity Detection) - built into Vosk
 * - STT (Speech-to-Text) - Vosk offline recognition
 * - TTS (Text-to-Speech) - Android built-in
 * 
 * Auto-downloads and configures everything on first run.
 */
class OfflineVoiceService(private val context: Context) {

    companion object {
        private const val TAG = "OfflineVoiceService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    // States
    sealed class VoiceState {
        object Initializing : VoiceState()
        object Ready : VoiceState()
        object Listening : VoiceState()
        object Processing : VoiceState()
        object Speaking : VoiceState()
        data class Error(val message: String) : VoiceState()
    }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Initializing)
    val state: StateFlow<VoiceState> = _state

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult

    private val _modelDownloadProgress = MutableStateFlow(0f)
    val modelDownloadProgress: StateFlow<Float> = _modelDownloadProgress

    // Vosk components
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    
    // TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Control flags
    private val isListening = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var listeningJob: Job? = null
    
    // Callbacks
    var onFinalResult: ((String) -> Unit)? = null
    var onSpeakingDone: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initialize the voice service - downloads model if needed
     */
    suspend fun initialize(): Boolean {
        if (isInitialized.get()) return true
        
        _state.value = VoiceState.Initializing
        
        return try {
            // Initialize TTS first (quick)
            initTTS()
            
            // Initialize Vosk model (may need download)
            val modelReady = initVoskModel()
            
            if (modelReady) {
                isInitialized.set(true)
                _state.value = VoiceState.Ready
                Log.i(TAG, "Voice service initialized successfully")
                true
            } else {
                _state.value = VoiceState.Error("Failed to initialize speech model")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            _state.value = VoiceState.Error(e.message ?: "Unknown error")
            false
        }
    }

    private suspend fun initTTS() {
        withContext(Dispatchers.Main) {
            val latch = CompletableDeferred<Boolean>()
            
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(1.0f)
                    ttsReady = true
                    
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.value = VoiceState.Speaking
                        }
                        override fun onDone(utteranceId: String?) {
                            onSpeakingDone?.invoke()
                            if (isInitialized.get()) {
                                _state.value = VoiceState.Ready
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            _state.value = VoiceState.Ready
                        }
                    })
                    
                    Log.d(TAG, "TTS initialized")
                }
                latch.complete(status == TextToSpeech.SUCCESS)
            }
            
            latch.await()
        }
    }

    private suspend fun initVoskModel(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(context.filesDir, "model")
                
                // Check if model already extracted
                if (modelDir.exists() && modelDir.isDirectory) {
                    val files = modelDir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        Log.d(TAG, "Loading existing Vosk model from: ${modelDir.absolutePath}")
                        try {
                            voskModel = Model(modelDir.absolutePath)
                            _modelDownloadProgress.value = 1.0f
                            Log.d(TAG, "Vosk model loaded successfully")
                            return@withContext true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load existing model, will re-extract", e)
                            modelDir.deleteRecursively()
                        }
                    }
                }
                
                // Extract model from assets manually
                Log.d(TAG, "Extracting Vosk model from assets...")
                _modelDownloadProgress.value = 0.1f
                
                val assetModelPath = "vosk-model-small-en-us-0.15"
                
                // Create model directory
                modelDir.mkdirs()
                
                // Copy all files from assets
                copyAssetFolder(assetModelPath, modelDir.absolutePath)
                
                _modelDownloadProgress.value = 0.9f
                
                // Load the model
                Log.d(TAG, "Loading extracted model...")
                voskModel = Model(modelDir.absolutePath)
                _modelDownloadProgress.value = 1.0f
                Log.d(TAG, "Vosk model loaded successfully")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init Vosk model", e)
                _modelDownloadProgress.value = 0f
                false
            }
        }
    }
    
    private fun copyAssetFolder(assetPath: String, destPath: String) {
        try {
            val assets = context.assets.list(assetPath) ?: return
            
            if (assets.isEmpty()) {
                // It's a file, copy it
                copyAssetFile(assetPath, destPath)
            } else {
                // It's a folder, create it and copy contents
                File(destPath).mkdirs()
                for (asset in assets) {
                    copyAssetFolder("$assetPath/$asset", "$destPath/$asset")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset folder: $assetPath", e)
        }
    }
    
    private fun copyAssetFile(assetPath: String, destPath: String) {
        try {
            context.assets.open(assetPath).use { input ->
                File(destPath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset file: $assetPath", e)
        }
    }

    /**
     * Start listening for voice input
     */
    fun startListening() {
        if (!isInitialized.get()) {
            Log.w(TAG, "Service not initialized")
            return
        }
        
        if (isListening.get()) {
            Log.w(TAG, "Already listening")
            return
        }
        
        // Stop TTS if speaking
        tts?.stop()
        
        isListening.set(true)
        _state.value = VoiceState.Listening
        _partialResult.value = ""
        
        listeningJob = scope.launch {
            try {
                startRecognition()
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                _state.value = VoiceState.Error(e.message ?: "Recognition failed")
            }
        }
    }

    private suspend fun startRecognition() = withContext(Dispatchers.IO) {
        val model = voskModel ?: return@withContext
        
        // Create recognizer - simpler settings for better accuracy
        recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
        
        // Optimal buffer size - not too large, not too small
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2 // Double the minimum
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // Optimized for speech
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = VoiceState.Error("Failed to initialize microphone")
            return@withContext
        }
        
        audioRecord?.startRecording()
        Log.d(TAG, "Started recording - speak clearly")
        
        // Smaller read buffer for more frequent processing
        val readSize = minBufferSize / 2
        val buffer = ShortArray(readSize)
        var silenceCount = 0
        val maxSilence = 50 // ~2.5 seconds of silence
        var hasSpoken = false
        var lastPartialLength = 0
        
        while (isListening.get()) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            
            if (read > 0) {
                val byteBuffer = ByteArray(read * 2)
                for (i in 0 until read) {
                    byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                }
                
                val isFinal = recognizer?.acceptWaveForm(byteBuffer, byteBuffer.size) ?: false
                
                if (isFinal) {
                    val result = recognizer?.result ?: "{}"
                    val text = parseVoskResult(result)
                    
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Final result: $text")
                        isListening.set(false)
                        cleanupAudio()
                        val callback = onFinalResult
                        withContext(Dispatchers.Main) {
                            callback?.invoke(text)
                        }
                        return@withContext
                    }
                } else {
                    val partial = recognizer?.partialResult ?: "{}"
                    val text = parseVoskPartial(partial)
                    
                    if (text.isNotBlank()) {
                        // Check if partial result is growing (user still speaking)
                        if (text.length > lastPartialLength) {
                            silenceCount = 0
                        }
                        lastPartialLength = text.length
                        _partialResult.value = text
                        hasSpoken = true
                        Log.d(TAG, "Partial: $text")
                    } else if (hasSpoken) {
                        silenceCount++
                    }
                }
                
                // VAD: Stop after prolonged silence ONLY if user has spoken
                if (hasSpoken && silenceCount > maxSilence && _partialResult.value.isNotBlank()) {
                    // Get final result
                    val finalResult = recognizer?.finalResult ?: "{}"
                    var finalText = parseVoskResult(finalResult)
                    
                    // If final is empty, use partial
                    if (finalText.isBlank()) {
                        finalText = _partialResult.value
                    }
                    
                    // Clean up the text
                    finalText = finalText.trim()
                    
                    Log.d(TAG, "Final text: $finalText")
                    isListening.set(false)
                    cleanupAudio()
                    val callback = onFinalResult
                    withContext(Dispatchers.Main) {
                        callback?.invoke(finalText)
                    }
                    return@withContext
                }
            }
            
            delay(20) // Fast polling
        }
    }
    
    private fun cleanupAudio() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
        audioRecord = null
        
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recognizer", e)
        }
        recognizer = null
        
        if (_state.value == VoiceState.Listening) {
            _state.value = VoiceState.Ready
        }
    }

    private fun parseVoskResult(json: String): String {
        return try {
            JSONObject(json).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseVoskPartial(json: String): String {
        return try {
            JSONObject(json).optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        isListening.set(false)
        listeningJob?.cancel()
        listeningJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
        }
        audioRecord = null
        
        recognizer?.close()
        recognizer = null
        
        if (_state.value == VoiceState.Listening) {
            _state.value = VoiceState.Ready
        }
        
        Log.d(TAG, "Stopped listening")
    }

    /**
     * Speak text using TTS
     */
    fun speak(text: String) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready")
            return
        }
        
        stopListening() // Stop listening while speaking
        _state.value = VoiceState.Speaking
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        Log.d(TAG, "Speaking: $text")
    }

    /**
     * Stop speaking
     */
    fun stopSpeaking() {
        tts?.stop()
        if (_state.value == VoiceState.Speaking) {
            _state.value = VoiceState.Ready
        }
    }

    /**
     * Check if model is downloaded
     */
    fun isModelDownloaded(): Boolean {
        val modelDir = File(context.filesDir, "vosk-model-small-en-us-0.15")
        return modelDir.exists() && modelDir.isDirectory
    }

    /**
     * Release all resources safely
     */
    fun release() {
        Log.d(TAG, "Releasing voice service...")
        
        // Clear callbacks first
        onFinalResult = null
        onSpeakingDone = null
        
        // Cancel coroutine scope
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling scope", e)
        }
        
        // Stop listening
        try {
            stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
        
        // Stop speaking
        try {
            stopSpeaking()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speaking", e)
        }
        
        // Release Vosk model
        try {
            voskModel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Vosk model", e)
        }
        voskModel = null
        
        // Release TTS
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        ttsReady = false
        
        isInitialized.set(false)
        Log.d(TAG, "Voice service released successfully")
    }
}
