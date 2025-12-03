package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.domain.service.MockVoiceInputHandler
import com.runanywhere.startup_hackathon20.domain.service.RecordingState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for VoiceInputHandler state transitions.
 * 
 * **Feature: offline-voice-assistant, Property 10: Recording State Transitions**
 * **Validates: Requirements 1.1, 1.2**
 */
class VoiceInputHandlerPropertyTest : FunSpec({
    
    // Arbitrary generator for mock audio data
    val arbAudioData = Arb.byteArray(Arb.int(0..1000), Arb.byte())
    
    // Arbitrary generator for transcription text
    val arbTranscriptionText = Arb.string(1..200).filter { it.isNotBlank() }
    
    test("**Feature: offline-voice-assistant, Property 10: Recording State Transitions**") {
        /**
         * For any VoiceInputHandler in Idle state, calling startRecording() should
         * transition to Recording state, and calling stopRecording() should
         * transition back to Idle or Processing state.
         * 
         * **Validates: Requirements 1.1, 1.2**
         */
        checkAll(100, arbAudioData) { audioData ->
            val handler = MockVoiceInputHandler()
            
            // Initial state should be Idle
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
            handler.isRecording() shouldBe false
            
            // Start recording - should transition to Recording
            handler.startRecording()
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Recording>()
            handler.isRecording() shouldBe true
            
            // Set mock audio data
            handler.setMockAudioData(audioData)
            
            // Stop recording - should transition to Processing then Idle
            val recordedData = handler.stopRecording()
            
            // State should be Processing immediately after stop
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Processing>()
            handler.isRecording() shouldBe false
            
            // Recorded data should match what was set
            recordedData shouldBe audioData
        }
    }
    
    test("Starting recording when already recording has no effect") {
        /**
         * Calling startRecording() when already recording should not change state.
         */
        checkAll(100, Arb.int(1..5)) { attempts ->
            val handler = MockVoiceInputHandler()
            
            // Start recording
            handler.startRecording()
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Recording>()
            
            // Try to start again multiple times
            repeat(attempts) {
                handler.startRecording()
                handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Recording>()
                handler.isRecording() shouldBe true
            }
        }
    }
    
    test("Stopping when not recording returns empty data") {
        /**
         * Calling stopRecording() when not recording should return empty ByteArray.
         */
        checkAll(100, Arb.int(1..5)) { attempts ->
            val handler = MockVoiceInputHandler()
            
            // Initial state is Idle
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
            
            // Try to stop multiple times
            repeat(attempts) {
                val data = handler.stopRecording()
                data shouldBe ByteArray(0)
            }
        }
    }
    
    test("Transcription returns configured result") {
        /**
         * Transcription should return the configured mock result.
         */
        checkAll(100, arbTranscriptionText, Arb.float(0f..1f)) { text, confidence ->
            val handler = MockVoiceInputHandler()
            handler.mockTranscriptionText = text
            handler.mockTranscriptionConfidence = confidence
            
            // Simulate recording flow
            handler.startRecording()
            handler.setMockAudioData(ByteArray(100))
            handler.stopRecording()
            
            // Transcribe
            val result = handler.transcribe(ByteArray(100))
            
            result.text shouldBe text
            result.confidence shouldBe confidence
            
            // State should be Idle after transcription
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
        }
    }
    
    test("Release stops recording and returns to Idle") {
        /**
         * Calling release() should stop any ongoing recording and return to Idle.
         */
        checkAll(100, arbAudioData) { audioData ->
            val handler = MockVoiceInputHandler()
            
            // Start recording
            handler.startRecording()
            handler.setMockAudioData(audioData)
            handler.isRecording() shouldBe true
            
            // Release
            handler.release()
            
            // Should be Idle and not recording
            handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
            handler.isRecording() shouldBe false
        }
    }
    
    test("Error state is properly reported") {
        /**
         * When an error occurs, state should transition to Error.
         */
        val handler = MockVoiceInputHandler()
        
        // Simulate error
        handler.simulateError("Test error message")
        
        val state = handler.getStateFlow().value
        state.shouldBeInstanceOf<RecordingState.Error>()
        (state as RecordingState.Error).message shouldBe "Test error message"
        handler.isRecording() shouldBe false
    }
    
    test("State transitions follow valid sequence") {
        /**
         * Valid state transitions: Idle -> Recording -> Processing -> Idle
         * Or: Any state -> Error
         */
        val handler = MockVoiceInputHandler()
        
        // Idle -> Recording
        handler.simulateIdle()
        handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
        
        handler.simulateRecording()
        handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Recording>()
        
        // Recording -> Processing
        handler.simulateProcessing()
        handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Processing>()
        
        // Processing -> Idle
        handler.simulateIdle()
        handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Idle>()
        
        // Any state -> Error
        handler.simulateRecording()
        handler.simulateError("Error during recording")
        handler.getStateFlow().value.shouldBeInstanceOf<RecordingState.Error>()
    }
})
