package com.runanywhere.startup_hackathon20.domain.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.runanywhere.startup_hackathon20.MainActivity
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.domain.model.Reminder

/**
 * Helper class for managing local notifications for reminders.
 * Handles notification channel creation, scheduling, and display.
 * 
 * Requirement 5.4: WHEN a reminder time arrives, THE Translexa app SHALL
 * display a local notification with the reminder content
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        const val REMINDER_CHANNEL_ID = "translexa_reminders"
        const val REMINDER_CHANNEL_NAME = "Reminders"
        const val REMINDER_CHANNEL_DESCRIPTION = "Notifications for scheduled reminders"
        
        const val ACTION_REMINDER_TRIGGERED = "com.runanywhere.startup_hackathon20.REMINDER_TRIGGERED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_DESCRIPTION = "reminder_description"
    }
    
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    
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
                description = REMINDER_CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Checks if notification permission is granted.
     * Required for Android 13+ (API 33+).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Checks if exact alarm permission is granted.
     * Required for Android 12+ (API 31+) for exact alarms.
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Schedules a reminder notification using AlarmManager.
     * 
     * @param reminder The reminder to schedule
     */
    fun scheduleReminder(reminder: Reminder) {
        val intent = Intent(ACTION_REMINDER_TRIGGERED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(EXTRA_REMINDER_DESCRIPTION, reminder.description)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerTimeMs,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fall back to inexact alarm if exact alarm permission not granted
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerTimeMs,
                pendingIntent
            )
        }
    }
    
    /**
     * Cancels a scheduled reminder notification.
     * 
     * @param reminderId The ID of the reminder to cancel
     */
    fun cancelReminder(reminderId: Long) {
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
        
        // Also dismiss any existing notification
        notificationManager.cancel(reminderId.toInt())
    }
    
    /**
     * Shows a reminder notification immediately.
     * 
     * @param reminderId The reminder ID
     * @param title The notification title
     * @param description The notification description
     */
    fun showReminderNotification(reminderId: Long, title: String, description: String = "") {
        if (!hasNotificationPermission()) {
            return
        }
        
        // Create intent to open the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Translexa Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        // Add description as expanded text if available
        if (description.isNotBlank()) {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$title\n$description")
            )
        }
        
        try {
            NotificationManagerCompat.from(context).notify(reminderId.toInt(), notificationBuilder.build())
        } catch (e: SecurityException) {
            // Permission not granted, silently fail
        }
    }
}
