package com.runanywhere.startup_hackathon20.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.runanywhere.startup_hackathon20.MyApplication
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Floating Voice Assistant Service
 *
 * Features:
 * - Floating bubble that stays on top of other apps
 * - Continuous voice listening
 * - Executes commands and continues listening
 * - Draggable bubble
 * - TTS responses
 * - Dismiss by dragging to bottom
 */
class FloatingVoiceService : Service() {

    companion object {
        private const val TAG = "FloatingVoiceService"
        private const val CHANNEL_ID = "floating_voice_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.runanywhere.nova.FLOATING_START"
        const val ACTION_STOP = "com.runanywhere.nova.FLOATING_STOP"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingVoiceService::class.java)
            intent.action = ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingVoiceService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var expandedView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var expandedParams: WindowManager.LayoutParams

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
    private var isExpanded = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Views
    private lateinit var bubbleIcon: ImageView
    private lateinit var expandedMicIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var closeButton: ImageView
    private lateinit var minimizeButton: ImageView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        initTTS()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (!isRunning) {
                    try {
                        // Start foreground with proper service type for Android 14+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(
                                NOTIFICATION_ID,
                                createNotification(),
                                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            )
                        } else {
                            startForeground(NOTIFICATION_ID, createNotification())
                        }
                        createFloatingWindow()
                        initSpeechRecognizer()
                        isRunning = true
                        Log.i(TAG, "Floating voice service started")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service: ${e.message}", e)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopListening()

        speechRecognizer?.destroy()
        speechRecognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        try {
            if (::floatingView.isInitialized) {
                windowManager.removeView(floatingView)
            }
            if (::expandedView.isInitialized && isExpanded) {
                windowManager.removeView(expandedView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing views: ${e.message}")
        }

        serviceScope.cancel()
        Log.i(TAG, "Floating voice service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nova Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating voice assistant"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nova Voice Assistant")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_close, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingWindow() {
        // Inflate bubble view
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        bubbleIcon = floatingView.findViewById(R.id.bubbleIcon)

        // Inflate expanded view
        expandedView = LayoutInflater.from(this).inflate(R.layout.floating_expanded, null)
        expandedMicIcon = expandedView.findViewById(R.id.expandedMicIcon)
        statusText = expandedView.findViewById(R.id.statusText)
        transcriptText = expandedView.findViewById(R.id.transcriptText)
        closeButton = expandedView.findViewById(R.id.closeButton)
        minimizeButton = expandedView.findViewById(R.id.minimizeButton)

        // Layout params for bubble
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Layout params for expanded view
        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        // Add bubble to window
        windowManager.addView(floatingView, params)

        setupBubbleTouchListener()
        setupExpandedViewListeners()

        // Start with pulse animation
        startPulseAnimation()
    }

    private fun setupBubbleTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastAction = 0

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastAction = event.action
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    lastAction = event.action
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (lastAction == MotionEvent.ACTION_DOWN) {
                        // It was a click, not a drag
                        toggleExpanded()
                    } else {
                        // Check if dragged to bottom to dismiss
                        val screenHeight = resources.displayMetrics.heightPixels
                        if (params.y > screenHeight - 300) {
                            stopSelf()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun setupExpandedViewListeners() {
        closeButton.setOnClickListener {
            stopSelf()
        }

        minimizeButton.setOnClickListener {
            collapseTooltip()
        }

        expandedMicIcon.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            collapseTooltip()
        } else {
            expandTooltip()
        }
    }

    private fun expandTooltip() {
        if (isExpanded) return
        isExpanded = true

        // Hide bubble
        floatingView.visibility = View.GONE

        // Show expanded view
        windowManager.addView(expandedView, expandedParams)

        // Start listening
        startListening()
    }

    private fun collapseTooltip() {
        if (!isExpanded) return
        isExpanded = false

        // Show bubble
        floatingView.visibility = View.VISIBLE

        // Remove expanded view
        try {
            windowManager.removeView(expandedView)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing expanded view: ${e.message}")
        }

        // Continue listening in background
        startListening()
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        // Resume listening after speaking
                        serviceScope.launch {
                            delay(300)
                            startListening()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        startListening()
                    }
                })
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                isListening = true
                updateUI("Listening...", "")
                startPulseAnimation()
            }

            override fun onBeginningOfSpeech() {
                updateUI("Listening...", "")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                updateUI("Processing...", "")
                stopPulseAnimation()
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    else -> "Error: $error"
                }
                Log.d(TAG, "Speech error: $errorMsg")

                // Restart listening after error
                serviceScope.launch {
                    delay(1000)
                    if (!isSpeaking && isRunning) {
                        startListening()
                    }
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""

                if (text.isNotBlank()) {
                    Log.d(TAG, "Recognized: $text")
                    updateUI("Processing...", "\"$text\"")
                    processCommand(text)
                } else {
                    // No text, restart listening
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    updateUI("Listening...", "\"$text\"")
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        // Start listening immediately
        serviceScope.launch {
            delay(500)
            startListening()
        }
    }

    private fun startListening() {
        if (isListening || isSpeaking || speechRecognizer == null) return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer: ${e.message}")
        }
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer: ${e.message}")
        }
        stopPulseAnimation()
    }

    private fun processCommand(text: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val classifier = MyApplication.instance.intentClassifier
                val classification = classifier.classify(text)

                Log.d(TAG, "Classification: ${classification.type}")

                val response = if (classification.type.isInstantAction()) {
                    // Execute instant action
                    val executor = MyApplication.instance.instantActionExecutor
                    executor.execute(classification)
                } else {
                    // For LLM queries, give a brief response
                    com.runanywhere.startup_hackathon20.domain.model.AssistantResponse(
                        text = "I'll help you with that. Please check the app for detailed response.",
                        actionResult = com.runanywhere.startup_hackathon20.domain.model.ActionResult.Success(
                            ""
                        ),
                        shouldSpeak = true
                    )
                }

                launch(Dispatchers.Main) {
                    speak(response.text)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing command: ${e.message}")
                launch(Dispatchers.Main) {
                    speak("Sorry, I couldn't process that command.")
                }
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) {
            startListening()
            return
        }

        updateUI("Speaking...", text)
        isSpeaking = true

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun updateUI(status: String, transcript: String) {
        serviceScope.launch(Dispatchers.Main) {
            if (isExpanded && ::statusText.isInitialized) {
                statusText.text = status
                transcriptText.text = transcript
            }

            // Update bubble icon based on status
            if (::bubbleIcon.isInitialized) {
                when {
                    isSpeaking -> bubbleIcon.setImageResource(R.drawable.ic_volume_up)
                    isListening -> bubbleIcon.setImageResource(R.drawable.audio_icon)
                    else -> bubbleIcon.setImageResource(R.drawable.ic_mic_off)
                }
            }
        }
    }

    private fun startPulseAnimation() {
        serviceScope.launch(Dispatchers.Main) {
            if (::bubbleIcon.isInitialized) {
                val pulse =
                    AnimationUtils.loadAnimation(this@FloatingVoiceService, android.R.anim.fade_in)
                pulse.repeatCount = -1
                pulse.duration = 1000
                bubbleIcon.startAnimation(pulse)
            }
        }
    }

    private fun stopPulseAnimation() {
        serviceScope.launch(Dispatchers.Main) {
            if (::bubbleIcon.isInitialized) {
                bubbleIcon.clearAnimation()
            }
        }
    }
}
