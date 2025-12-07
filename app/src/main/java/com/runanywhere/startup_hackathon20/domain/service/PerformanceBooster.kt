package com.runanywhere.startup_hackathon20.domain.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.view.WindowManager
import androidx.core.content.getSystemService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Performance Booster for LLM Generation
 *
 * Maximizes device performance during LLM inference by:
 * 1. Starting a FOREGROUND SERVICE (highest Android priority!)
 * 2. Acquiring partial wake lock (prevents CPU from sleeping)
 * 3. Setting high thread priority for LLM threads
 * 4. Requesting battery optimization exemption
 *
 * FOREGROUND SERVICE = Android won't kill our app or throttle CPU!
 */
class PerformanceBooster(private val context: Context) {

    companion object {
        private const val TAG = "PerformanceBooster"
        private const val WAKE_LOCK_TAG = "Nova:LLMGeneration"

        /**
         * Get optimal thread count for LLM inference.
         * Uses all available cores for maximum speed.
         */
        fun getOptimalThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            // Use all cores for maximum performance
            // llama.cpp handles thread management internally
            return cores
        }

        /**
         * Get performance-optimized thread count.
         * Leaves 1-2 cores for system to prevent UI lag.
         */
        fun getBalancedThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                cores >= 8 -> cores - 2  // Leave 2 cores for system on 8+ core devices
                cores >= 4 -> cores - 1  // Leave 1 core on 4-7 core devices
                else -> cores            // Use all on low-end devices
            }
        }
    }

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val isBoosted = AtomicBoolean(false)

    /**
     * Start MAXIMUM performance boost mode for LLM generation.
     * Call this BEFORE starting LLM inference.
     *
     * This does:
     * 1. Starts Foreground Service (HIGHEST PRIORITY - Android won't kill us!)
     * 2. Acquires wake lock (CPU stays at full speed)
     * 3. Sets thread priority to URGENT_AUDIO (highest without root)
     */
    fun startBoost() {
        if (isBoosted.getAndSet(true)) {
            Log.d(TAG, "Already in boost mode")
            return
        }

        Log.i(TAG, "🚀🚀🚀 Starting MAXIMUM Performance Boost Mode 🚀🚀🚀")

        // 1. START FOREGROUND SERVICE - This is the KEY!
        // Foreground services get HIGHEST priority from Android
        // System won't kill our app or throttle CPU while service is running
        try {
            LLMForegroundService.start(context)
            Log.d(TAG, "✓ Foreground Service STARTED - Maximum priority active!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }

        // 2. Acquire partial wake lock - keeps CPU running at full speed
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            )?.apply {
                setReferenceCounted(false)
                acquire(5 * 60 * 1000L) // 5 minutes max (safety timeout)
            }
            Log.d(TAG, "✓ Wake lock acquired - CPU won't sleep")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // 3. Set high thread priority for current thread
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.d(TAG, "✓ Thread priority set to URGENT_AUDIO (highest)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set thread priority: ${e.message}")
        }

        // 4. Log CPU info
        val cores = Runtime.getRuntime().availableProcessors()
        val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        Log.i(TAG, "📱 Device: $cores cores, ${maxMemory}MB max heap")
        Log.i(TAG, "🧵 Using ALL $cores CPU cores for LLM inference")
        Log.i(TAG, "⚡ Performance mode: MAXIMUM")
    }

    /**
     * Stop performance boost mode.
     * Call this AFTER LLM generation is complete.
     */
    fun stopBoost() {
        if (!isBoosted.getAndSet(false)) {
            return
        }

        Log.i(TAG, "⏹️ Stopping Performance Boost Mode")

        // 1. Stop Foreground Service
        try {
            LLMForegroundService.stop(context)
            Log.d(TAG, "✓ Foreground Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service: ${e.message}")
        }

        // 2. Release wake lock
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

        // 3. Reset thread priority to normal
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
            Log.d(TAG, "✓ Thread priority reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting thread priority: ${e.message}")
        }

        Log.i(TAG, "✅ Performance boost ended - resources released")
    }

    /**
     * Request battery optimization exemption.
     * This shows a system dialog asking user to exempt our app.
     * Once exempted, Android won't throttle our app even in background!
     *
     * Call this ONCE when user first uses LLM feature.
     */
    fun requestBatteryOptimizationExemption(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Not needed on older Android
        }

        val packageName = context.packageName

        // Check if already exempted
        if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
            Log.i(TAG, "✓ Already exempted from battery optimization")
            return true
        }

        // Request exemption - shows system dialog
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "📋 Requested battery optimization exemption")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery exemption: ${e.message}")
            return false
        }
    }

    /**
     * Check if app is exempted from battery optimization.
     */
    fun isBatteryOptimizationExempted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /**
     * Boost a specific thread for LLM work.
     * Call this from the thread that will do LLM inference.
     */
    fun boostCurrentThread() {
        try {
            // THREAD_PRIORITY_URGENT_AUDIO is the highest priority available without root
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.d(TAG, "✓ Current thread boosted (tid: ${Process.myTid()})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to boost thread: ${e.message}")
        }
    }

    /**
     * Reset thread priority to normal after LLM work.
     */
    fun resetCurrentThread() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset thread: ${e.message}")
        }
    }

    /**
     * Check if device is in power save mode.
     */
    fun isPowerSaveMode(): Boolean {
        return powerManager?.isPowerSaveMode == true
    }

    /**
     * Get performance recommendations for the user.
     */
    fun getPerformanceTips(): String {
        val tips = mutableListOf<String>()

        // Check power save mode
        if (isPowerSaveMode()) {
            tips.add("Note: Power Save Mode is ON - responses may be slower. Disable for faster AI.")
        }

        // Check battery
        val batteryManager =
            context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel =
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?: 100
        val isCharging = batteryManager?.isCharging == true

        if (batteryLevel < 20 && !isCharging) {
            tips.add("Low battery ($batteryLevel%) - plug in for faster responses")
        }

        if (isCharging) {
            tips.add("Charging - optimal performance available")
        }

        // CPU info
        val cores = Runtime.getRuntime().availableProcessors()
        tips.add("Using $cores CPU cores for AI processing")

        return tips.joinToString("\n")
    }

    /**
     * Execute a block of code with performance boost.
     * Automatically starts and stops boost.
     */
    inline fun <T> withBoost(block: () -> T): T {
        startBoost()
        return try {
            block()
        } finally {
            stopBoost()
        }
    }

    /**
     * Clean up resources.
     */
    fun release() {
        stopBoost()
    }
}
