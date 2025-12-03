package com.runanywhere.startup_hackathon20.property

import com.runanywhere.startup_hackathon20.domain.model.IntentType
import com.runanywhere.startup_hackathon20.domain.service.IntentClassifierImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for IntentClassifier.
 * Tests the two-speed routing system: instant actions (<80ms) vs knowledge queries (LLM).
 */
class IntentClassifierPropertyTest : FunSpec({
    
    val classifier = IntentClassifierImpl()
    
    // Generators for instant action inputs
    
    // App names for OPEN_APP pattern
    val arbAppName = Arb.element(
        "camera", "spotify", "calculator", "settings", "maps",
        "chrome", "youtube", "gmail", "calendar", "photos"
    )
    
    // Open app command generator
    val arbOpenAppCommand = arbitrary {
        val prefix = Arb.element("open", "launch", "start").bind()
        val app = arbAppName.bind()
        "$prefix $app"
    }
    
    // Play music command generator
    val arbPlayMusicCommand = arbitrary {
        val variant = Arb.int(0..3).bind()
        when (variant) {
            0 -> "play music"
            1 -> "play some jazz"
            2 -> "play song bohemian rhapsody"
            else -> "play ${Arb.element("rock", "pop", "classical", "jazz").bind()}"
        }
    }
    
    // Timer command generator
    val arbTimerCommand = arbitrary {
        val duration = Arb.int(1..60).bind()
        val unit = Arb.element("minute", "minutes", "second", "seconds", "hour", "hours").bind()
        val variant = Arb.int(0..2).bind()
        when (variant) {
            0 -> "set timer for $duration $unit"
            1 -> "set a timer for $duration $unit"
            else -> "timer $duration $unit"
        }
    }

    
    // Reminder command generator
    val arbReminderCommand = arbitrary {
        val task = Arb.element(
            "pack bags", "call mom", "buy groceries", "take medicine", "check email"
        ).bind()
        val time = Arb.element(
            "8am", "tomorrow", "in 5 minutes", "at noon", "tonight"
        ).bind()
        val variant = Arb.int(0..1).bind()
        when (variant) {
            0 -> "remind me to $task at $time"
            else -> "set reminder for $task at $time"
        }
    }
    
    // Flashlight command generator
    val arbFlashlightCommand = arbitrary {
        val state = Arb.element("on", "off").bind()
        val variant = Arb.int(0..2).bind()
        when (variant) {
            0 -> "turn $state flashlight"
            1 -> "flashlight $state"
            else -> "toggle flashlight"
        }
    }
    
    // Take photo command generator
    val arbTakePhotoCommand = Arb.element(
        "take a photo", "take photo", "take picture", "take a picture",
        "take selfie", "take a selfie", "capture photo"
    )
    
    // Combined instant action generator
    val arbInstantActionCommand = Arb.choice(
        arbOpenAppCommand,
        arbPlayMusicCommand,
        arbTimerCommand,
        arbReminderCommand,
        arbFlashlightCommand,
        arbTakePhotoCommand
    )
    
    // Knowledge query generators
    val arbKnowledgeQueryCommand = arbitrary {
        val queryType = Arb.int(0..6).bind()
        when (queryType) {
            0 -> "what is ${Arb.element("photosynthesis", "gravity", "democracy", "evolution").bind()}?"
            1 -> "how do I ${Arb.element("treat a snake bite", "start a fire", "find water", "build shelter").bind()}?"
            2 -> "why is ${Arb.element("the sky blue", "water wet", "fire hot").bind()}?"
            3 -> "explain ${Arb.element("quantum physics", "machine learning", "climate change").bind()}"
            4 -> "tell me about ${Arb.element("first aid", "survival skills", "navigation").bind()}"
            5 -> "how to ${Arb.element("cook rice", "tie a knot", "read a map").bind()}"
            else -> "what should I do during ${Arb.element("an earthquake", "a flood", "a fire").bind()}?"
        }
    }

    
    /**
     * Feature: offline-voice-assistant, Property 13: Instant Action Classification Consistency
     * 
     * For any input string that matches an instant action pattern (e.g., "open camera", 
     * "play music"), the IntentClassifier should return an IntentType that is marked as 
     * instant action (isInstantAction() returns true).
     * 
     * Validates: Requirements 4.1 (PS3 zero-latency)
     */
    test("Property 13 - Instant Action Classification Consistency") {
        checkAll(100, arbInstantActionCommand) { command ->
            val result = classifier.classify(command)
            
            // The classified intent type should be an instant action
            result.type.isInstantAction() shouldBe true
            
            // isInstantAction helper should also return true
            classifier.isInstantAction(command) shouldBe true
            
            // Confidence should be high for pattern matches
            result.confidence shouldBe 1.0f
        }
    }
    
    /**
     * Feature: offline-voice-assistant, Property 14: Knowledge Query Classification
     * 
     * For any input string that is a question (contains "what", "how", "why", "explain", etc.) 
     * and does NOT match instant action patterns, the IntentClassifier should return 
     * IntentType.KNOWLEDGE_QUERY or IntentType.CONVERSATION.
     * 
     * Validates: Requirements 2.1 (PS1 offline intelligence)
     */
    test("Property 14 - Knowledge Query Classification") {
        checkAll(100, arbKnowledgeQueryCommand) { query ->
            val result = classifier.classify(query)
            
            // Should NOT be classified as an instant action
            result.type.isInstantAction() shouldBe false
            
            // Should be classified as knowledge query or conversation
            (result.type == IntentType.KNOWLEDGE_QUERY || 
             result.type == IntentType.CONVERSATION) shouldBe true
            
            // isInstantAction helper should return false
            classifier.isInstantAction(query) shouldBe false
        }
    }
    
    // Additional unit tests for edge cases
    
    test("Empty input returns UNKNOWN intent") {
        val result = classifier.classify("")
        result.type shouldBe IntentType.UNKNOWN
    }
    
    test("Whitespace-only input returns UNKNOWN intent") {
        val result = classifier.classify("   ")
        result.type shouldBe IntentType.UNKNOWN
    }
    
    test("Open app extracts app name parameter") {
        val result = classifier.classify("open camera")
        result.type shouldBe IntentType.OPEN_APP
        result.extractedParams["appName"] shouldBe "camera"
    }
    
    test("Set timer extracts duration and unit parameters") {
        val result = classifier.classify("set timer for 5 minutes")
        result.type shouldBe IntentType.SET_TIMER
        result.extractedParams["duration"] shouldBe "5"
        result.extractedParams["unit"] shouldBe "minute"
    }
    
    test("Reminder extracts task and time parameters") {
        val result = classifier.classify("remind me to pack bags at 8am")
        result.type shouldBe IntentType.CREATE_REMINDER
        result.extractedParams["task"] shouldBe "pack bags"
        result.extractedParams["time"] shouldBe "8am"
    }
    
    test("Flashlight extracts state parameter") {
        val result = classifier.classify("turn on flashlight")
        result.type shouldBe IntentType.TOGGLE_FLASHLIGHT
        result.extractedParams["state"] shouldBe "on"
    }
    
    test("Question mark indicates knowledge query") {
        val result = classifier.classify("Is it going to rain tomorrow?")
        result.type shouldBe IntentType.KNOWLEDGE_QUERY
    }
})
