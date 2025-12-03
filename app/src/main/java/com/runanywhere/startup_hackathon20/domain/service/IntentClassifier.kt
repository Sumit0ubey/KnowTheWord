package com.runanywhere.startup_hackathon20.domain.service

import android.util.Log
import com.runanywhere.startup_hackathon20.domain.model.ClassificationResult
import com.runanywhere.startup_hackathon20.domain.model.IntentType

/**
 * Interface for classifying user input into intent types.
 */
interface IntentClassifier {
    fun classify(input: String): ClassificationResult
    fun isInstantAction(input: String): Boolean
}

/**
 * Smart Intent Classifier with fuzzy matching.
 * Handles spelling mistakes, synonyms, and understands user intention.
 */
class IntentClassifierImpl : IntentClassifier {
    
    companion object {
        private const val TAG = "IntentClassifier"
        
        // Action keywords - STRICT matching, no loose words
        private val OPEN_KEYWORDS = setOf("open", "launch", "start", "khol")
        private val ON_KEYWORDS = setOf("on", "enable", "activate", "chalu", "jala")
        private val OFF_KEYWORDS = setOf("off", "disable", "deactivate", "band", "bujha")
        private val PLAY_KEYWORDS = setOf("play", "bajao", "sunao")
        private val TAKE_KEYWORDS = setOf("take", "capture", "click", "snap", "shoot", "khicho")
        private val REMIND_KEYWORDS = setOf("remind", "reminder", "yaad")
        
        // Target keywords - STRICT, no ambiguous words like "light"
        private val FLASHLIGHT_KEYWORDS = setOf("flashlight", "torch", "torchlight")
        private val CAMERA_KEYWORDS = setOf("camera")
        private val PHOTO_KEYWORDS = setOf("photo", "picture", "selfie", "pic")
        private val TIMER_KEYWORDS = setOf("timer", "alarm")
        private val MUSIC_KEYWORDS = setOf("music", "song", "songs", "gana", "gaana")
        
        // Common app name corrections
        private val APP_CORRECTIONS = mapOf(
            "whatsap" to "whatsapp", "watsapp" to "whatsapp", "whtsapp" to "whatsapp", "whatapp" to "whatsapp", "whtasapp" to "whatsapp",
            "instagarm" to "instagram", "insta" to "instagram", "instgram" to "instagram", "ig" to "instagram", "insatgram" to "instagram",
            "snapcht" to "snapchat", "snap" to "snapchat", "snpchat" to "snapchat", "snapcht" to "snapchat",
            "youtub" to "youtube", "yt" to "youtube", "utube" to "youtube", "yuotube" to "youtube",
            "gpay" to "google pay", "googlepay" to "google pay", "gpy" to "google pay",
            "fb" to "facebook", "facbook" to "facebook", "facebok" to "facebook",
            "twiter" to "twitter", "x" to "twitter", "twitr" to "twitter",
            "telegarm" to "telegram", "tg" to "telegram", "telgram" to "telegram",
            "setings" to "settings", "setting" to "settings", "seting" to "settings",
            "calender" to "calendar", "calander" to "calendar", "calnedar" to "calendar",
            "calculater" to "calculator", "calc" to "calculator", "calcualtor" to "calculator",
            "mesages" to "messages", "msg" to "messages", "sms" to "messages", "mesage" to "messages",
            "crome" to "chrome", "chorme" to "chrome", "browser" to "chrome", "croem" to "chrome",
            "gmail" to "gmail", "mail" to "gmail", "email" to "gmail", "gmal" to "gmail",
            "maps" to "google maps", "map" to "google maps", "navigation" to "google maps", "gmap" to "google maps",
            "fone" to "phone", "dialer" to "phone", "call" to "phone", "phon" to "phone",
            "galery" to "gallery", "photos" to "gallery", "gallry" to "gallery", "galary" to "gallery",
            "sptoify" to "spotify", "spotfy" to "spotify"
        )
    }
    
