package com.runanywhere.startup_hackathon20.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Types of intents that can be classified from user input.
 * Divided into instant actions (no LLM needed) and knowledge queries (LLM needed).
 *
 * Comprehensive voice UI control support for fast (<80ms) execution.
 */
enum class IntentType {
    // ===== App Launch Actions =====
    OPEN_APP,               // "open instagram", "launch whatsapp"

    // ===== Media Actions =====
    PLAY_MUSIC,             // "play music", "play songs"
    TAKE_PHOTO,             // "take photo", "click picture"
    RECORD_VIDEO,           // "record video", "start recording"

    // ===== Time-based Actions =====
    SET_TIMER,              // "set timer for 5 minutes"
    SET_ALARM,              // "set alarm for 7am"
    CREATE_REMINDER,        // "remind me to call mom"

    // ===== Communication Actions =====
    CALL_CONTACT,           // "call John", "dial mom"
    SEND_MESSAGE,           // "send message to John"
    SEND_WHATSAPP,          // "send whatsapp to John"

    // ===== System Toggle Actions =====
    TOGGLE_FLASHLIGHT,      // "turn on flashlight", "torch off"
    TOGGLE_WIFI,            // "turn on wifi", "disable wifi"
    TOGGLE_BLUETOOTH,       // "enable bluetooth", "bluetooth off"
    TOGGLE_MOBILE_DATA,     // "turn on data", "disable mobile data"
    TOGGLE_AIRPLANE_MODE,   // "airplane mode on", "flight mode off"
    TOGGLE_DND,             // "do not disturb on", "dnd off"
    TOGGLE_HOTSPOT,         // "turn on hotspot", "disable hotspot"
    TOGGLE_LOCATION,        // "turn on gps", "location off"
    TOGGLE_AUTO_ROTATE,     // "auto rotate on", "rotation lock"

    // ===== Volume/Brightness Control =====
    VOLUME_UP,              // "increase volume", "volume up"
    VOLUME_DOWN,            // "decrease volume", "volume down"
    VOLUME_MUTE,            // "mute", "silent mode"
    VOLUME_MAX,             // "max volume", "full volume"
    BRIGHTNESS_UP,          // "increase brightness", "brighter"
    BRIGHTNESS_DOWN,        // "decrease brightness", "dimmer"
    BRIGHTNESS_MAX,         // "max brightness", "full brightness"
    BRIGHTNESS_AUTO,        // "auto brightness"

    // ===== Navigation/Settings Actions =====
    OPEN_SETTINGS,          // "open settings"
    OPEN_WIFI_SETTINGS,     // "open wifi settings", "go to wifi"
    OPEN_BLUETOOTH_SETTINGS,// "bluetooth settings"
    OPEN_DISPLAY_SETTINGS,  // "display settings", "screen settings"
    OPEN_SOUND_SETTINGS,    // "sound settings", "volume settings"
    OPEN_BATTERY_SETTINGS,  // "battery settings", "power settings"
    OPEN_STORAGE_SETTINGS,  // "storage settings"
    OPEN_APP_SETTINGS,      // "app settings", "manage apps"
    OPEN_NOTIFICATION_SETTINGS, // "notification settings"
    OPEN_QUICK_SETTINGS,    // "quick settings", "notification panel"

    // ===== Gallery/Files Actions =====
    SHOW_RECENT_PHOTOS,     // "show recent photos", "recent pictures"
    OPEN_GALLERY,           // "open gallery", "open photos"
    OPEN_DOWNLOADS,         // "open downloads", "show downloads"
    OPEN_DOCUMENTS,         // "open documents"
    OPEN_FILE_MANAGER,      // "open files", "file manager"

    // ===== Search Actions =====
    SEARCH_WEB,             // "search for cats", "google search"
    SEARCH_YOUTUBE,         // "search on youtube"
    SEARCH_MAPS,            // "search on maps", "find location"
    SEARCH_CONTACTS,        // "search contacts"

    // ===== Device Info Actions =====
    SHOW_BATTERY_LEVEL,     // "battery level", "how much battery"
    SHOW_STORAGE_INFO,      // "storage info", "how much space"
    SHOW_TIME,              // "what time is it"
    SHOW_DATE,              // "what's the date"

