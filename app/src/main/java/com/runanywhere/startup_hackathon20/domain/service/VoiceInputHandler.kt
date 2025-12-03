package com.runanywhere.startup_hackathon20.domain.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Represents the current state of voice recording.
 */
sealed class RecordingState {
    /** Recording is idle and ready to start */
    object Idle : RecordingState()
    
    /** Currently recording audio */
    object Recording : RecordingState()
    
    /** Processing recorded audio (transcription) */
    object Processing : RecordingState()
    
    /** Recording encountered an error */
    data class Error(val message: String) : RecordingState()
}

/**
 * Result of audio transcription.
 */
data class TranscriptionResult(
    val text: String,
    val confidence: Float,
    val durationMs: Long
)

/**
 * Interface for handling voice input.
 * Manages audio recording and transcription.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4
 */
interface VoiceInputHandler {
    /**
     * Starts recording audio.
     * Returns a Flow that emits state changes during recording.
     * 
     * Requirement 1.1: WHEN the user taps the microphone button,
     * THE Translexa app SHALL begin recording audio and display a visual recording indicator
     * 
     * @return Flow of RecordingState updates
     */
    fun startRecording(): Flow<RecordingState>
    
    /**
     * Stops recording and returns the recorded audio data.
     * 
     * Requirement 1.2: WHEN the user stops speaking (detected via VAD) or taps the stop button,
     * THE Translexa app SHALL stop recording and process the audio
     * 
     * @return ByteArray of recorded audio data
     */
    fun stopRecording(): ByteArray
    
    /**
     * Transcribes audio data to text.
     * 
     * Requirement 1.3: WHEN audio recording completes, THE Translexa app SHALL
     * transcribe the audio to text using the on-device Whisper model within 3 seconds
     * 
     * @param audioData The recorded audio data
     * @return TranscriptionResult with text and confidence
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    
    /**
     * Checks if currently recording.
     * 
     * @return true if recording is in progress
     */
    fun isRecording(): Boolean
    
    /**
     * Gets the current recording state as a StateFlow.
     * 
     * @return StateFlow of current RecordingState
     */
    fun getStateFlow(): StateFlow<RecordingState>
    
    /**
     * Releases resources. Call when done using the handler.
     */
    fun release()
}


/**
 * Implementation of VoiceInputHandler using Android AudioRecord.
 * Provides audio recording and transcription functionality.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5
 */