    override fun classify(input: String): ClassificationResult {
        val trimmedInput = input.trim().lowercase()
        Log.d(TAG, "Classifying: $trimmedInput")
        
        if (trimmedInput.isEmpty()) {
            return ClassificationResult(IntentType.UNKNOWN, 1.0f, emptyMap())
        }
        
        val words = trimmedInput.split(Regex("\\s+"))
        
        // 1. Check flashlight intent (highest priority)
        val flashlightResult = checkFlashlightIntent(words, trimmedInput)
        if (flashlightResult != null) {
            Log.d(TAG, "Detected FLASHLIGHT intent")
            return flashlightResult
        }
        
        // 2. Check camera/photo intent
        val cameraResult = checkCameraIntent(words, trimmedInput)
        if (cameraResult != null) {
            Log.d(TAG, "Detected CAMERA/PHOTO intent")
            return cameraResult
        }
        
        // 3. Check timer intent
        val timerResult = checkTimerIntent(words, trimmedInput)
        if (timerResult != null) {
            Log.d(TAG, "Detected TIMER intent")
            return timerResult
        }
        
        // 4. Check music intent
        val musicResult = checkMusicIntent(words, trimmedInput)
        if (musicResult != null) {
            Log.d(TAG, "Detected MUSIC intent")
            return musicResult
        }
        
        // 5. Check reminder intent
        val reminderResult = checkReminderIntent(words, trimmedInput)
        if (reminderResult != null) {
            Log.d(TAG, "Detected REMINDER intent")
            return reminderResult
        }
        
        // 6. Check open app intent
        val openAppResult = checkOpenAppIntent(words, trimmedInput)
        if (openAppResult != null) {
            Log.d(TAG, "Detected OPEN_APP intent: ${openAppResult.extractedParams}")
            return openAppResult
        }
        
        // 7. Check knowledge query
        if (isKnowledgeQuery(trimmedInput)) {
            return ClassificationResult(IntentType.KNOWLEDGE_QUERY, 0.9f, mapOf("query" to trimmedInput))
        }
        
        return ClassificationResult(IntentType.CONVERSATION, 0.7f, mapOf("text" to trimmedInput))
    }
    
    override fun isInstantAction(input: String): Boolean {
        val result = classify(input)
        return result.type in listOf(
            IntentType.OPEN_APP, IntentType.TOGGLE_FLASHLIGHT, IntentType.TAKE_PHOTO,
            IntentType.SET_TIMER, IntentType.PLAY_MUSIC, IntentType.CREATE_REMINDER
        )
    }
    
    private fun checkFlashlightIntent(words: List<String>, input: String): ClassificationResult? {
        // STRICT check - must have exact flashlight/torch keyword
        val hasFlashlight = words.any { it in FLASHLIGHT_KEYWORDS } ||
                           input.contains("flashlight") || input.contains("torch")
        
        if (!hasFlashlight) return null
        
        // Determine on/off state
        val hasOn = words.any { it in ON_KEYWORDS } || 
                   input.contains("turn on") || input.contains("switch on")
        val hasOff = words.any { it in OFF_KEYWORDS } || 
                    input.contains("turn off") || input.contains("switch off")
        
        val state = when {
            hasOff -> "off"
            hasOn -> "on"
            else -> "toggle"
        }
        
        return ClassificationResult(IntentType.TOGGLE_FLASHLIGHT, 0.95f, mapOf("state" to state))
    }
    
    private fun checkCameraIntent(words: List<String>, input: String): ClassificationResult? {
        val hasCamera = words.any { it in CAMERA_KEYWORDS } || input.contains("camera")
        val hasPhoto = words.any { it in PHOTO_KEYWORDS }
        val hasTake = words.any { it in TAKE_KEYWORDS }
        val hasOpen = words.any { it in OPEN_KEYWORDS }
        
        // "take photo", "click picture", "capture selfie"
        if (hasTake && (hasPhoto || hasCamera)) {
            return ClassificationResult(IntentType.TAKE_PHOTO, 0.95f, emptyMap())
        }
        
        // "open camera"
        if (hasOpen && hasCamera) {
            return ClassificationResult(IntentType.OPEN_APP, 0.95f, mapOf("appName" to "camera"))
        }
        
        return null
    }
    
