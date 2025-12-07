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

    /**
     * Async classification that can use AI for complex cases like reminders.
     * Falls back to synchronous classify if AI is not available.
     */
    suspend fun classifyWithAI(input: String): ClassificationResult

    /**
     * Quick check if input might be a reminder request.
     */
    fun mightBeReminder(input: String): Boolean
}

/**
 * Smart Intent Classifier with fuzzy matching and AI support.
 * Handles spelling mistakes, synonyms, and understands user intention.
 * Now supports AI-powered reminder detection for natural language inputs.
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
        private val REMIND_KEYWORDS = setOf("remind", "reminder", "yaad", "batana", "bata")

        // Extended reminder keywords for Hindi/Hinglish
        private val EXTENDED_REMIND_KEYWORDS = setOf(
            "remind", "reminder", "remember", "alert me", "notify",
            "yaad", "yaad dilana", "yaad karna", "batana", "bata dena",
            "bhool na", "bhulna mat", "don't forget"
        )

        // Time indicators for reminder detection
        private val TIME_INDICATORS = setOf(
            "today", "tomorrow", "kal", "parso", "aaj",
            "morning", "afternoon", "evening", "night",
            "subah", "dopahar", "shaam", "raat",
            "am", "pm", "baje", "o'clock",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "somvar", "mangalvar", "budhvar", "guruvar", "shukravar", "shanivar", "ravivar",
            "next week", "hafta", "at", "on"
        )
        
        // Target keywords - STRICT, no ambiguous words like "light"
        private val FLASHLIGHT_KEYWORDS = setOf("flashlight", "torch", "torchlight")
        private val CAMERA_KEYWORDS = setOf("camera")
        private val PHOTO_KEYWORDS = setOf("photo", "picture", "selfie", "pic")
        private val TIMER_KEYWORDS = setOf("timer", "alarm")
        private val MUSIC_KEYWORDS = setOf("music", "song", "songs", "gana", "gaana")
        
        // ===== NEW: System Control Keywords =====

        // Volume keywords
        private val VOLUME_UP_KEYWORDS = setOf(
            "volume up",
            "increase volume",
            "louder",
            "raise volume",
            "volume badhao",
            "awaz badhao"
        )
        private val VOLUME_DOWN_KEYWORDS = setOf(
            "volume down",
            "decrease volume",
            "lower volume",
            "quieter",
            "volume kam",
            "awaz kam"
        )
        private val VOLUME_MUTE_KEYWORDS = setOf("mute", "silent", "silence", "chup", "mute karo")
        private val VOLUME_MAX_KEYWORDS =
            setOf("max volume", "full volume", "maximum volume", "volume full")

        // Brightness keywords
        private val BRIGHTNESS_UP_KEYWORDS = setOf(
            "brightness up",
            "increase brightness",
            "brighter",
            "more bright",
            "brightness badhao"
        )
        private val BRIGHTNESS_DOWN_KEYWORDS = setOf(
            "brightness down",
            "decrease brightness",
            "dimmer",
            "dim",
            "less bright",
            "brightness kam"
        )
        private val BRIGHTNESS_MAX_KEYWORDS =
            setOf("max brightness", "full brightness", "maximum brightness")
        private val BRIGHTNESS_AUTO_KEYWORDS =
            setOf("auto brightness", "automatic brightness", "adaptive brightness")

        // WiFi keywords
        private val WIFI_KEYWORDS = setOf("wifi", "wi-fi", "wi fi", "wireless")

        // Bluetooth keywords  
        private val BLUETOOTH_KEYWORDS = setOf("bluetooth", "bt")

        // Mobile data keywords
        private val MOBILE_DATA_KEYWORDS = setOf("mobile data", "data", "internet", "cellular")

        // Airplane mode keywords
        private val AIRPLANE_KEYWORDS =
            setOf("airplane mode", "flight mode", "aeroplane mode", "airplane")

        // DND keywords
        private val DND_KEYWORDS = setOf("do not disturb", "dnd", "don't disturb", "silent mode")

        // Hotspot keywords
        private val HOTSPOT_KEYWORDS = setOf("hotspot", "tethering", "portable hotspot")

        // Location/GPS keywords
        private val LOCATION_KEYWORDS = setOf("location", "gps", "location services")

        // Settings navigation keywords
        private val SETTINGS_WIFI_KEYWORDS =
            setOf("wifi settings", "wi-fi settings", "wireless settings")
        private val SETTINGS_BLUETOOTH_KEYWORDS = setOf("bluetooth settings")
        private val SETTINGS_DISPLAY_KEYWORDS = setOf("display settings", "screen settings")
        private val SETTINGS_SOUND_KEYWORDS =
            setOf("sound settings", "audio settings", "volume settings")
        private val SETTINGS_BATTERY_KEYWORDS = setOf("battery settings", "power settings")
        private val SETTINGS_STORAGE_KEYWORDS = setOf("storage settings", "memory settings")
        private val SETTINGS_APP_KEYWORDS =
            setOf("app settings", "apps settings", "manage apps", "application settings")
        private val SETTINGS_NOTIFICATION_KEYWORDS = setOf("notification settings", "notifications")

        // Gallery/Photos keywords
        private val GALLERY_KEYWORDS =
            setOf("gallery", "photos", "pictures", "images", "photo gallery")
        private val RECENT_PHOTOS_KEYWORDS =
            setOf("recent photos", "recent pictures", "latest photos", "new photos")

        // File keywords
        private val DOWNLOADS_KEYWORDS = setOf("downloads", "download folder", "downloaded files")
        private val DOCUMENTS_KEYWORDS = setOf("documents", "my documents", "files")
        private val FILE_MANAGER_KEYWORDS =
            setOf("file manager", "files", "my files", "file explorer")

        // Search keywords
        private val SEARCH_WEB_KEYWORDS = setOf("search", "google", "search for", "look up", "find")
        private val SEARCH_YOUTUBE_KEYWORDS =
            setOf("youtube", "search youtube", "on youtube", "search on youtube")
        private val SEARCH_MAPS_KEYWORDS = setOf("maps", "directions", "navigate", "find location")

        // Device info keywords
        private val BATTERY_INFO_KEYWORDS = setOf(
            "battery level",
            "battery percentage",
            "how much battery",
            "battery status",
            "battery kitni",
            "kitni battery"
        )
        private val STORAGE_INFO_KEYWORDS = setOf(
            "storage",
            "storage info",
            "how much storage",
            "storage space",
            "kitni storage",
            "kitni space"
        )
        private val TIME_KEYWORDS = setOf("what time", "current time", "time kya", "kitne baje")
        private val DATE_KEYWORDS =
            setOf("what date", "today's date", "what day", "aaj kya date", "aaj kaunsa din")

        // Screen actions
        private val SCREENSHOT_KEYWORDS =
            setOf("screenshot", "screen capture", "take screenshot", "capture screen")
        private val SCREEN_RECORD_KEYWORDS =
            setOf("screen record", "record screen", "screen recording")

        // Quick action keywords
        private val CALCULATOR_KEYWORDS = setOf("calculator", "calc", "calculate")
        private val CALENDAR_KEYWORDS = setOf("calendar", "schedule", "appointments")
        private val CONTACTS_KEYWORDS = setOf("contacts", "phonebook", "address book")
        private val CLOCK_KEYWORDS = setOf("clock", "alarms", "timer app")
        private val NOTES_KEYWORDS = setOf("notes", "note", "notepad", "keep")
        private val QR_KEYWORDS = setOf("qr", "qr code", "scan qr", "barcode", "scan code")

        // Communication keywords
        private val CALL_KEYWORDS = setOf("call", "dial", "phone", "ring")
        private val SMS_KEYWORDS = setOf("message", "sms", "text", "send message")
        private val WHATSAPP_KEYWORDS = setOf("whatsapp", "wa", "whats app")
        
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
        
        // ===== INSTANT SYSTEM CONTROLS (Highest Priority) =====

        // 1. Check volume controls
        val volumeResult = checkVolumeIntent(trimmedInput)
        if (volumeResult != null) {
            Log.d(TAG, "Detected VOLUME intent: ${volumeResult.type}")
            return volumeResult
        }

        // 2. Check brightness controls
        val brightnessResult = checkBrightnessIntent(trimmedInput)
        if (brightnessResult != null) {
            Log.d(TAG, "Detected BRIGHTNESS intent: ${brightnessResult.type}")
            return brightnessResult
        }

        // 3. Check system toggles (WiFi, Bluetooth, etc.)
        val toggleResult = checkSystemToggleIntent(words, trimmedInput)
        if (toggleResult != null) {
            Log.d(TAG, "Detected TOGGLE intent: ${toggleResult.type}")
            return toggleResult
        }

        // 4. Check flashlight intent
        val flashlightResult = checkFlashlightIntent(words, trimmedInput)
        if (flashlightResult != null) {
            Log.d(TAG, "Detected FLASHLIGHT intent")
            return flashlightResult
        }

        // 5. Check settings navigation
        val settingsResult = checkSettingsIntent(trimmedInput)
        if (settingsResult != null) {
            Log.d(TAG, "Detected SETTINGS intent: ${settingsResult.type}")
            return settingsResult
        }

        // 6. Check device info queries (battery, storage, time, date)
        val deviceInfoResult = checkDeviceInfoIntent(trimmedInput)
        if (deviceInfoResult != null) {
            Log.d(TAG, "Detected DEVICE_INFO intent: ${deviceInfoResult.type}")
            return deviceInfoResult
        }

        // 7. Check gallery/files intent
        val filesResult = checkFilesIntent(trimmedInput)
        if (filesResult != null) {
            Log.d(TAG, "Detected FILES intent: ${filesResult.type}")
            return filesResult
        }

        // 8. Check search intents
        val searchResult = checkSearchIntent(trimmedInput)
        if (searchResult != null) {
            Log.d(TAG, "Detected SEARCH intent: ${searchResult.type}")
            return searchResult
        }

        // 9. Check quick action intents (calculator, calendar, etc.)
        val quickActionResult = checkQuickActionIntent(trimmedInput)
        if (quickActionResult != null) {
            Log.d(TAG, "Detected QUICK_ACTION intent: ${quickActionResult.type}")
            return quickActionResult
        }

        // 10. Check communication intents (call, sms, whatsapp)
        val communicationResult = checkCommunicationIntent(trimmedInput)
        if (communicationResult != null) {
            Log.d(TAG, "Detected COMMUNICATION intent: ${communicationResult.type}")
            return communicationResult
        }

        // 11. Check screen actions (screenshot, record)
        val screenResult = checkScreenActionIntent(trimmedInput)
        if (screenResult != null) {
            Log.d(TAG, "Detected SCREEN intent: ${screenResult.type}")
            return screenResult
        }

        // ===== ORIGINAL INTENTS =====

        // 12. Check camera/photo intent
        val cameraResult = checkCameraIntent(words, trimmedInput)
        if (cameraResult != null) {
            Log.d(TAG, "Detected CAMERA/PHOTO intent")
            return cameraResult
        }

        // 13. Check timer intent
        val timerResult = checkTimerIntent(words, trimmedInput)
        if (timerResult != null) {
            Log.d(TAG, "Detected TIMER intent")
            return timerResult
        }

        // 14. Check music intent
        val musicResult = checkMusicIntent(words, trimmedInput)
        if (musicResult != null) {
            Log.d(TAG, "Detected MUSIC intent")
            return musicResult
        }

        // 15. Check reminder intent
        val reminderResult = checkReminderIntent(words, trimmedInput)
        if (reminderResult != null) {
            Log.d(TAG, "Detected REMINDER intent")
            return reminderResult
        }

        // 16. Check open app intent
        val openAppResult = checkOpenAppIntent(words, trimmedInput)
        if (openAppResult != null) {
            Log.d(TAG, "Detected OPEN_APP intent: ${openAppResult.extractedParams}")
            return openAppResult
        }

        // 17. Check knowledge query
        if (isKnowledgeQuery(trimmedInput)) {
            return ClassificationResult(
                IntentType.KNOWLEDGE_QUERY,
                0.9f,
                mapOf("query" to trimmedInput)
            )
        }

        return ClassificationResult(IntentType.CONVERSATION, 0.7f, mapOf("text" to trimmedInput))
    }

    // ===== NEW CHECK FUNCTIONS =====

    private fun checkVolumeIntent(input: String): ClassificationResult? {
        return when {
            VOLUME_MAX_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.VOLUME_MAX, 0.95f, emptyMap())

            VOLUME_MUTE_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.VOLUME_MUTE, 0.95f, emptyMap())

            VOLUME_UP_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.VOLUME_UP, 0.95f, emptyMap())

            VOLUME_DOWN_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.VOLUME_DOWN, 0.95f, emptyMap())

            else -> null
        }
    }

    private fun checkBrightnessIntent(input: String): ClassificationResult? {
        return when {
            BRIGHTNESS_AUTO_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.BRIGHTNESS_AUTO, 0.95f, emptyMap())

            BRIGHTNESS_MAX_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.BRIGHTNESS_MAX, 0.95f, emptyMap())

            BRIGHTNESS_UP_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.BRIGHTNESS_UP, 0.95f, emptyMap())

            BRIGHTNESS_DOWN_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.BRIGHTNESS_DOWN, 0.95f, emptyMap())

            else -> null
        }
    }

    private fun checkSystemToggleIntent(words: List<String>, input: String): ClassificationResult? {
        val hasOn =
            words.any { it in ON_KEYWORDS } || input.contains("turn on") || input.contains("enable") || input.contains(
                "switch on"
            )
        val hasOff =
            words.any { it in OFF_KEYWORDS } || input.contains("turn off") || input.contains("disable") || input.contains(
                "switch off"
            )
        val state = when {
            hasOff -> "off"
            hasOn -> "on"
            else -> "toggle"
        }

        return when {
            WIFI_KEYWORDS.any { input.contains(it) } && (hasOn || hasOff || input.contains("toggle")) ->
                ClassificationResult(IntentType.TOGGLE_WIFI, 0.95f, mapOf("state" to state))

            BLUETOOTH_KEYWORDS.any { input.contains(it) } && (hasOn || hasOff || input.contains("toggle")) ->
                ClassificationResult(IntentType.TOGGLE_BLUETOOTH, 0.95f, mapOf("state" to state))

            MOBILE_DATA_KEYWORDS.any { input.contains(it) } && (hasOn || hasOff) ->
                ClassificationResult(IntentType.TOGGLE_MOBILE_DATA, 0.95f, mapOf("state" to state))

            AIRPLANE_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(
                    IntentType.TOGGLE_AIRPLANE_MODE,
                    0.95f,
                    mapOf("state" to state)
                )

            DND_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.TOGGLE_DND, 0.95f, mapOf("state" to state))

            HOTSPOT_KEYWORDS.any { input.contains(it) } && (hasOn || hasOff) ->
                ClassificationResult(IntentType.TOGGLE_HOTSPOT, 0.95f, mapOf("state" to state))

            LOCATION_KEYWORDS.any { input.contains(it) } && (hasOn || hasOff) ->
                ClassificationResult(IntentType.TOGGLE_LOCATION, 0.95f, mapOf("state" to state))

            else -> null
        }
    }

    private fun checkSettingsIntent(input: String): ClassificationResult? {
        val hasOpen =
            OPEN_KEYWORDS.any { input.contains(it) } || input.contains("go to") || input.contains("show")

        return when {
            SETTINGS_WIFI_KEYWORDS.any { input.contains(it) } || (hasOpen && WIFI_KEYWORDS.any {
                input.contains(
                    it
                )
            } && input.contains("setting")) ->
                ClassificationResult(IntentType.OPEN_WIFI_SETTINGS, 0.95f, emptyMap())

            SETTINGS_BLUETOOTH_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_BLUETOOTH_SETTINGS, 0.95f, emptyMap())

            SETTINGS_DISPLAY_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_DISPLAY_SETTINGS, 0.95f, emptyMap())

            SETTINGS_SOUND_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_SOUND_SETTINGS, 0.95f, emptyMap())

            SETTINGS_BATTERY_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_BATTERY_SETTINGS, 0.95f, emptyMap())

            SETTINGS_STORAGE_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_STORAGE_SETTINGS, 0.95f, emptyMap())

            SETTINGS_APP_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_APP_SETTINGS, 0.95f, emptyMap())

            SETTINGS_NOTIFICATION_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_NOTIFICATION_SETTINGS, 0.95f, emptyMap())

            hasOpen && input.contains("setting") ->
                ClassificationResult(IntentType.OPEN_SETTINGS, 0.9f, emptyMap())

            else -> null
        }
    }

    private fun checkDeviceInfoIntent(input: String): ClassificationResult? {
        return when {
            BATTERY_INFO_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SHOW_BATTERY_LEVEL, 0.95f, emptyMap())

            STORAGE_INFO_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SHOW_STORAGE_INFO, 0.95f, emptyMap())

            TIME_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SHOW_TIME, 0.95f, emptyMap())

            DATE_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SHOW_DATE, 0.95f, emptyMap())

            else -> null
        }
    }

    private fun checkFilesIntent(input: String): ClassificationResult? {
        val hasOpen = OPEN_KEYWORDS.any { input.contains(it) } || input.contains("show")

        return when {
            RECENT_PHOTOS_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SHOW_RECENT_PHOTOS, 0.95f, emptyMap())

            hasOpen && GALLERY_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_GALLERY, 0.95f, emptyMap())

            hasOpen && DOWNLOADS_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_DOWNLOADS, 0.95f, emptyMap())

            hasOpen && DOCUMENTS_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_DOCUMENTS, 0.95f, emptyMap())

            hasOpen && FILE_MANAGER_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_FILE_MANAGER, 0.95f, emptyMap())

            else -> null
        }
    }

    private fun checkSearchIntent(input: String): ClassificationResult? {
        // Extract search query
        fun extractQuery(input: String, keywords: Collection<String>): String {
            var query = input
            for (kw in keywords) {
                query = query.replace(kw, "")
            }
            query = query.replace(Regex("\\b(for|on|in|the|a|an)\\b"), "").trim()
            return query
        }

        return when {
            SEARCH_YOUTUBE_KEYWORDS.any { input.contains(it) } -> {
                val query = extractQuery(input, SEARCH_YOUTUBE_KEYWORDS)
                ClassificationResult(IntentType.SEARCH_YOUTUBE, 0.95f, mapOf("query" to query))
            }

            SEARCH_MAPS_KEYWORDS.any { input.contains(it) } -> {
                val query = extractQuery(input, SEARCH_MAPS_KEYWORDS)
                ClassificationResult(IntentType.SEARCH_MAPS, 0.95f, mapOf("query" to query))
            }

            input.contains("search contact") || input.contains("find contact") -> {
                val query = input.replace(Regex("(?i)(search|find)\\s+contact(s)?\\s*"), "").trim()
                ClassificationResult(IntentType.SEARCH_CONTACTS, 0.95f, mapOf("query" to query))
            }

            SEARCH_WEB_KEYWORDS.any { input.startsWith(it) } || input.contains("search for") -> {
                val query = extractQuery(input, SEARCH_WEB_KEYWORDS)
                ClassificationResult(IntentType.SEARCH_WEB, 0.9f, mapOf("query" to query))
            }

            else -> null
        }
    }

    private fun checkQuickActionIntent(input: String): ClassificationResult? {
        val hasOpen = OPEN_KEYWORDS.any { input.contains(it) }

        return when {
            hasOpen && CALCULATOR_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_CALCULATOR, 0.95f, emptyMap())

            hasOpen && CALENDAR_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_CALENDAR, 0.95f, emptyMap())

            hasOpen && CONTACTS_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_CONTACTS, 0.95f, emptyMap())

            hasOpen && CLOCK_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_CLOCK, 0.95f, emptyMap())

            hasOpen && NOTES_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.OPEN_NOTES, 0.95f, emptyMap())

            QR_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SCAN_QR, 0.95f, emptyMap())

            else -> null
        }
    }

    private fun checkCommunicationIntent(input: String): ClassificationResult? {
        return when {
            WHATSAPP_KEYWORDS.any { input.contains(it) } && (input.contains("send") || input.contains(
                "message"
            )) -> {
                val contact =
                    input.replace(Regex("(?i)(send|message|to|whatsapp|wa|whats app)"), "").trim()
                ClassificationResult(IntentType.SEND_WHATSAPP, 0.95f, mapOf("contact" to contact))
            }

            CALL_KEYWORDS.any { input.startsWith(it) } -> {
                val contact = input.replace(Regex("(?i)^(call|dial|phone|ring)\\s*"), "").trim()
                ClassificationResult(IntentType.CALL_CONTACT, 0.95f, mapOf("contact" to contact))
            }

            SMS_KEYWORDS.any { input.contains(it) } && input.contains("send") -> {
                val parts = input.replace(Regex("(?i)(send|message|sms|text|to)"), "").trim()
                ClassificationResult(IntentType.SEND_MESSAGE, 0.9f, mapOf("contact" to parts))
            }

            else -> null
        }
    }

    private fun checkScreenActionIntent(input: String): ClassificationResult? {
        return when {
            SCREENSHOT_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.TAKE_SCREENSHOT, 0.95f, emptyMap())

            SCREEN_RECORD_KEYWORDS.any { input.contains(it) } ->
                ClassificationResult(IntentType.SCREEN_RECORD, 0.95f, emptyMap())

            input.contains("lock screen") || input.contains("lock phone") || input.contains("lock device") ->
                ClassificationResult(IntentType.LOCK_SCREEN, 0.95f, emptyMap())

            else -> null
        }
    }
    
    override fun isInstantAction(input: String): Boolean {
        val result = classify(input)
        return result.type in listOf(
            IntentType.OPEN_APP, IntentType.TOGGLE_FLASHLIGHT, IntentType.TAKE_PHOTO,
            IntentType.SET_TIMER, IntentType.PLAY_MUSIC, IntentType.CREATE_REMINDER
        )
    }

    /**
     * Quick check if input might be a reminder request.
     * Used for fast-path decisions before AI analysis.
     */
    override fun mightBeReminder(input: String): Boolean {
        val normalized = input.lowercase()

        // Check for explicit reminder keywords
        if (EXTENDED_REMIND_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }

        // Check for multiple time indicators (suggests scheduling intent)
        val timeIndicatorCount = TIME_INDICATORS.count { normalized.contains(it) }
        if (timeIndicatorCount >= 2) {
            return true
        }

        return false
    }

    /**
     * AI-powered classification for complex inputs.
     * Uses SmartReminderAnalyzer for natural language reminder detection.
     */
    override suspend fun classifyWithAI(input: String): ClassificationResult {
        val trimmedInput = input.trim().lowercase()
        Log.d(TAG, "AI Classifying: $trimmedInput")

        // First, try synchronous classification
        val syncResult = classify(input)

        // If sync found a high-confidence result, use it
        if (syncResult.confidence >= 0.85f) {
            return syncResult
        }

        // If might be a reminder, use SmartReminderAnalyzer
        if (mightBeReminder(trimmedInput) || syncResult.type == IntentType.CREATE_REMINDER) {
            try {
                val analyzer = SmartReminderAnalyzer()
                val reminderResult = analyzer.analyze(trimmedInput, useAI = true)

                if (reminderResult.isReminderRequest && !reminderResult.task.isNullOrBlank()) {
                    Log.d(
                        TAG,
                        "AI detected reminder: task='${reminderResult.task}', time='${reminderResult.dateTime}'"
                    )

                    val params = mutableMapOf(
                        "task" to reminderResult.task!!,
                        "smart_parsed" to "true"
                    )

                    reminderResult.dateTime?.let { params["time"] = it }
                    reminderResult.parsedTimeMs?.let { params["parsed_time_ms"] = it.toString() }

                    return ClassificationResult(
                        type = IntentType.CREATE_REMINDER,
                        confidence = reminderResult.confidence,
                        extractedParams = params
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI reminder analysis failed: ${e.message}")
            }
        }

        // Fall back to sync result
        return syncResult
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
        // Check for any reminder keyword (English or Hindi)
        val hasRemind = words.any { it in REMIND_KEYWORDS } ||
                EXTENDED_REMIND_KEYWORDS.any { input.contains(it) }
        if (!hasRemind) return null

        // Try multiple extraction patterns

        // Pattern 1: English "remind me to [task] at [time]"
        val englishTimeRegex = Regex("(?i)(at|in|on|after|by)\\s+(.+)$")
        val englishMatch = englishTimeRegex.find(input)

        // Pattern 2: Hindi time patterns "kal", "parso", "X baje"
        val hindiTimeRegex = Regex("(?i)(kal|parso|aaj|subah|shaam|raat|dopahar|\\d+\\s*baje)")
        val hindiTimeMatch = hindiTimeRegex.find(input)

        // Extract task - remove reminder keywords and time expressions
        var task = input

        // Remove English reminder prefixes
        task = task.replace(
            Regex("(?i)^(please\\s+)?(remind|reminder)\\s*(me)?\\s*(to|about|of|that)?\\s*"),
            ""
        )

        // Remove Hindi reminder prefixes
        task = task.replace(
            Regex("(?i)^(mujhe|muje)?\\s*(yaad|remind)\\s*(dilana|karna|kar|dena)?\\s*(ki|ke|ka)?\\s*"),
            ""
        )
        task = task.replace(Regex("(?i)^(batana|bata)\\s*(dena)?\\s*(ki|ke|ka)?\\s*"), "")

        // Remove time expressions from the end
        task = task.replace(englishTimeRegex, "")
        task = task.replace(
            Regex("(?i)\\s*(kal|parso|aaj)\\s*(ko)?\\s*(subah|shaam|raat|dopahar)?\\s*(\\d+\\s*baje)?\\s*$"),
            ""
        )
        task = task.replace(Regex("(?i)\\s*(hai|he|h)\\s*$"), "")
        task = task.trim()

        // Extract time
        val time = when {
            englishMatch != null -> englishMatch.groupValues[2].trim()
            hindiTimeMatch != null -> {
                // Collect all Hindi time parts
                val timeParts = mutableListOf<String>()
                hindiTimeRegex.findAll(input).forEach { timeParts.add(it.value) }
                timeParts.joinToString(" ")
            }

            else -> ""
        }

        if (task.isNotEmpty() && task.length >= 2) {
            // Capitalize first letter
            task = task.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val params = mutableMapOf("task" to task)
            if (time.isNotEmpty()) {
                params["time"] = time
            }

            return ClassificationResult(
                type = IntentType.CREATE_REMINDER,
                confidence = if (time.isNotEmpty()) 0.9f else 0.75f,
                extractedParams = params
            )
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
