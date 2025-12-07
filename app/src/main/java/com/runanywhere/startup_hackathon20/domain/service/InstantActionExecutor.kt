package com.runanywhere.startup_hackathon20.domain.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import com.runanywhere.startup_hackathon20.domain.model.ActionResult
import com.runanywhere.startup_hackathon20.domain.model.AssistantResponse
import com.runanywhere.startup_hackathon20.domain.model.ClassificationResult
import com.runanywhere.startup_hackathon20.domain.model.IntentType
import com.runanywhere.startup_hackathon20.domain.model.Reminder
import com.runanywhere.startup_hackathon20.data.repository.ReminderRepository
import java.util.concurrent.TimeUnit

/**
 * Interface for executing instant actions without LLM involvement.
 * Achieves sub-80ms execution for device actions.
 * 
 * Requirements: 4.3 (PS3 instant execution)
 */
interface InstantActionExecutor {
    /**
     * Executes an instant action based on the classification result.
     * @param classification The classified intent with extracted parameters
     * @return AssistantResponse with the result of the action
     */
    suspend fun execute(classification: ClassificationResult): AssistantResponse
    
    /**
     * Checks if the executor can handle the given intent type.
     * @param intentType The type of intent to check
     * @return true if this executor can handle the intent
     */
    fun canExecute(intentType: IntentType): Boolean
}

/**
 * Implementation of InstantActionExecutor using Android intents and system services.
 * Provides sub-80ms execution for common device actions.
 * 
 * Requirements: 4.3 (PS3 instant execution)
 */
