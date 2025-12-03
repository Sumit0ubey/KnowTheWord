package com.runanywhere.startup_hackathon20.domain.service

import com.runanywhere.startup_hackathon20.domain.model.IntentType

/**
 * Regex patterns for instant action detection.
 * These patterns enable <80ms response by bypassing LLM entirely.
 * 
 * Requirements: 4.1 (PS3 zero-latency)
 */
object InstantActionPatterns {
    
    /**
     * Patterns for opening apps.
     * Examples: "open camera", "launch spotify", "start calculator"
     */
    val OPEN_APP = listOf(
        Regex("(?i)^open\\s+(.+)$"),
        Regex("(?i)^launch\\s+(.+)$"),
        Regex("(?i)^start\\s+(.+)$")
    )
    
    /**
     * Patterns for playing music.
     * Examples: "play music", "play song bohemian rhapsody", "play some jazz"
     */
    val PLAY_MUSIC = listOf(
        Regex("(?i)^play\\s+music$"),
        Regex("(?i)^play\\s+some\\s+(.+)$"),
        Regex("(?i)^play\\s+song\\s+(.+)$"),
        Regex("(?i)^play\\s+(.+)$")
    )
    
    /**
     * Patterns for setting timers.
     * Examples: "set timer for 5 minutes", "timer 30 seconds", "set a timer for 1 hour"
     */
    val SET_TIMER = listOf(
        Regex("(?i)^set\\s+(?:a\\s+)?timer\\s+(?:for\\s+)?(\\d+)\\s*(minutes?|seconds?|hours?)$"),
        Regex("(?i)^timer\\s+(\\d+)\\s*(minutes?|seconds?|hours?)$"),
        Regex("(?i)^(\\d+)\\s*(minutes?|seconds?|hours?)\\s+timer$")
    )
    
    /**
     * Patterns for creating reminders.
     * Examples: "remind me to pack bags at 8am", "set reminder for meeting tomorrow"
     */
    val CREATE_REMINDER = listOf(
        Regex("(?i)^remind\\s+me\\s+(?:to\\s+)?(.+?)\\s+(?:at|in|on)\\s+(.+)$"),
        Regex("(?i)^set\\s+(?:a\\s+)?reminder\\s+(?:for\\s+)?(.+?)\\s+(?:at|in|on)\\s+(.+)$"),
        Regex("(?i)^set\\s+(?:a\\s+)?reminder\\s+(?:for\\s+)?(.+)$")
    )

    
    /**
     * Patterns for toggling flashlight.
     * Examples: "turn on flashlight", "flashlight off", "toggle torch"
     */
    val TOGGLE_FLASHLIGHT = listOf(
        Regex("(?i)^(?:turn\\s+)?(on|off)\\s+(?:the\\s+)?(?:flashlight|torch)$"),
        Regex("(?i)^(?:flashlight|torch)\\s+(on|off)$"),
        Regex("(?i)^toggle\\s+(?:the\\s+)?(?:flashlight|torch)$")
    )
    
    /**
     * Patterns for taking photos.
     * Examples: "take a photo", "take picture", "take selfie", "open camera"
     */
    val TAKE_PHOTO = listOf(
        Regex("(?i)^take\\s+(?:a\\s+)?(?:photo|picture|selfie)$"),
        Regex("(?i)^capture\\s+(?:a\\s+)?(?:photo|picture|selfie)$")
    )
    
    /**
     * Map of intent types to their corresponding patterns.
     */
    val PATTERN_MAP: Map<IntentType, List<Regex>> = mapOf(
        IntentType.OPEN_APP to OPEN_APP,
        IntentType.PLAY_MUSIC to PLAY_MUSIC,
        IntentType.SET_TIMER to SET_TIMER,
        IntentType.CREATE_REMINDER to CREATE_REMINDER,
        IntentType.TOGGLE_FLASHLIGHT to TOGGLE_FLASHLIGHT,
        IntentType.TAKE_PHOTO to TAKE_PHOTO
    )
    
    /**
     * Question words that indicate a knowledge query.
     */
    val KNOWLEDGE_QUERY_INDICATORS = listOf(
        "what", "how", "why", "when", "where", "who", "which",
        "explain", "describe", "tell me about", "can you explain",
        "help me understand", "what is", "what are", "how do",
        "how to", "why is", "why are", "could you"
    )
}
