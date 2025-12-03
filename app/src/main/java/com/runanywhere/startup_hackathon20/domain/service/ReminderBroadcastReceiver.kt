package com.runanywhere.startup_hackathon20.domain.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver that handles reminder notifications when they trigger.
 * Displays a local notification with the reminder content.
 * 
 * Requirement 5.4: WHEN a reminder time arrives, THE Translexa app SHALL 
 * display a local notification with the reminder content
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Handle both old and new action constants for compatibility
        if (action == NotificationHelper.ACTION_REMINDER_TRIGGERED ||
            action == InstantActionExecutorImpl.ACTION_REMINDER_TRIGGERED) {
            
            val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1)
                .takeIf { it != -1L }
                ?: intent.getLongExtra(InstantActionExecutorImpl.EXTRA_REMINDER_ID, -1)
            
            val reminderTitle = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_TITLE)
                ?: intent.getStringExtra(InstantActionExecutorImpl.EXTRA_REMINDER_TITLE)
                ?: "Reminder"
            
            val reminderDescription = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_DESCRIPTION) ?: ""
            
            if (reminderId != -1L) {
                val notificationHelper = NotificationHelper(context)
                notificationHelper.showReminderNotification(reminderId, reminderTitle, reminderDescription)
            }
        }
    }
}