class InstantActionExecutorImpl(
    private val context: Context,
    private val reminderRepository: ReminderRepository
) : InstantActionExecutor {

    companion object {
        const val REMINDER_CHANNEL_ID = "translexa_reminders"
        const val REMINDER_CHANNEL_NAME = "Reminders"
        const val ACTION_REMINDER_TRIGGERED = "com.runanywhere.startup_hackathon20.REMINDER_TRIGGERED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
    }

    private var flashlightOn = false
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Creates the notification channel for reminders (required for Android O+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for reminders"
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // System control executor for new commands
    private val systemControlExecutor by lazy { SystemControlExecutor(context) }

    override suspend fun execute(classification: ClassificationResult): AssistantResponse {
        return try {
            when (classification.type) {
                // Original intents
                IntentType.OPEN_APP -> openApp(classification.extractedParams["appName"])
                IntentType.PLAY_MUSIC -> playMusic(classification.extractedParams["query"])
                IntentType.SET_TIMER -> setTimer(
                    classification.extractedParams["duration"],
                    classification.extractedParams["unit"]
                )

                IntentType.CREATE_REMINDER -> createReminder(classification.extractedParams)
                IntentType.TOGGLE_FLASHLIGHT -> toggleFlashlight(classification.extractedParams["state"])
                IntentType.TAKE_PHOTO -> takePhoto()

                // ===== NEW: System Controls via SystemControlExecutor =====

                // Volume controls
                IntentType.VOLUME_UP,
                IntentType.VOLUME_DOWN,
                IntentType.VOLUME_MUTE,
                IntentType.VOLUME_MAX,

                    // Brightness controls
                IntentType.BRIGHTNESS_UP,
                IntentType.BRIGHTNESS_DOWN,
                IntentType.BRIGHTNESS_MAX,
                IntentType.BRIGHTNESS_AUTO,

                    // System toggles
                IntentType.TOGGLE_WIFI,
                IntentType.TOGGLE_BLUETOOTH,
                IntentType.TOGGLE_MOBILE_DATA,
                IntentType.TOGGLE_AIRPLANE_MODE,
                IntentType.TOGGLE_DND,
                IntentType.TOGGLE_HOTSPOT,
                IntentType.TOGGLE_LOCATION,
                IntentType.TOGGLE_AUTO_ROTATE,

                    // Settings navigation
                IntentType.OPEN_SETTINGS,
                IntentType.OPEN_WIFI_SETTINGS,
                IntentType.OPEN_BLUETOOTH_SETTINGS,
                IntentType.OPEN_DISPLAY_SETTINGS,
                IntentType.OPEN_SOUND_SETTINGS,
                IntentType.OPEN_BATTERY_SETTINGS,
                IntentType.OPEN_STORAGE_SETTINGS,
                IntentType.OPEN_APP_SETTINGS,
                IntentType.OPEN_NOTIFICATION_SETTINGS,
                IntentType.OPEN_QUICK_SETTINGS,

                    // Gallery/Files
                IntentType.SHOW_RECENT_PHOTOS,
                IntentType.OPEN_GALLERY,
                IntentType.OPEN_DOWNLOADS,
                IntentType.OPEN_DOCUMENTS,
                IntentType.OPEN_FILE_MANAGER,

                    // Search actions
                IntentType.SEARCH_WEB,
                IntentType.SEARCH_YOUTUBE,
                IntentType.SEARCH_MAPS,
                IntentType.SEARCH_CONTACTS,

                    // Device info
                IntentType.SHOW_BATTERY_LEVEL,
                IntentType.SHOW_STORAGE_INFO,
                IntentType.SHOW_TIME,
                IntentType.SHOW_DATE,

                    // Screen actions
                IntentType.TAKE_SCREENSHOT,
                IntentType.SCREEN_RECORD,
                IntentType.LOCK_SCREEN,

                    // Quick actions
                IntentType.OPEN_CALCULATOR,
                IntentType.OPEN_CALENDAR,
                IntentType.OPEN_CONTACTS,
                IntentType.OPEN_CLOCK,
                IntentType.OPEN_NOTES,
                IntentType.SCAN_QR,

                    // Communication
                IntentType.CALL_CONTACT,
                IntentType.SEND_MESSAGE,
                IntentType.SEND_WHATSAPP,

                    // Media
                IntentType.RECORD_VIDEO
                    -> systemControlExecutor.execute(
                    classification.type,
                    classification.extractedParams
                )

                else -> AssistantResponse(
                    text = "Action not supported",
                    actionResult = ActionResult.Failure("Unsupported action type: ${classification.type}"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            AssistantResponse(
                text = "Failed to execute action: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    override fun canExecute(intentType: IntentType): Boolean {
        return intentType.isInstantAction()
    }
    
    /**
     * Opens an app by name using Android's package manager.
     * Requirement: 4.3 (PS3 instant execution)
     */
    private fun openApp(appName: String?): AssistantResponse {
        if (appName.isNullOrBlank()) {
            return AssistantResponse(
                text = "Please specify which app to open",
                actionResult = ActionResult.Failure("No app name provided"),
                shouldSpeak = true
            )
        }
        
        val normalizedName = appName.lowercase().trim()
        android.util.Log.d("InstantAction", "Opening app: $normalizedName")
        
        // Method 1: Try known package names first
        val knownPackage = getPackageNameForApp(normalizedName)
        if (knownPackage != null) {
            android.util.Log.d("InstantAction", "Found known package: $knownPackage")
            val result = tryLaunchPackage(knownPackage, appName)
            if (result != null) return result
        }
        
        // Method 2: Search all installed apps by label
        val searchResult = searchAndLaunchApp(normalizedName)
        if (searchResult != null) return searchResult
        
        // Method 3: Search by package name
        val packageSearchResult = searchByPackageName(normalizedName)
        if (packageSearchResult != null) return packageSearchResult
        
        return AssistantResponse(
            text = "Could not find app: $appName",
            actionResult = ActionResult.Failure("App not found"),
            shouldSpeak = true
        )
    }
    
    private fun tryLaunchPackage(packageName: String, displayName: String): AssistantResponse? {
        return try {
            // Method 1: Try getLaunchIntentForPackage
            var launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            
            // Method 2: If null, try explicit component intent
            if (launchIntent == null) {
                android.util.Log.d("InstantAction", "Trying explicit intent for: $packageName")
                launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
                
                val activities = context.packageManager.queryIntentActivities(launchIntent, 0)
                if (activities.isNotEmpty()) {
                    val activity = activities[0].activityInfo
                    launchIntent.setClassName(activity.packageName, activity.name)
                } else {
                    launchIntent = null
                }
            }
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                android.util.Log.d("InstantAction", "Launched package: $packageName")
                AssistantResponse(
                    text = "Opening $displayName",
                    actionResult = ActionResult.Success("Opened $displayName"),
                    shouldSpeak = true
                )
            } else {
                android.util.Log.d("InstantAction", "No launch intent for: $packageName")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("InstantAction", "Error launching $packageName: ${e.message}")
            null
        }
    }
    
    private fun searchAndLaunchApp(appName: String): AssistantResponse? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
        
        android.util.Log.d("InstantAction", "Searching ${apps.size} apps for: $appName")
        
        // Try exact match first
        for (app in apps) {
            val label = app.loadLabel(pm).toString().lowercase()
            if (label == appName) {
                return launchResolveInfo(app, pm)
            }
        }
        
        // Try contains match
        for (app in apps) {
            val label = app.loadLabel(pm).toString().lowercase()
            if (label.contains(appName) || appName.contains(label)) {
                return launchResolveInfo(app, pm)
            }
        }
        
        // Try starts with
        for (app in apps) {
            val label = app.loadLabel(pm).toString().lowercase()
            if (label.startsWith(appName) || appName.startsWith(label)) {
                return launchResolveInfo(app, pm)
            }
        }
        
        return null
    }
    
    private fun searchByPackageName(appName: String): AssistantResponse? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
        
        for (app in apps) {
            val pkg = app.activityInfo.packageName.lowercase()
            if (pkg.contains(appName)) {
                return launchResolveInfo(app, pm)
            }
        }
        return null
    }
    
    private fun launchResolveInfo(app: android.content.pm.ResolveInfo, pm: android.content.pm.PackageManager): AssistantResponse {
        val packageName = app.activityInfo.packageName
        val appLabel = app.loadLabel(pm).toString()
        
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            android.util.Log.d("InstantAction", "Launched via search: $appLabel ($packageName)")
            AssistantResponse(
                text = "Opening $appLabel",
                actionResult = ActionResult.Success("Opened $appLabel"),
                shouldSpeak = true
            )
        } else {
            AssistantResponse(
                text = "Could not open $appLabel",
                actionResult = ActionResult.Failure("Launch failed"),
                shouldSpeak = true
            )
        }
    }
    
    
    /**
     * Maps common app names to their package names.
     */
    private fun getPackageNameForApp(appName: String): String? {
        val appPackageMap = mapOf(
            // Social Media Apps
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "whats app" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "fb" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "snapchat" to "com.snapchat.android",
            "snap" to "com.snapchat.android",
            "telegram" to "org.telegram.messenger",
            "linkedin" to "com.linkedin.android",
            "pinterest" to "com.pinterest",
            "reddit" to "com.reddit.frontpage",
            "tiktok" to "com.zhiliaoapp.musically",
            "discord" to "com.discord",
            
            // Google Apps
            "chrome" to "com.android.chrome",
            "browser" to "com.android.browser",
            "youtube" to "com.google.android.youtube",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs",
            "calendar" to "com.google.android.calendar",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            
            // System Apps
            "camera" to "com.android.camera",
            "camera2" to "com.android.camera2",
            "gallery" to "com.android.gallery3d",
            "settings" to "com.android.settings",
            "calculator" to "com.android.calculator2",
            "calc" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "alarm" to "com.android.deskclock",
            "contacts" to "com.android.contacts",
            "phone" to "com.android.dialer",
            "dialer" to "com.android.dialer",
            "messages" to "com.google.android.apps.messaging",
            "sms" to "com.android.mms",
            "files" to "com.android.documentsui",
            "file manager" to "com.android.documentsui",
            
            // Entertainment
            "spotify" to "com.spotify.music",
            "music" to "com.google.android.music",
            "play music" to "com.google.android.music",
            "netflix" to "com.netflix.mediaclient",
            "amazon prime" to "com.amazon.avod.thirdpartyclient",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "hotstar" to "in.startv.hotstar",
            "disney hotstar" to "in.startv.hotstar",
            
            // Productivity
            "notes" to "com.google.android.keep",
            "keep" to "com.google.android.keep",
            "google keep" to "com.google.android.keep",
            
            // Shopping
            "amazon" to "com.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            
            // Payment Apps (India)
            "paytm" to "net.one97.paytm",
            "phonepe" to "com.phonepe.app",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            
            // Xiaomi specific (since you have Xiaomi phone)
            "mi camera" to "com.android.camera",
            "mi gallery" to "com.miui.gallery",
            "mi music" to "com.miui.player",
            "mi video" to "com.miui.videoplayer"
        )
        return appPackageMap[appName]
    }
    
    /**
     * Plays music using the default music player or a search intent.
     * Requirement: 4.3 (PS3 instant execution)
     */
    private fun playMusic(query: String?): AssistantResponse {
        return try {
            val intent = if (query.isNullOrBlank() || query == "music") {
                // Open default music player
                Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Search for specific music
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            context.startActivity(intent)
            
            val message = if (query.isNullOrBlank() || query == "music") {
                "Opening music player"
            } else {
                "Playing $query"
            }
            
            AssistantResponse(
                text = message,
                actionResult = ActionResult.Success(message),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not play music: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Sets a timer using the system clock app.
     * Requirement: 4.3 (PS3 instant execution)
     */
    private fun setTimer(duration: String?, unit: String?): AssistantResponse {
        if (duration.isNullOrBlank()) {
            return AssistantResponse(
                text = "Please specify a duration for the timer",
                actionResult = ActionResult.Failure("No duration provided"),
                shouldSpeak = true
            )
        }
        
        return try {
            val durationValue = duration.toIntOrNull() ?: 0
            val unitNormalized = unit?.lowercase() ?: "minute"
            
            val seconds = when {
                unitNormalized.startsWith("second") -> durationValue
                unitNormalized.startsWith("minute") -> durationValue * 60
                unitNormalized.startsWith("hour") -> durationValue * 3600
                else -> durationValue * 60 // Default to minutes
            }
            
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            val timeDescription = formatDuration(durationValue, unitNormalized)
            AssistantResponse(
                text = "Setting timer for $timeDescription",
                actionResult = ActionResult.Success("Timer set for $timeDescription"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not set timer: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Formats duration for display.
     */
    private fun formatDuration(value: Int, unit: String): String {
        val unitDisplay = when {
            unit.startsWith("second") -> if (value == 1) "second" else "seconds"
            unit.startsWith("minute") -> if (value == 1) "minute" else "minutes"
            unit.startsWith("hour") -> if (value == 1) "hour" else "hours"
            else -> unit
        }
        return "$value $unitDisplay"
    }

    
    /**
     * Creates a reminder from extracted parameters and schedules BOTH:
     * 1. A system alarm (visible in Clock app)
     * 2. A local notification backup
     * Now supports smart-parsed time from AI analysis.
     * Requirements: 4.3, 5.1
     */
    private suspend fun createReminder(params: Map<String, String>): AssistantResponse {
        val task = params["task"]
        val time = params["time"]
        val smartParsed = params["smart_parsed"] == "true"
        val parsedTimeMs = params["parsed_time_ms"]?.toLongOrNull()

        android.util.Log.d(
            "InstantAction",
            "Creating reminder - task: $task, time: $time, smartParsed: $smartParsed, parsedTimeMs: $parsedTimeMs"
        )

        if (task.isNullOrBlank()) {
            return AssistantResponse(
                text = "Please specify what to remind you about",
                actionResult = ActionResult.Failure("No task provided"),
                shouldSpeak = true
            )
        }

        return try {
            // Use pre-parsed time if available, otherwise parse the time string
            val triggerTimeMs = when {
                // If SmartReminderAnalyzer already parsed the time, use it
                parsedTimeMs != null && parsedTimeMs > System.currentTimeMillis() -> parsedTimeMs

                // If time string provided, use SmartReminderAnalyzer for better parsing
                !time.isNullOrBlank() -> {
                    val analyzer = SmartReminderAnalyzer()
                    analyzer.parseTimeString(time)
                }

                // Default to 1 hour from now
                else -> System.currentTimeMillis() + (60 * 60 * 1000)
            }

            val reminder = Reminder(
                title = task,
                description = time ?: "",
                triggerTimeMs = triggerTimeMs
            )

            val id = reminderRepository.create(reminder)

            // METHOD 1: Set SYSTEM ALARM (visible in Clock app!)
            setSystemAlarm(task, triggerTimeMs)

            // METHOD 2: Also schedule local notification as backup
            scheduleReminderNotification(id, task, triggerTimeMs)

            // Format time description for user
            val timeDescription = formatReminderTime(triggerTimeMs, time)

            android.util.Log.d(
                "InstantAction",
                "Reminder created: id=$id, triggerTime=${java.util.Date(triggerTimeMs)}"
            )

            AssistantResponse(
                text = "✓ Alarm set! I'll remind you: \"$task\" $timeDescription",
                actionResult = ActionResult.Success("Reminder created", id),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            android.util.Log.e("InstantAction", "Reminder creation failed: ${e.message}", e)
            AssistantResponse(
                text = "Could not create reminder: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }

    /**
     * Sets a SYSTEM ALARM that shows in the Clock app.
     * This is what users expect when they say "set an alarm".
     */
    private fun setSystemAlarm(message: String, triggerTimeMs: Long) {
        try {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = triggerTimeMs

            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)

            android.util.Log.d("InstantAction", "Setting system alarm for $hour:$minute - $message")

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Don't show alarm UI
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            android.util.Log.d("InstantAction", "System alarm set successfully")

        } catch (e: Exception) {
            android.util.Log.e("InstantAction", "Failed to set system alarm: ${e.message}")
            // Silently fail - notification backup will still work
        }
    }

    /**
     * Formats the reminder time for display to the user.
     */
    private fun formatReminderTime(triggerTimeMs: Long, originalTimeStr: String?): String {
        val now = System.currentTimeMillis()
        val diffMs = triggerTimeMs - now
        val diffMinutes = diffMs / (60 * 1000)
        val diffHours = diffMs / (60 * 60 * 1000)
        val diffDays = diffMs / (24 * 60 * 60 * 1000)

        // Format the calendar date/time
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = triggerTimeMs
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val timeStr = if (minute == 0) "$displayHour $amPm" else "$displayHour:${
            minute.toString().padStart(2, '0')
        } $amPm"

        // Determine day description
        val todayCal = java.util.Calendar.getInstance()
        val isToday =
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                    calendar.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR)

        todayCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val isTomorrow =
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                    calendar.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR)

        return when {
            diffMinutes < 60 -> "in ${diffMinutes} minutes"
            isToday -> "today at $timeStr"
            isTomorrow -> "tomorrow at $timeStr"
            diffDays < 7 -> {
                val dayName = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                    java.util.Calendar.MONDAY -> "Monday"
                    java.util.Calendar.TUESDAY -> "Tuesday"
                    java.util.Calendar.WEDNESDAY -> "Wednesday"
                    java.util.Calendar.THURSDAY -> "Thursday"
                    java.util.Calendar.FRIDAY -> "Friday"
                    java.util.Calendar.SATURDAY -> "Saturday"
                    java.util.Calendar.SUNDAY -> "Sunday"
                    else -> "that day"
                }
                "on $dayName at $timeStr"
            }

            else -> {
                val month = calendar.get(java.util.Calendar.MONTH) + 1
                val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                "on $day/$month at $timeStr"
            }
        }
    }
    
    /**
     * Schedules a local notification for a reminder using AlarmManager.
     * Requirement: 5.4 - WHEN a reminder time arrives, THE Translexa app SHALL 
     * display a local notification with the reminder content
     */
    private fun scheduleReminderNotification(reminderId: Long, title: String, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(ACTION_REMINDER_TRIGGERED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }
    
    /**
     * Cancels a scheduled reminder notification.
     */
    fun cancelReminderNotification(reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(ACTION_REMINDER_TRIGGERED).apply {
            setPackage(context.packageName)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
    }
    
    /**
     * Parses a time string to milliseconds since epoch.
     * Supports formats like "8am", "in 5 minutes", "tomorrow at 9am"
     */
    private fun parseTimeToMillis(time: String?): Long {
        if (time.isNullOrBlank()) {
            // Default to 1 hour from now
            return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        }
        
        val lowerTime = time.lowercase().trim()
        val now = System.currentTimeMillis()
        
        // Handle "in X minutes/hours"
        val inPattern = Regex("in\\s+(\\d+)\\s*(minutes?|hours?|seconds?)")
        inPattern.find(lowerTime)?.let { match ->
            val value = match.groupValues[1].toLongOrNull() ?: 0
            val unit = match.groupValues[2]
            val millis = when {
                unit.startsWith("second") -> TimeUnit.SECONDS.toMillis(value)
                unit.startsWith("minute") -> TimeUnit.MINUTES.toMillis(value)
                unit.startsWith("hour") -> TimeUnit.HOURS.toMillis(value)
                else -> TimeUnit.MINUTES.toMillis(value)
            }
            return now + millis
        }
        
        // Handle simple time like "8am", "3pm", "14:00"
        val simpleTimePattern = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        simpleTimePattern.find(lowerTime)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: 0
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[3]
            
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
            calendar.set(java.util.Calendar.MINUTE, minute)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            
            // If the time has passed today, schedule for tomorrow
            if (calendar.timeInMillis <= now) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            
            return calendar.timeInMillis
        }
        
        // Default to 1 hour from now
        return now + TimeUnit.HOURS.toMillis(1)
    }
    
    /**
     * Toggles the flashlight on or off.
     * Requirement: 4.3 (PS3 instant execution)
     */
    private fun toggleFlashlight(state: String?): AssistantResponse {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            
            if (cameraId == null) {
                return AssistantResponse(
                    text = "Flashlight not available on this device",
                    actionResult = ActionResult.Failure("No camera with flash found"),
                    shouldSpeak = true
                )
            }
            
            val targetState = when (state?.lowercase()) {
                "on" -> true
                "off" -> false
                else -> !flashlightOn // Toggle
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, targetState)
                flashlightOn = targetState
            }
            
            val stateText = if (targetState) "on" else "off"
            AssistantResponse(
                text = "Flashlight turned $stateText",
                actionResult = ActionResult.Success("Flashlight $stateText"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not toggle flashlight: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
    
    /**
     * Opens the camera app to take a photo.
     * Requirement: 4.3 (PS3 instant execution)
     */
    private fun takePhoto(): AssistantResponse {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                AssistantResponse(
                    text = "Opening camera to take a photo",
                    actionResult = ActionResult.Success("Camera opened"),
                    shouldSpeak = true
                )
            } else {
                // Fall back to opening camera app
                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(cameraIntent)
                AssistantResponse(
                    text = "Opening camera",
                    actionResult = ActionResult.Success("Camera opened"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open camera: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }
    }
}

/**
 * SearchManager constant for media search queries.
 */
private object SearchManager {
    const val QUERY = "query"
}
