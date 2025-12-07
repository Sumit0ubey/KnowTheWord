package com.runanywhere.startup_hackathon20.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.runanywhere.startup_hackathon20.R

/**
 * Foreground Service for LLM Generation
 *
 * Android gives HIGHEST PRIORITY to foreground services!
 * This ensures:
 * 1. App won't be killed during generation
 * 2. CPU resources are prioritized for our app
 * 3. System won't throttle our process
 *
 * Usage:
 * 1. Start service before LLM generation
 * 2. Stop service after generation complete
 */
class LLMForegroundService : Service() {

    companion object {
        private const val TAG = "LLMForegroundService"
        private const val CHANNEL_ID = "nova_llm_channel"
        private const val NOTIFICATION_ID = 9999
        private const val WAKE_LOCK_TAG = "Nova:LLMService"

        // Static flag to check if service is running
        @Volatile
        var isRunning = false
            private set

        /**
         * Start the foreground service for LLM generation
         */
        fun start(context: Context) {
            if (isRunning) {
                Log.d(TAG, "Service already running")
                return
            }

            try {
                val intent = Intent(context, LLMForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "🚀 LLM Foreground Service starting...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }

        /**
         * Stop the foreground service after LLM generation
         */
        fun stop(context: Context) {
            if (!isRunning) return

            try {
                val intent = Intent(context, LLMForegroundService::class.java)
                context.stopService(intent)
                Log.i(TAG, "🛑 LLM Foreground Service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LLMForegroundService = this@LLMForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // Start as foreground immediately
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        isRunning = true

        // Acquire wake lock
        acquireWakeLock()

        // Set high thread priority for this service
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            Log.d(TAG, "✓ Thread priority set to FOREGROUND")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set thread priority: ${e.message}")
        }

        Log.i(TAG, "🚀 LLM Foreground Service STARTED - Maximum priority active!")

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()

        // Reset thread priority
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        } catch (e: Exception) {
            // Ignore
        }

        Log.i(TAG, "🛑 LLM Foreground Service DESTROYED")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Processing",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            ).apply {
                description = "Shows when Nova is processing your request"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova AI")
            .setContentText("Processing your request...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 1000L) // 3 minutes max
            }
            Log.d(TAG, "✓ Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "✓ Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
    }
}
