package com.runanywhere.startup_hackathon20.domain.service

import android.app.Activity
import android.app.NotificationManager
import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.runanywhere.startup_hackathon20.domain.model.ActionResult
import com.runanywhere.startup_hackathon20.domain.model.AssistantResponse
import com.runanywhere.startup_hackathon20.domain.model.IntentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * System Control Executor
 *
 * Handles ALL system-level voice commands with <80ms execution.
 * Supports toggles, navigation, settings, and system info queries.
 *
 * Categories:
 * - System Toggles (WiFi, Bluetooth, etc.)
 * - Volume/Brightness Control
 * - Settings Navigation
 * - Gallery/File Access
 * - Search Actions
 * - Device Info
 * - Screen Actions
 */
class SystemControlExecutor(private val context: Context) {

    companion object {
        private const val TAG = "SystemControlExecutor"
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Execute a system control command.
     */
    fun execute(
        intentType: IntentType,
        params: Map<String, String> = emptyMap()
    ): AssistantResponse {
        val startTime = System.currentTimeMillis()

        val result = try {
            when (intentType) {
                // ===== System Toggles =====
                IntentType.TOGGLE_WIFI -> toggleWifi(params["state"])
                IntentType.TOGGLE_BLUETOOTH -> toggleBluetooth(params["state"])
                IntentType.TOGGLE_MOBILE_DATA -> openMobileDataSettings()
                IntentType.TOGGLE_AIRPLANE_MODE -> toggleAirplaneMode()
                IntentType.TOGGLE_DND -> toggleDoNotDisturb(params["state"])
                IntentType.TOGGLE_HOTSPOT -> openHotspotSettings()
                IntentType.TOGGLE_LOCATION -> openLocationSettings()
                IntentType.TOGGLE_AUTO_ROTATE -> toggleAutoRotate()

                // ===== Volume Control =====
                IntentType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                IntentType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                IntentType.VOLUME_MUTE -> muteVolume()
                IntentType.VOLUME_MAX -> setMaxVolume()

                // ===== Brightness Control =====
                IntentType.BRIGHTNESS_UP -> adjustBrightness(increase = true)
                IntentType.BRIGHTNESS_DOWN -> adjustBrightness(increase = false)
                IntentType.BRIGHTNESS_MAX -> setMaxBrightness()
                IntentType.BRIGHTNESS_AUTO -> enableAutoBrightness()

                // ===== Settings Navigation =====
                IntentType.OPEN_SETTINGS -> openSettings()
                IntentType.OPEN_WIFI_SETTINGS -> openWifiSettings()
                IntentType.OPEN_BLUETOOTH_SETTINGS -> openBluetoothSettings()
                IntentType.OPEN_DISPLAY_SETTINGS -> openDisplaySettings()
                IntentType.OPEN_SOUND_SETTINGS -> openSoundSettings()
                IntentType.OPEN_BATTERY_SETTINGS -> openBatterySettings()
                IntentType.OPEN_STORAGE_SETTINGS -> openStorageSettings()
                IntentType.OPEN_APP_SETTINGS -> openAppSettings()
                IntentType.OPEN_NOTIFICATION_SETTINGS -> openNotificationSettings()
                IntentType.OPEN_QUICK_SETTINGS -> openQuickSettings()

                // ===== Gallery/Files =====
                IntentType.SHOW_RECENT_PHOTOS -> showRecentPhotos()
                IntentType.OPEN_GALLERY -> openGallery()
                IntentType.OPEN_DOWNLOADS -> openDownloads()
                IntentType.OPEN_DOCUMENTS -> openDocuments()
                IntentType.OPEN_FILE_MANAGER -> openFileManager()

                // ===== Search Actions =====
                IntentType.SEARCH_WEB -> searchWeb(params["query"] ?: "")
                IntentType.SEARCH_YOUTUBE -> searchYoutube(params["query"] ?: "")
                IntentType.SEARCH_MAPS -> searchMaps(params["query"] ?: "")
                IntentType.SEARCH_CONTACTS -> searchContacts(params["query"] ?: "")

                // ===== Device Info =====
                IntentType.SHOW_BATTERY_LEVEL -> showBatteryLevel()
                IntentType.SHOW_STORAGE_INFO -> showStorageInfo()
                IntentType.SHOW_TIME -> showTime()
                IntentType.SHOW_DATE -> showDate()

                // ===== Screen Actions =====
                IntentType.TAKE_SCREENSHOT -> takeScreenshot()
                IntentType.SCREEN_RECORD -> startScreenRecord()
                IntentType.LOCK_SCREEN -> lockScreen()

                // ===== Quick Actions =====
                IntentType.OPEN_CALCULATOR -> openCalculator()
                IntentType.OPEN_CALENDAR -> openCalendar()
                IntentType.OPEN_CONTACTS -> openContacts()
                IntentType.OPEN_CLOCK -> openClock()
                IntentType.OPEN_NOTES -> openNotes()
                IntentType.SCAN_QR -> openQRScanner()

                // ===== Communication =====
                IntentType.CALL_CONTACT -> callContact(params["contact"] ?: "")
                IntentType.SEND_MESSAGE -> sendSMS(params["contact"] ?: "", params["message"] ?: "")
                IntentType.SEND_WHATSAPP -> sendWhatsApp(
                    params["contact"] ?: "",
                    params["message"] ?: ""
                )

                // ===== Media =====
                IntentType.RECORD_VIDEO -> recordVideo()

                else -> AssistantResponse(
                    text = "Command not supported",
                    actionResult = ActionResult.Failure("Unsupported command"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $intentType: ${e.message}")
            AssistantResponse(
                text = "Failed to execute: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Unknown error"),
                shouldSpeak = true
            )
        }

        val executionTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Executed $intentType in ${executionTime}ms")

        return result
    }

    // ==================== SYSTEM TOGGLES ====================

    private fun toggleWifi(state: String?): AssistantResponse {
        return try {
            // Direct toggle not allowed on Android 10+, open settings instead
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val action = when (state?.lowercase()) {
                "on", "enable" -> "Please enable"
                "off", "disable" -> "Please disable"
                else -> "Opening"
            }

            AssistantResponse(
                text = "$action WiFi settings",
                actionResult = ActionResult.Success("WiFi settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open WiFi settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun toggleBluetooth(state: String?): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val action = when (state?.lowercase()) {
                "on", "enable" -> "Please enable"
                "off", "disable" -> "Please disable"
                else -> "Opening"
            }

            AssistantResponse(
                text = "$action Bluetooth settings",
                actionResult = ActionResult.Success("Bluetooth settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open Bluetooth settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openMobileDataSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening mobile data settings",
                actionResult = ActionResult.Success("Data settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open data settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun toggleAirplaneMode(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening airplane mode settings",
                actionResult = ActionResult.Success("Airplane mode settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open airplane mode settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun toggleDoNotDisturb(state: String?): AssistantResponse {
        return try {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val newFilter = when (state?.lowercase()) {
                    "on", "enable" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    "off", "disable" -> NotificationManager.INTERRUPTION_FILTER_ALL
                    else -> if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                }
                notificationManager.setInterruptionFilter(newFilter)

                val status =
                    if (newFilter == NotificationManager.INTERRUPTION_FILTER_ALL) "disabled" else "enabled"
                AssistantResponse(
                    text = "Do Not Disturb $status",
                    actionResult = ActionResult.Success("DND $status"),
                    shouldSpeak = true
                )
            } else {
                // Request permission
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                AssistantResponse(
                    text = "Please grant Do Not Disturb access",
                    actionResult = ActionResult.Success("DND settings opened"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not toggle Do Not Disturb",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openHotspotSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening hotspot settings",
                actionResult = ActionResult.Success("Hotspot settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open hotspot settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openLocationSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening location settings",
                actionResult = ActionResult.Success("Location settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open location settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun toggleAutoRotate(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening display settings for auto-rotate",
                actionResult = ActionResult.Success("Display settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open display settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== VOLUME CONTROL ====================

    private fun adjustVolume(direction: Int): AssistantResponse {
        return try {
            audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = (currentVolume * 100) / maxVolume

            val action = if (direction == AudioManager.ADJUST_RAISE) "increased" else "decreased"
            AssistantResponse(
                text = "Volume $action to $percent%",
                actionResult = ActionResult.Success("Volume $action"),
                shouldSpeak = false // Don't speak over volume change
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not adjust volume",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun muteVolume(): AssistantResponse {
        return try {
            audioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            AssistantResponse(
                text = "Volume muted",
                actionResult = ActionResult.Success("Volume muted"),
                shouldSpeak = false
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not mute volume",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun setMaxVolume(): AssistantResponse {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                maxVolume,
                AudioManager.FLAG_SHOW_UI
            )

            AssistantResponse(
                text = "Volume set to maximum",
                actionResult = ActionResult.Success("Max volume"),
                shouldSpeak = false
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not set max volume",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== BRIGHTNESS CONTROL ====================

    private fun adjustBrightness(increase: Boolean): AssistantResponse {
        return try {
            // Open display settings for brightness control
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val action = if (increase) "increase" else "decrease"
            AssistantResponse(
                text = "Opening display settings to $action brightness",
                actionResult = ActionResult.Success("Display settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not adjust brightness",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun setMaxBrightness(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening display settings for maximum brightness",
                actionResult = ActionResult.Success("Display settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open display settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun enableAutoBrightness(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening display settings for auto-brightness",
                actionResult = ActionResult.Success("Display settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open display settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== SETTINGS NAVIGATION ====================

    private fun openSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening settings",
                actionResult = ActionResult.Success("Settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openWifiSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening WiFi settings",
                actionResult = ActionResult.Success("WiFi settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open WiFi settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openBluetoothSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening Bluetooth settings",
                actionResult = ActionResult.Success("Bluetooth settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open Bluetooth settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openDisplaySettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening display settings",
                actionResult = ActionResult.Success("Display settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open display settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openSoundSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening sound settings",
                actionResult = ActionResult.Success("Sound settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open sound settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openBatterySettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening battery settings",
                actionResult = ActionResult.Success("Battery settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open battery settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openStorageSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening storage settings",
                actionResult = ActionResult.Success("Storage settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open storage settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openAppSettings(): AssistantResponse {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening app settings",
                actionResult = ActionResult.Success("App settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open app settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openNotificationSettings(): AssistantResponse {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening notification settings",
                actionResult = ActionResult.Success("Notification settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open notification settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openQuickSettings(): AssistantResponse {
        return try {
            // This requires system permission, so we'll open settings instead
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening settings panel",
                actionResult = ActionResult.Success("Settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open quick settings",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== GALLERY/FILES ====================

    private fun showRecentPhotos(): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(intent, "View Recent Photos"))

            AssistantResponse(
                text = "Opening recent photos",
                actionResult = ActionResult.Success("Photos opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            // Fallback to gallery
            openGallery()
        }
    }

    private fun openGallery(): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Try Google Photos first
            try {
                intent.setPackage("com.google.android.apps.photos")
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Fallback to any gallery app
                intent.setPackage(null)
                context.startActivity(Intent.createChooser(intent, "Open Gallery"))
            }

            AssistantResponse(
                text = "Opening gallery",
                actionResult = ActionResult.Success("Gallery opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open gallery",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openDownloads(): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath),
                "resource/folder"
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Fallback: open file manager
                val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
                fileIntent.type = "*/*"
                fileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fileIntent)
            }

            AssistantResponse(
                text = "Opening downloads",
                actionResult = ActionResult.Success("Downloads opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open downloads",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openDocuments(): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening documents",
                actionResult = ActionResult.Success("Documents opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open documents",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openFileManager(): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(intent, "Open File Manager"))

            AssistantResponse(
                text = "Opening file manager",
                actionResult = ActionResult.Success("File manager opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open file manager",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== SEARCH ACTIONS ====================

    private fun searchWeb(query: String): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Searching for: $query",
                actionResult = ActionResult.Success("Web search opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            // Fallback to browser
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                )
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)

                AssistantResponse(
                    text = "Searching for: $query",
                    actionResult = ActionResult.Success("Search opened in browser"),
                    shouldSpeak = true
                )
            } catch (e2: Exception) {
                AssistantResponse(
                    text = "Could not search",
                    actionResult = ActionResult.Failure(e2.message ?: "Error"),
                    shouldSpeak = true
                )
            }
        }
    }

    private fun searchYoutube(query: String): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH)
            intent.setPackage("com.google.android.youtube")
            intent.putExtra(SearchManager.QUERY, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Searching YouTube for: $query",
                actionResult = ActionResult.Success("YouTube search opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            // Fallback to YouTube website
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                )
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)

                AssistantResponse(
                    text = "Searching YouTube for: $query",
                    actionResult = ActionResult.Success("YouTube search opened"),
                    shouldSpeak = true
                )
            } catch (e2: Exception) {
                AssistantResponse(
                    text = "Could not search YouTube",
                    actionResult = ActionResult.Failure(e2.message ?: "Error"),
                    shouldSpeak = true
                )
            }
        }
    }

    private fun searchMaps(query: String): AssistantResponse {
        return try {
            val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(mapIntent)

            AssistantResponse(
                text = "Searching maps for: $query",
                actionResult = ActionResult.Success("Maps search opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not search maps",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun searchContacts(query: String): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Searching contacts for: $query",
                actionResult = ActionResult.Success("Contacts search opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            // Fallback: just open contacts
            openContacts()
        }
    }

    // ==================== DEVICE INFO ====================

    private fun showBatteryLevel(): AssistantResponse {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            val chargingStatus = if (isCharging) " and charging" else ""
            AssistantResponse(
                text = "Battery level is $batteryLevel%$chargingStatus",
                actionResult = ActionResult.Success("Battery: $batteryLevel%"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not get battery level",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun showStorageInfo(): AssistantResponse {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes

            val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGB = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)

            AssistantResponse(
                text = String.format(
                    "Storage: %.1f GB used of %.1f GB total. %.1f GB free",
                    usedGB,
                    totalGB,
                    freeGB
                ),
                actionResult = ActionResult.Success("Storage info retrieved"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not get storage info",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun showTime(): AssistantResponse {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        val time = sdf.format(Date())

        return AssistantResponse(
            text = "The time is $time",
            actionResult = ActionResult.Success("Time: $time"),
            shouldSpeak = true
        )
    }

    private fun showDate(): AssistantResponse {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val date = sdf.format(Date())

        return AssistantResponse(
            text = "Today is $date",
            actionResult = ActionResult.Success("Date: $date"),
            shouldSpeak = true
        )
    }

    // ==================== SCREEN ACTIONS ====================

    private fun takeScreenshot(): AssistantResponse {
        return AssistantResponse(
            text = "To take a screenshot, press Power + Volume Down buttons together",
            actionResult = ActionResult.Success("Screenshot instruction"),
            shouldSpeak = true
        )
    }

    private fun startScreenRecord(): AssistantResponse {
        return try {
            // Android 11+ has built-in screen recorder in quick settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Open quick settings and tap screen recorder",
                actionResult = ActionResult.Success("Settings opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not start screen recording",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun lockScreen(): AssistantResponse {
        return AssistantResponse(
            text = "Press the power button to lock your screen",
            actionResult = ActionResult.Success("Lock instruction"),
            shouldSpeak = true
        )
    }

    // ==================== QUICK ACTIONS ====================

    private fun openCalculator(): AssistantResponse {
        return try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening calculator",
                actionResult = ActionResult.Success("Calculator opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open calculator",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openCalendar(): AssistantResponse {
        return try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening calendar",
                actionResult = ActionResult.Success("Calendar opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open calendar",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openContacts(): AssistantResponse {
        return try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_APP_CONTACTS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening contacts",
                actionResult = ActionResult.Success("Contacts opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open contacts",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openClock(): AssistantResponse {
        return try {
            val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.setPackage("com.android.deskclock")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Try alternative
                val altIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(altIntent)
            }

            AssistantResponse(
                text = "Opening clock",
                actionResult = ActionResult.Success("Clock opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open clock",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openNotes(): AssistantResponse {
        return try {
            // Try Google Keep first
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.keep")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Try any notes app
                val genericIntent = Intent(Intent.ACTION_CREATE_NOTE)
                genericIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(genericIntent)
            }

            AssistantResponse(
                text = "Opening notes",
                actionResult = ActionResult.Success("Notes opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open notes app",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun openQRScanner(): AssistantResponse {
        return try {
            // Try to open camera with QR mode or Google Lens
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening camera for QR scanning",
                actionResult = ActionResult.Success("Camera opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open QR scanner",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== COMMUNICATION ====================

    private fun callContact(contact: String): AssistantResponse {
        return try {
            if (contact.isBlank()) {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                AssistantResponse(
                    text = "Opening dialer",
                    actionResult = ActionResult.Success("Dialer opened"),
                    shouldSpeak = true
                )
            } else {
                // Try to dial the contact
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${Uri.encode(contact)}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                AssistantResponse(
                    text = "Calling $contact",
                    actionResult = ActionResult.Success("Call initiated"),
                    shouldSpeak = true
                )
            }
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not make call",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun sendSMS(contact: String, message: String): AssistantResponse {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("smsto:${Uri.encode(contact)}")
            intent.putExtra("sms_body", message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val toText = if (contact.isNotBlank()) " to $contact" else ""
            AssistantResponse(
                text = "Opening messages$toText",
                actionResult = ActionResult.Success("SMS opened"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not open messages",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    private fun sendWhatsApp(contact: String, message: String): AssistantResponse {
        return try {
            Log.d(TAG, "sendWhatsApp: contact='$contact', message='$message'")

            // Method 1: Try to open WhatsApp directly with send intent
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.setPackage("com.whatsapp")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (message.isNotBlank()) {
                intent.putExtra(Intent.EXTRA_TEXT, message)
            }

            try {
                context.startActivity(intent)

                val responseText = if (contact.isNotBlank() && message.isNotBlank()) {
                    "Opening WhatsApp to send \"$message\" - please select $contact"
                } else if (message.isNotBlank()) {
                    "Opening WhatsApp - please select a contact to send: $message"
                } else {
                    "Opening WhatsApp"
                }

                AssistantResponse(
                    text = responseText,
                    actionResult = ActionResult.Success("WhatsApp opened"),
                    shouldSpeak = true
                )
            } catch (e: ActivityNotFoundException) {
                // Fallback: Just open WhatsApp main screen
                val fallbackIntent =
                    context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (fallbackIntent != null) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallbackIntent)

                    AssistantResponse(
                        text = "Opening WhatsApp - please select a contact manually",
                        actionResult = ActionResult.Success("WhatsApp opened"),
                        shouldSpeak = true
                    )
                } else {
                    AssistantResponse(
                        text = "WhatsApp is not installed",
                        actionResult = ActionResult.Failure("WhatsApp not found"),
                        shouldSpeak = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp error: ${e.message}")
            AssistantResponse(
                text = "Could not open WhatsApp: ${e.message}",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }

    // ==================== MEDIA ====================

    private fun recordVideo(): AssistantResponse {
        return try {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            AssistantResponse(
                text = "Opening camera to record video",
                actionResult = ActionResult.Success("Video recording started"),
                shouldSpeak = true
            )
        } catch (e: Exception) {
            AssistantResponse(
                text = "Could not start video recording",
                actionResult = ActionResult.Failure(e.message ?: "Error"),
                shouldSpeak = true
            )
        }
    }
}