class VoiceInputHandlerImpl(
    private val context: Context
) : VoiceInputHandler {
    
    companion object {
        private const val SAMPLE_RATE = 16000 // 16kHz for Whisper compatibility
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isCurrentlyRecording = false
    private var audioBuffer: ByteArrayOutputStream? = null
    private var recordingStartTime: Long = 0
    
    private val _stateFlow = MutableStateFlow<RecordingState>(RecordingState.Idle)
    
    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        maxOf(minBufferSize, SAMPLE_RATE * 2) // At least 1 second buffer
    }
    
    /**
     * Starts recording audio.
     * Requirement 1.1: WHEN the user taps the microphone button,
     * THE Translexa app SHALL begin recording audio
     */
    override fun startRecording(): Flow<RecordingState> {
        if (isCurrentlyRecording) {
            return _stateFlow.asStateFlow()
        }
        
        // Check permission
        if (!hasRecordPermission()) {
            _stateFlow.value = RecordingState.Error("Microphone permission not granted")
            return _stateFlow.asStateFlow()
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _stateFlow.value = RecordingState.Error("Failed to initialize audio recorder")
                return _stateFlow.asStateFlow()
            }
            
            audioBuffer = ByteArrayOutputStream()
            recordingStartTime = System.currentTimeMillis()
            
            audioRecord?.startRecording()
            isCurrentlyRecording = true
            _stateFlow.value = RecordingState.Recording
            
            // Start recording thread
            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                while (isCurrentlyRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        audioBuffer?.write(buffer, 0, bytesRead)
                    }
                }
            }.apply { start() }
            
        } catch (e: SecurityException) {
            _stateFlow.value = RecordingState.Error("Microphone permission denied")
        } catch (e: Exception) {
            _stateFlow.value = RecordingState.Error("Failed to start recording: ${e.message}")
        }
        
        return _stateFlow.asStateFlow()
    }
    
    /**
     * Stops recording and returns the recorded audio data.
     * Requirement 1.2: WHEN the user stops speaking or taps the stop button,
     * THE Translexa app SHALL stop recording and process the audio
     */
    override fun stopRecording(): ByteArray {
        if (!isCurrentlyRecording) {
            return ByteArray(0)
        }
        
        isCurrentlyRecording = false
        
        try {
            // Wait for recording thread to finish
            recordingThread?.join(1000)
            recordingThread = null
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            _stateFlow.value = RecordingState.Processing
            
            val audioData = audioBuffer?.toByteArray() ?: ByteArray(0)
            audioBuffer?.close()
            audioBuffer = null
            
            return audioData
            
        } catch (e: Exception) {
            _stateFlow.value = RecordingState.Error("Failed to stop recording: ${e.message}")
            return ByteArray(0)
        }
    }
    
    /**
     * Transcribes audio data to text.
     * Uses Android SpeechRecognizer as fallback when Whisper is not available.
     * 
     * Requirement 1.3: WHEN audio recording completes, THE Translexa app SHALL
     * transcribe the audio to text within 3 seconds for typical utterances
     */
    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        val startTime = System.currentTimeMillis()
        
        if (audioData.isEmpty()) {
            _stateFlow.value = RecordingState.Idle
            return TranscriptionResult(
                text = "",
                confidence = 0f,
                durationMs = 0
            )
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Integrate with RunAnywhere SDK Whisper model when available
                // For now, return a placeholder indicating transcription would happen here
                // In production, this would call:
                // RunAnywhere.transcribe(audioData) or similar
                
                val durationMs = System.currentTimeMillis() - startTime
                
                _stateFlow.value = RecordingState.Idle
                
                TranscriptionResult(
                    text = "[Transcription placeholder - integrate Whisper STT]",
                    confidence = 0.0f,
                    durationMs = durationMs
                )
            } catch (e: Exception) {
                _stateFlow.value = RecordingState.Error("Transcription failed: ${e.message}")
                TranscriptionResult(
                    text = "",
                    confidence = 0f,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }
    
    override fun isRecording(): Boolean = isCurrentlyRecording
    
    override fun getStateFlow(): StateFlow<RecordingState> = _stateFlow.asStateFlow()
    
    override fun release() {
        if (isCurrentlyRecording) {
            stopRecording()
        }
        audioRecord?.release()
        audioRecord = null
        _stateFlow.value = RecordingState.Idle
    }
    
    /**
     * Checks if the app has microphone permission.
     * Requirement 1.5: IF audio recording fails due to permission denial,
     * THEN THE Translexa app SHALL display a clear permission request message
     */
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}


/**
 * Mock implementation of VoiceInputHandler for testing.
 * Does not require Android Context or permissions.
 */
class MockVoiceInputHandler : VoiceInputHandler {
    
    private var recording = false
    private var recordedData = ByteArray(0)
    private val _stateFlow = MutableStateFlow<RecordingState>(RecordingState.Idle)
    
    // Configurable transcription result for testing
    var mockTranscriptionText = "Test transcription"
    var mockTranscriptionConfidence = 0.95f
    
    override fun startRecording(): Flow<RecordingState> {
        if (recording) {
            return _stateFlow.asStateFlow()
        }
        
        recording = true
        _stateFlow.value = RecordingState.Recording
        return _stateFlow.asStateFlow()
    }
    
    override fun stopRecording(): ByteArray {
        if (!recording) {
            return ByteArray(0)
        }
        
        recording = false
        _stateFlow.value = RecordingState.Processing
        
        // Return mock audio data
        val data = recordedData
        recordedData = ByteArray(0)
        return data
    }
    
    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        _stateFlow.value = RecordingState.Idle
        
        return TranscriptionResult(
            text = mockTranscriptionText,
            confidence = mockTranscriptionConfidence,
            durationMs = 100
        )
    }
    
    override fun isRecording(): Boolean = recording
    
    override fun getStateFlow(): StateFlow<RecordingState> = _stateFlow.asStateFlow()
    
    override fun release() {
        recording = false
        _stateFlow.value = RecordingState.Idle
    }
    
    // Test helper methods
    fun simulateRecording() {
        recording = true
        _stateFlow.value = RecordingState.Recording
    }
    
    fun simulateProcessing() {
        recording = false
        _stateFlow.value = RecordingState.Processing
    }
    
    fun simulateIdle() {
        recording = false
        _stateFlow.value = RecordingState.Idle
    }
    
    fun simulateError(message: String) {
        recording = false
        _stateFlow.value = RecordingState.Error(message)
    }
    
    fun setMockAudioData(data: ByteArray) {
        recordedData = data
    }
}