    // ===== Screen Actions =====
    TAKE_SCREENSHOT,        // "take screenshot", "capture screen"
    SCREEN_RECORD,          // "record screen"
    LOCK_SCREEN,            // "lock phone", "lock screen"

    // ===== Quick Actions =====
    OPEN_CALCULATOR,        // "open calculator", "calculate"
    OPEN_CALENDAR,          // "open calendar"
    OPEN_CONTACTS,          // "open contacts"
    OPEN_CLOCK,             // "open clock"
    OPEN_NOTES,             // "open notes"
    SCAN_QR,                // "scan qr code", "scan barcode"

    // ===== Knowledge Queries (LLM needed) =====
    KNOWLEDGE_QUERY,
    CONVERSATION,

    UNKNOWN;

    /**
     * Returns true if this intent type can be executed instantly without LLM.
     */
    fun isInstantAction(): Boolean = when (this) {
        // App launch
        OPEN_APP,
            // Media
        PLAY_MUSIC, TAKE_PHOTO, RECORD_VIDEO,
            // Time-based
        SET_TIMER, SET_ALARM, CREATE_REMINDER,
            // Communication
        CALL_CONTACT, SEND_MESSAGE, SEND_WHATSAPP,
            // System toggles
        TOGGLE_FLASHLIGHT, TOGGLE_WIFI, TOGGLE_BLUETOOTH, TOGGLE_MOBILE_DATA,
        TOGGLE_AIRPLANE_MODE, TOGGLE_DND, TOGGLE_HOTSPOT, TOGGLE_LOCATION, TOGGLE_AUTO_ROTATE,
            // Volume/Brightness
        VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, VOLUME_MAX,
        BRIGHTNESS_UP, BRIGHTNESS_DOWN, BRIGHTNESS_MAX, BRIGHTNESS_AUTO,
            // Navigation/Settings
        OPEN_SETTINGS, OPEN_WIFI_SETTINGS, OPEN_BLUETOOTH_SETTINGS, OPEN_DISPLAY_SETTINGS,
        OPEN_SOUND_SETTINGS, OPEN_BATTERY_SETTINGS, OPEN_STORAGE_SETTINGS, OPEN_APP_SETTINGS,
        OPEN_NOTIFICATION_SETTINGS, OPEN_QUICK_SETTINGS,
            // Gallery/Files
        SHOW_RECENT_PHOTOS, OPEN_GALLERY, OPEN_DOWNLOADS, OPEN_DOCUMENTS, OPEN_FILE_MANAGER,
            // Search
        SEARCH_WEB, SEARCH_YOUTUBE, SEARCH_MAPS, SEARCH_CONTACTS,
            // Device Info
        SHOW_BATTERY_LEVEL, SHOW_STORAGE_INFO, SHOW_TIME, SHOW_DATE,
            // Screen
        TAKE_SCREENSHOT, SCREEN_RECORD, LOCK_SCREEN,
            // Quick Actions
        OPEN_CALCULATOR, OPEN_CALENDAR, OPEN_CONTACTS, OPEN_CLOCK, OPEN_NOTES, SCAN_QR
            -> true
        else -> false
    }
}

/**
 * Types of actions that can be executed by the LLM action executor.
 */
enum class ActionType {
    CREATE_REMINDER,
    DELETE_REMINDER,
    LIST_REMINDERS,
    CREATE_TASK,
    DELETE_TASK,
    LIST_TASKS,
    UNKNOWN
}

/**
 * Result of intent classification.
 */
data class ClassificationResult(
    @SerializedName("type")
    val type: IntentType,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("extractedParams")
    val extractedParams: Map<String, String> = emptyMap()
)

/**
 * Represents a parsed action intent from LLM output.
 * Supports JSON serialization for persistence and parsing.
 */
data class ActionIntent(
    @SerializedName("type")
    val type: ActionType,
    
    @SerializedName("parameters")
    val parameters: Map<String, Any> = emptyMap()
)
