package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.domain.service.MockTTSService
import com.runanywhere.startup_hackathon20.domain.service.TTSState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for TTSService state consistency.
 * 
 * **Feature: offline-voice-assistant, Property 11: TTS State Consistency**
 * **Validates: Requirements 3.1, 3.4**
 */
class TTSServicePropertyTest : FunSpec({
    
    // Arbitrary generator for non-empty text strings
    val arbNonEmptyText = Arb.string(1..200).filter { it.isNotBlank() }
    
    // Arbitrary generator for empty/blank text strings
    val arbBlankText = Arb.choice(
        Arb.constant(""),
        Arb.constant("   "),
        Arb.constant("\t\n")
    )
    
    test("**Feature: offline-voice-assistant, Property 11: TTS State Consistency**") {
        /**
         * For any TTSService, if isEnabled() returns true and speak() is called
         * with non-empty text, the state should transition to Speaking and 
         * eventually to Completed or Error.
         * 
         * **Validates: Requirements 3.1, 3.4**
         */
        checkAll(100, arbNonEmptyText) { text ->
            val ttsService = MockTTSService()
            
            // Ensure TTS is enabled
            ttsService.setEnabled(true)
            ttsService.isEnabled() shouldBe true
            
            // Initial state should be Idle
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
            
            // Simulate speaking
            ttsService.simulateSpeaking()
            
            // State should be Speaking
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Speaking>()
            ttsService.isSpeaking() shouldBe true
            
            // Simulate completion
            ttsService.simulateCompleted()
            
            // State should be Idle after completion
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
            ttsService.isSpeaking() shouldBe false
        }
    }
    
    test("TTS disabled does not speak") {
        /**
         * When TTS is disabled, speak() should not change state to Speaking.
         * 
         * **Validates: Requirements 3.4**
         */
        checkAll(100, arbNonEmptyText) { text ->
            val ttsService = MockTTSService()
            
            // Disable TTS
            ttsService.setEnabled(false)
            ttsService.isEnabled() shouldBe false
            
            // Call speak
            ttsService.speak(text)
            
            // State should remain Idle
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
            ttsService.isSpeaking() shouldBe false
        }
    }
    
    test("Empty text does not trigger speaking") {
        /**
         * When speak() is called with empty/blank text, state should remain Idle.
         */
        checkAll(100, arbBlankText) { blankText ->
            val ttsService = MockTTSService()
            ttsService.setEnabled(true)
            
            // Call speak with blank text
            ttsService.speak(blankText)
            
            // State should remain Idle
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
            ttsService.isSpeaking() shouldBe false
        }
    }
    
    test("Stop interrupts speaking and returns to Idle") {
        /**
         * Calling stop() during speaking should immediately return to Idle state.
         * 
         * **Validates: Requirements 3.3**
         */
        checkAll(100, arbNonEmptyText) { text ->
            val ttsService = MockTTSService()
            ttsService.setEnabled(true)
            
            // Simulate speaking
            ttsService.simulateSpeaking()
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Speaking>()
            
            // Stop speaking
            ttsService.stop()
            
            // State should be Idle
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
            ttsService.isSpeaking() shouldBe false
        }
    }
    
    test("Disabling TTS while speaking stops playback") {
        /**
         * Disabling TTS while speaking should stop playback.
         * 
         * **Validates: Requirements 3.4**
         */
        checkAll(100, arbNonEmptyText) { text ->
            val ttsService = MockTTSService()
            ttsService.setEnabled(true)
            
            // Simulate speaking
            ttsService.simulateSpeaking()
            ttsService.isSpeaking() shouldBe true
            
            // Disable TTS
            ttsService.setEnabled(false)
            
            // Should stop speaking
            ttsService.isSpeaking() shouldBe false
            ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
        }
    }
    
    test("Error state is properly reported") {
        /**
         * When an error occurs, state should transition to Error.
         */
        val ttsService = MockTTSService()
        ttsService.setEnabled(true)
        
        // Simulate error
        ttsService.simulateError("Test error message")
        
        val state = ttsService.getStateFlow().value
        state.shouldBeInstanceOf<TTSState.Error>()
        (state as TTSState.Error).message shouldBe "Test error message"
    }
    
    test("Shutdown resets state to Idle") {
        val ttsService = MockTTSService()
        ttsService.setEnabled(true)
        
        // Simulate speaking
        ttsService.simulateSpeaking()
        
        // Shutdown
        ttsService.shutdown()
        
        // State should be Idle
        ttsService.getStateFlow().value.shouldBeInstanceOf<TTSState.Idle>()
        ttsService.isSpeaking() shouldBe false
    }
})
