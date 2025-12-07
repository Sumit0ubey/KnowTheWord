package com.runanywhere.startup_hackathon20.domain.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log

/**
 * Device capability tiers based on available resources.
 * Used to determine which models can safely run on the device.
 */
enum class DeviceTier {
    LOW,      // < 3GB RAM - Only tiny models (SmolLM2 360M)
    MEDIUM,   // 3-6GB RAM - Small to medium models (Qwen 0.5B, Llama 1B)
    HIGH,     // 6-8GB RAM - Medium to large models (Qwen 1.5B, Llama 3B)
    ULTRA     // 8GB+ RAM - All models including Mistral 7B
}

/**
 * Battery status for adaptive model loading decisions.
 */
enum class BatteryStatus {
    CRITICAL,   // < 15% - Avoid heavy operations
    LOW,        // 15-30% - Use small models only
    NORMAL,     // 30-60% - Normal operation
    GOOD,       // > 60% - Can use any model
    CHARGING    // Plugged in - No restrictions
}

/**
 * Resource snapshot containing current device state.
 */
data class ResourceSnapshot(
    val availableRamMB: Long,
    val totalRamMB: Long,
    val ramUsagePercent: Int,
    val batteryPercent: Int,
    val batteryStatus: BatteryStatus,
    val isCharging: Boolean,
    val availableStorageMB: Long,
    val deviceTier: DeviceTier
)

/**
 * Model resource requirements for safe loading decisions.
 */
data class ModelRequirements(
    val modelName: String,
    val minRamMB: Long,         // Minimum RAM needed
    val recommendedRamMB: Long, // Recommended RAM for smooth operation
    val fileSizeMB: Long,       // Download size
    val qualityTier: Int        // 1-10 quality rating
)

/**
 * Manages device resource monitoring for adaptive model loading.
 * Uses TOTAL RAM for decisions since Android's "available RAM" is misleading
 * (most RAM is used for caching and can be freed when needed).
 *
 * Features:
 * - Real-time RAM monitoring
 * - Battery level tracking
 * - Device tier classification (based on TOTAL RAM)
 * - Safe model recommendations
 */
class DeviceResourceManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceResourceManager"

        // RAM thresholds for device tiers (in MB) - based on TOTAL RAM
        private const val LOW_RAM_THRESHOLD = 3000L      // 3GB
        private const val MEDIUM_RAM_THRESHOLD = 6000L   // 6GB
        private const val HIGH_RAM_THRESHOLD = 8000L     // 8GB

        // Battery thresholds
        private const val BATTERY_CRITICAL = 15
        private const val BATTERY_LOW = 30
        private const val BATTERY_NORMAL = 60

        // Pre-defined model requirements - REDUCED for practical use
        // minRamMB is now based on TOTAL device RAM, not available RAM
        val MODEL_REQUIREMENTS = mapOf(
            // Small models - Works on any device with 2GB+ RAM
            "SmolLM2-360M" to ModelRequirements(
                modelName = "SmolLM2 360M",
                minRamMB = 2000,      // 2GB total RAM
                recommendedRamMB = 3000,
                fileSizeMB = 119,
                qualityTier = 2
            ),
            "Qwen-0.5B" to ModelRequirements(
                modelName = "Qwen 2.5 0.5B",
                minRamMB = 2000,      // 2GB total RAM
                recommendedRamMB = 3000,
                fileSizeMB = 374,
                qualityTier = 3
            ),

            // Medium models - Works on 4GB+ devices
            "Llama-1B-Q4" to ModelRequirements(
                modelName = "Llama 3.2 1B Q4",
                minRamMB = 3000,      // 3GB total RAM
                recommendedRamMB = 4000,
                fileSizeMB = 600,
                qualityTier = 4
            ),
            "Llama-1B-Q6" to ModelRequirements(
                modelName = "Llama 3.2 1B Q6",
                minRamMB = 3000,      // 3GB total RAM
                recommendedRamMB = 4000,
                fileSizeMB = 815,
                qualityTier = 5
            ),
            "Qwen-1.5B" to ModelRequirements(
                modelName = "Qwen 2.5 1.5B",
                minRamMB = 4000,      // 4GB total RAM
                recommendedRamMB = 6000,
                fileSizeMB = 1200,
                qualityTier = 6
            ),

            // Large models - Works on 6GB+ devices
            "Qwen-3B" to ModelRequirements(
                modelName = "Qwen 2.5 3B",
                minRamMB = 6000,      // 6GB total RAM
                recommendedRamMB = 8000,
                fileSizeMB = 2100,
                qualityTier = 7
            ),
            "Llama-3B" to ModelRequirements(
                modelName = "Llama 3.2 3B",
                minRamMB = 6000,      // 6GB total RAM
                recommendedRamMB = 8000,
                fileSizeMB = 2000,
                qualityTier = 7
            ),
            "Phi-3-Mini" to ModelRequirements(
                modelName = "Phi-3 Mini 3.8B",
                minRamMB = 6000,      // 6GB total RAM
                recommendedRamMB = 8000,
                fileSizeMB = 2300,
                qualityTier = 8
            ),

            // Ultra models - Works on 8GB+ devices
            "Mistral-7B" to ModelRequirements(
                modelName = "Mistral 7B",
                minRamMB = 8000,      // 8GB total RAM
                recommendedRamMB = 12000,
                fileSizeMB = 4400,
                qualityTier = 9
            )
        )
    }

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Gets current resource snapshot.
     */
    fun getResourceSnapshot(): ResourceSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMB = memoryInfo.availMem / (1024 * 1024)
        val usedRamMB = totalRamMB - availableRamMB
        val ramUsagePercent = ((usedRamMB * 100) / totalRamMB).toInt()

        val batteryPercent = getBatteryPercent()
        val isCharging = isDeviceCharging()
        val batteryStatus = getBatteryStatus(batteryPercent, isCharging)
        val availableStorageMB = getAvailableStorageMB()
        val deviceTier = calculateDeviceTier(totalRamMB)

        return ResourceSnapshot(
            availableRamMB = availableRamMB,
            totalRamMB = totalRamMB,
            ramUsagePercent = ramUsagePercent,
            batteryPercent = batteryPercent,
            batteryStatus = batteryStatus,
            isCharging = isCharging,
            availableStorageMB = availableStorageMB,
            deviceTier = deviceTier
        )
    }

    /**
     * Calculates device tier based on total RAM.
     */
    private fun calculateDeviceTier(totalRamMB: Long): DeviceTier {
        return when {
            totalRamMB < LOW_RAM_THRESHOLD -> DeviceTier.LOW
            totalRamMB < MEDIUM_RAM_THRESHOLD -> DeviceTier.MEDIUM
            totalRamMB < HIGH_RAM_THRESHOLD -> DeviceTier.HIGH
            else -> DeviceTier.ULTRA
        }
    }

    /**
     * Gets current battery percentage.
     */
    private fun getBatteryPercent(): Int {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        return batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                50 // Default if unknown
            }
        } ?: 50
    }

    /**
     * Checks if device is currently charging.
     */
    private fun isDeviceCharging(): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        return batteryIntent?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
    }

    /**
     * Determines battery status from percentage and charging state.
     */
    private fun getBatteryStatus(percent: Int, isCharging: Boolean): BatteryStatus {
        if (isCharging) return BatteryStatus.CHARGING

        return when {
            percent < BATTERY_CRITICAL -> BatteryStatus.CRITICAL
            percent < BATTERY_LOW -> BatteryStatus.LOW
            percent < BATTERY_NORMAL -> BatteryStatus.NORMAL
            else -> BatteryStatus.GOOD
        }
    }

    /**
     * Gets available storage space in MB.
     */
    private fun getAvailableStorageMB(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage: ${e.message}")
            0L
        }
    }

    /**
     * Checks if a model can safely be loaded with current resources.
     * Uses TOTAL RAM for decision (not available RAM) since Android caches aggressively.
     *
     * @param modelKey The model identifier (e.g., "Qwen-0.5B")
     * @return Pair of (canLoad, reason)
     */
    fun canLoadModel(modelKey: String): Pair<Boolean, String> {
        val requirements = MODEL_REQUIREMENTS[modelKey]
            ?: return Pair(true, "Unknown model, allowing load attempt")

        val snapshot = getResourceSnapshot()

        // Check TOTAL RAM (not available RAM) - Android frees cache when needed
        if (snapshot.totalRamMB < requirements.minRamMB) {
            return Pair(
                false,
                "Device RAM (${snapshot.totalRamMB}MB) is less than required (${requirements.minRamMB}MB)"
            )
        }

        // Check battery (unless charging)
        if (!snapshot.isCharging && snapshot.batteryStatus == BatteryStatus.CRITICAL) {
            return Pair(
                false,
                "Battery too low (${snapshot.batteryPercent}%). Please charge your device"
            )
        }

        // Warning for low battery with heavy models
        if (!snapshot.isCharging &&
            snapshot.batteryStatus == BatteryStatus.LOW &&
            requirements.qualityTier >= 6
        ) {
            return Pair(
                true,
                "Warning: Low battery (${snapshot.batteryPercent}%). Model may drain battery quickly"
            )
        }

        return Pair(true, "Resources OK - ${snapshot.totalRamMB}MB total RAM")
    }

    /**
     * Gets the best model recommendation based on TOTAL device RAM.
     * Returns the highest quality model that can safely run.
     */
    fun getRecommendedModel(): String {
        val snapshot = getResourceSnapshot()

        Log.d(
            TAG,
            "Getting recommended model. Total RAM: ${snapshot.totalRamMB}MB, Tier: ${snapshot.deviceTier}"
        )

        // If battery critical, only allow tiny models
        if (snapshot.batteryStatus == BatteryStatus.CRITICAL) {
            Log.d(TAG, "Battery critical, recommending smallest model")
            return "SmolLM2-360M"
        }

        // Find best model that fits in TOTAL RAM
        val suitableModels = MODEL_REQUIREMENTS.entries
            .filter { it.value.minRamMB <= snapshot.totalRamMB }
            .sortedByDescending { it.value.qualityTier }

        val recommended = suitableModels.firstOrNull()?.key ?: "SmolLM2-360M"
        Log.d(TAG, "Recommended model: $recommended")

        return recommended
    }

    /**
     * Gets the default model for app startup based on device tier.
     * Uses conservative estimates to ensure smooth startup.
     */
    fun getDefaultStartupModel(): String {
        val snapshot = getResourceSnapshot()

        return when (snapshot.deviceTier) {
            DeviceTier.LOW -> "SmolLM2-360M"       // 119MB, very safe
            DeviceTier.MEDIUM -> "Qwen-0.5B"      // 374MB, good balance
            DeviceTier.HIGH -> "Llama-1B-Q4"      // 600MB, fast + quality
            DeviceTier.ULTRA -> "Qwen-1.5B"       // 1.2GB, high quality
        }
    }

    /**
     * Gets list of all models safe for current device tier.
     */
    fun getSafeModelsForDevice(): List<String> {
        val snapshot = getResourceSnapshot()

        return MODEL_REQUIREMENTS.entries
            .filter { entry ->
                val (canLoad, _) = canLoadModel(entry.key)
                canLoad
            }
            .map { it.key }
    }

    /**
     * Logs current resource state for debugging.
     */
    fun logResourceState() {
        val snapshot = getResourceSnapshot()
        Log.i(
            TAG, """
            ====== Device Resources ======
            Total RAM: ${snapshot.totalRamMB}MB
            Available RAM: ${snapshot.availableRamMB}MB
            RAM Usage: ${snapshot.ramUsagePercent}%
            Battery: ${snapshot.batteryPercent}%
            Battery Status: ${snapshot.batteryStatus}
            Charging: ${snapshot.isCharging}
            Storage: ${snapshot.availableStorageMB}MB
            Device Tier: ${snapshot.deviceTier}
            Default Model: ${getDefaultStartupModel()}
            ==============================
        """.trimIndent()
        )
    }
}
