package com.runanywhere.startup_hackathon20

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.startup_hackathon20.domain.service.RecordingState
import com.runanywhere.startup_hackathon20.domain.service.TranscriptionResult
import com.runanywhere.startup_hackathon20.domain.service.VoiceInputHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing voice input state.
 * Coordinates with VoiceInputHandler for recording and transcription.
 * 
 * Requirements: 1.1, 1.2, 1.4
 */
class VoiceViewModel(
    private val voiceInputHandler: VoiceInputHandler? = null
) : ViewModel() {
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val _transcriptionResult = MutableStateFlow<TranscriptionResult?>(null)
    val transcriptionResult: StateFlow<TranscriptionResult?> = _transcriptionResult.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var recordedAudioData: ByteArray? = null
    
    // Callback for when transcription is complete
    var onTranscriptionComplete: ((String) -> Unit)? = null
    
    init {
        observeRecordingState()
    }
    
    /**
     * Observes recording state changes from VoiceInputHandler.
     */
    private fun observeRecordingState() {
        viewModelScope.launch {
            voiceInputHandler?.getStateFlow()?.collect { state ->
                _recordingState.value = state
                
                // Handle error states
                if (state is RecordingState.Error) {
                    _errorMessage.value = state.message
                }
            }
        }
    }
    
    /**
     * Starts voice recording.
     * Requirement 1.1: WHEN the user taps the microphone button,
     * THE Translexa app SHALL begin recording audio and display a visual recording indicator
     */
    fun startRecording() {
        if (voiceInputHandler == null) {
            _errorMessage.value = "Voice input not available"
            return
        }
        
        if (voiceInputHandler.isRecording()) {
            return
        }
        
        _errorMessage.value = null
        _transcriptionResult.value = null
        
        viewModelScope.launch {
            voiceInputHandler.startRecording()
        }
    }
    
    /**
     * Stops voice recording and starts transcription.
     * Requirement 1.2: WHEN the user stops speaking or taps the stop button,
     * THE Translexa app SHALL stop recording and process the audio
     */
    fun stopRecording() {
        if (voiceInputHandler == null || !voiceInputHandler.isRecording()) {
            return
        }
        
        viewModelScope.launch {
            // Stop recording and get audio data
            recordedAudioData = voiceInputHandler.stopRecording()
            
            // Transcribe the audio
            if (recordedAudioData != null && recordedAudioData!!.isNotEmpty()) {
                transcribeAudio()
            } else {
                _recordingState.value = RecordingState.Idle
                _errorMessage.value = "No audio recorded"
            }
        }
    }
    
    /**
     * Toggles recording state (start if idle, stop if recording).
     */
    fun toggleRecording() {
        if (isRecording()) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Transcribes the recorded audio.
     * Requirement 1.3: WHEN audio recording completes, THE Translexa app SHALL
     * transcribe the audio to text within 3 seconds for typical utterances
     */
    private suspend fun transcribeAudio() {
        val audioData = recordedAudioData ?: return
        
        try {
            val result = voiceInputHandler?.transcribe(audioData)
            
            if (result != null) {
                _transcriptionResult.value = result
                
                // Requirement 1.4: WHEN transcription completes, THE Translexa app
                // SHALL display the transcribed text in the chat interface
                if (result.text.isNotBlank()) {
                    onTranscriptionComplete?.invoke(result.text)
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Transcription failed: ${e.message}"
            _recordingState.value = RecordingState.Idle
        }
        
        recordedAudioData = null
    }
    
    /**
     * Checks if currently recording.
     */
    fun isRecording(): Boolean {
        return voiceInputHandler?.isRecording() == true
    }
    
    /**
     * Clears the current error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Clears the transcription result.
     */
    fun clearTranscription() {
        _transcriptionResult.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceInputHandler?.release()
    }
}