    private fun checkTimerIntent(words: List<String>, input: String): ClassificationResult? {
        // Must have explicit timer/alarm keyword
        val hasTimer = words.any { it in TIMER_KEYWORDS } || 
                      input.contains("timer") || input.contains("alarm")
        if (!hasTimer) return null
        
        // Extract duration - must have a number
        val durationRegex = Regex("(\\d+)\\s*(min|minute|minutes|sec|second|seconds|hour|hours|hr|hrs)")
        val match = durationRegex.find(input)
        
        if (match != null) {
            val duration = match.groupValues[1]
            val unitRaw = match.groupValues[2].lowercase()
            val unit = when {
                unitRaw.startsWith("min") -> "minute"
                unitRaw.startsWith("sec") -> "second"
                unitRaw.startsWith("h") -> "hour"
                else -> "minute"
            }
            return ClassificationResult(IntentType.SET_TIMER, 0.95f, mapOf("duration" to duration, "unit" to unit))
        }
        
        return null
    }
    
    private fun checkMusicIntent(words: List<String>, input: String): ClassificationResult? {
        val hasPlay = words.any { it in PLAY_KEYWORDS } || input.contains("play")
        val hasMusic = words.any { it in MUSIC_KEYWORDS } || 
                      input.contains("music") || input.contains("song")
        
        // Must have both play AND music/song
        if (hasPlay && hasMusic) {
            val query = input.replace(Regex("(?i)(play|start)\\s*(some|a|the)?\\s*(music|song|songs)?\\s*"), "").trim()
            return ClassificationResult(IntentType.PLAY_MUSIC, 0.9f, mapOf("query" to query))
        }
        
        return null
    }
    
    private fun checkReminderIntent(words: List<String>, input: String): ClassificationResult? {
        // Must have explicit remind/reminder keyword
        val hasRemind = words.any { it in REMIND_KEYWORDS } || 
                       input.contains("remind") || input.contains("reminder")
        if (!hasRemind) return null
        
        val timeRegex = Regex("(?i)(at|in|on|after)\\s+(.+)$")
        val timeMatch = timeRegex.find(input)
        
        val task = input.replace(Regex("(?i)(remind|reminder)\\s*(me)?\\s*(to|about)?\\s*"), "")
            .replace(timeRegex, "").trim()
        val time = timeMatch?.groupValues?.getOrNull(2)?.trim() ?: ""
        
        if (task.isNotEmpty()) {
            return ClassificationResult(IntentType.CREATE_REMINDER, 0.9f, 
                if (time.isNotEmpty()) mapOf("task" to task, "time" to time) else mapOf("task" to task))
        }
        
        return null
    }
    
    private fun checkOpenAppIntent(words: List<String>, input: String): ClassificationResult? {
        // Must have explicit "open" or "launch" keyword
        val hasOpen = words.any { it in OPEN_KEYWORDS }
        
        if (hasOpen) {
            // Extract app name - everything after open keyword
            var appName = input
            for (keyword in OPEN_KEYWORDS) {
                appName = appName.replace(Regex("(?i)\\b$keyword\\b\\s*"), "")
            }
            appName = appName.replace(Regex("(?i)^(the|a|an)\\s+"), "").trim()
            
            // Apply corrections for common misspellings
            appName = APP_CORRECTIONS[appName] ?: appName
            
            if (appName.isNotEmpty() && appName.length >= 2) {
                return ClassificationResult(IntentType.OPEN_APP, 0.9f, mapOf("appName" to appName))
            }
        }
        
        // Only match exact app name shortcuts (no fuzzy)
        val correctedApp = APP_CORRECTIONS[input]
        if (correctedApp != null) {
            return ClassificationResult(IntentType.OPEN_APP, 0.8f, mapOf("appName" to correctedApp))
        }
        
        return null
    }
    
    private fun isKnowledgeQuery(input: String): Boolean {
        if (input.endsWith("?")) return true
        return InstantActionPatterns.KNOWLEDGE_QUERY_INDICATORS.any { 
            input.startsWith(it) || input.contains(" $it ") 
        }
    }
    
    /**
     * Strict fuzzy match - only allows 1 character difference for longer words
     */
    private fun fuzzyMatch(word: String, keywords: Set<String>): Boolean {
        // Exact match first
        if (word in keywords) return true
        
        // Only fuzzy match for words longer than 5 chars, max 1 char difference
        if (word.length >= 5) {
            for (keyword in keywords) {
                if (keyword.length >= 5 && levenshteinDistance(word, keyword) == 1) {
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
