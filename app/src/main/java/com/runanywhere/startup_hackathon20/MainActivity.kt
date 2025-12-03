package com.runanywhere.startup_hackathon20

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.runanywhere.startup_hackathon20.domain.service.RecordingState
import com.runanywhere.startup_hackathon20.domain.service.TTSState
import kotlinx.coroutines.launch

/**
 * Main activity for Translexa voice assistant.
 * Integrates chat, voice input, and TTS functionality.
 * 
 * Requirements: 1.1, 1.4, 3.3, 3.4, 9.1, 9.2
 */
class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusMessage: TextView
    private lateinit var statusIndicator: View
    private lateinit var voiceIndicator: ImageView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var modelSelectorRecyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var audioButton: MaterialButton
    private lateinit var modelButton: MaterialButton
    private lateinit var addButton: MaterialButton

    private var showModelSelector = false

    private val chatViewModel: ChatViewModel by viewModels { ViewModelFactory() }
    private val voiceViewModel: VoiceViewModel by viewModels { ViewModelFactory() }

    private val messagesAdapter = MessagesAdapter()
    private val modelsAdapter = ModelsAdapter(
        onDownload = { modelId -> chatViewModel.downloadModel(modelId) },
        onLoad = { modelId -> chatViewModel.loadModel(modelId) }
    )
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start recording
            voiceViewModel.startRecording()
        } else {
            // Permission denied
            Toast.makeText(
                this,
                "Microphone permission is required for voice input",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModels()
        setupVoiceCallback()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        statusMessage = findViewById(R.id.statusMessage)
        statusIndicator = findViewById(R.id.statusIndicator)
        voiceIndicator = findViewById(R.id.voiceIndicator)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        modelSelectorRecyclerView = findViewById(R.id.modelSelectorRecyclerView)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        audioButton = findViewById(R.id.audioButton)
        modelButton = findViewById(R.id.modelButton)
        addButton = findViewById(R.id.addButton)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }
    
    private fun setupRecyclerViews() {
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.adapter = messagesAdapter

        modelSelectorRecyclerView.layoutManager = LinearLayoutManager(this)
        modelSelectorRecyclerView.adapter = modelsAdapter
    }
    
    private fun setupClickListeners() {
        // Send button
        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendMessage(text)
                inputEditText.text.clear()
            }
        }
        
        // Audio/microphone button
        audioButton.isEnabled = true
        audioButton.setOnClickListener {
            handleMicrophoneClick()
        }
        
        // Model button
        modelButton.isEnabled = true
        modelButton.setOnClickListener {
            toggleModelSelector()
        }
        
        // Add/Refresh button - refreshes model list
        addButton.isEnabled = true
        addButton.setOnClickListener {
            chatViewModel.refreshModels()
            Toast.makeText(this, "Refreshing models...", Toast.LENGTH_SHORT).show()
        }
        
        // Text input watcher
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrBlank()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    
    private fun observeViewModels() {
        // Chat messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.messages.collect { messages ->
                    messagesAdapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // Status message
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.statusMessage.collect { message ->
                    statusMessage.text = message
                }
            }
        }

        // Available models
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.availableModels.collect { models ->
                    modelsAdapter.submitList(models)
                }
            }
        }

        // Current model
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.currentModelId.collect { modelId ->
                    modelsAdapter.setCurrentModel(modelId)
                }
            }
        }

        // Loading state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.isLoading.collect { isLoading ->
                    sendButton.isEnabled = !isLoading && inputEditText.text.isNotBlank()
                    updateLoadingIndicator(isLoading)
                }
            }
        }
        
        // Recording state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.recordingState.collect { state ->
                    updateRecordingUI(state)
                }
            }
        }
        
        // TTS state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.ttsState.collect { state ->
                    updateTTSIndicator(state)
                }
            }
        }
        
        // Voice error messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.errorMessage.collect { error ->
                    error?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        voiceViewModel.clearError()
                    }
                }
            }
        }
        
        // Transcription results
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.transcriptionResult.collect { result ->
                    result?.let {
                        // Display transcribed text in input field
                        if (it.text.isNotBlank() && !it.text.startsWith("[")) {
                            inputEditText.setText(it.text)
                        }
                        voiceViewModel.clearTranscription()
                    }
                }
            }
        }
    }
    
    /**
     * Sets up callback for when transcription completes.
     * Requirement 1.4: WHEN transcription completes, THE Translexa app
     * SHALL display the transcribed text in the chat interface
     */
    private fun setupVoiceCallback() {
        voiceViewModel.onTranscriptionComplete = { transcribedText ->
            runOnUiThread {
                // Auto-send the transcribed text
                if (transcribedText.isNotBlank() && !transcribedText.startsWith("[")) {
                    chatViewModel.sendMessage(transcribedText)
                }
            }
        }
    }
    
    /**
     * Handles microphone button click.
     * Opens full-screen voice input dialog with animated circle.
     */
    private fun handleMicrophoneClick() {
        if (hasMicrophonePermission()) {
            showVoiceInputDialog()
        } else {
            requestMicrophonePermission()
        }
    }
    
    /**
     * Opens the full-screen voice assistant with offline Vosk STT.
     */
    private fun showVoiceInputDialog() {
        val intent = android.content.Intent(this, VoiceAssistantActivity::class.java)
        startActivity(intent)
    }
    
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Requests microphone permission.
     * Requirement 1.5: IF audio recording fails due to permission denial,
     * THEN THE Translexa app SHALL display a clear permission request message
     */
    private fun requestMicrophonePermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    /**
     * Updates UI based on recording state.
     * Requirement 1.1: Display a visual recording indicator
     */
    private fun updateRecordingUI(state: RecordingState) {
        when (state) {
            is RecordingState.Idle -> {
                audioButton.setIconTintResource(R.color.iconColor)
                voiceIndicator.visibility = View.GONE
                updateStatusIndicator(StatusType.READY)
                statusMessage.text = chatViewModel.statusMessage.value
            }
            is RecordingState.Recording -> {
                // Visual indicator for recording (red tint)
                audioButton.setIconTintResource(android.R.color.holo_red_dark)
                voiceIndicator.visibility = View.VISIBLE
                updateStatusIndicator(StatusType.RECORDING)
                statusMessage.text = "Recording... Tap to stop"
            }
            is RecordingState.Processing -> {
                audioButton.setIconTintResource(R.color.iconColor)
                voiceIndicator.visibility = View.GONE
                updateStatusIndicator(StatusType.PROCESSING)
                statusMessage.text = "Processing audio..."
            }
            is RecordingState.Error -> {
                audioButton.setIconTintResource(R.color.iconColor)
                voiceIndicator.visibility = View.GONE
                updateStatusIndicator(StatusType.ERROR)
                statusMessage.text = "Error: ${state.message}"
            }
        }
    }
    
    private enum class StatusType { READY, RECORDING, PROCESSING, ERROR }
    
    private fun updateStatusIndicator(type: StatusType) {
        val color = when (type) {
            StatusType.READY -> ContextCompat.getColor(this, R.color.accentGreen)
            StatusType.RECORDING -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            StatusType.PROCESSING -> ContextCompat.getColor(this, R.color.accentOrange)
            StatusType.ERROR -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        (statusIndicator.background as? GradientDrawable)?.setColor(color)
    }
    
    /**
     * Updates loading indicator.
     * Requirement 9.2: WHEN the assistant is processing,
     * THE Translexa app SHALL display a loading indicator
     */
    private fun updateLoadingIndicator(isLoading: Boolean) {
        if (isLoading) {
            statusMessage.text = "Processing..."
            updateStatusIndicator(StatusType.PROCESSING)
        } else {
            updateStatusIndicator(StatusType.READY)
        }
    }
    
    /**
     * Updates TTS indicator.
     * Requirement 3.2: WHEN TTS playback begins,
     * THE Translexa app SHALL display a visual indicator
     */
    private fun updateTTSIndicator(state: TTSState) {
        when (state) {
            is TTSState.Speaking -> {
                // Could add a speaking indicator here
            }
            is TTSState.Completed, is TTSState.Idle -> {
                // Reset indicator
            }
            is TTSState.Error -> {
                Toast.makeText(this, "TTS Error: ${state.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_models -> {
                toggleModelSelector()
                true
            }
            R.id.action_clear_history -> {
                chatViewModel.clearHistory()
                true
            }
            R.id.action_toggle_tts -> {
                val newState = !chatViewModel.isTtsEnabled.value
                chatViewModel.setTtsEnabled(newState)
                Toast.makeText(
                    this,
                    if (newState) "TTS enabled" else "TTS disabled",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_stop_speaking -> {
                chatViewModel.stopSpeaking()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleModelSelector() {
        showModelSelector = !showModelSelector
        modelSelectorRecyclerView.visibility = if (showModelSelector) View.VISIBLE else View.GONE
        
        // Show model count for debugging
        val modelCount = chatViewModel.availableModels.value.size
        if (showModelSelector) {
            Toast.makeText(this, "Models available: $modelCount", Toast.LENGTH_SHORT).show()
        }
    }
}
