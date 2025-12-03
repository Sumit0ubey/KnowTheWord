package com.runanywhere.startup_hackathon20.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.runanywhere.sdk.models.ModelInfo
import com.runanywhere.startup_hackathon20.ChatViewModel
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.ViewModelFactory
import com.runanywhere.startup_hackathon20.VoiceAssistantActivity
import com.runanywhere.startup_hackathon20.domain.model.ChatSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private val chatViewModel: ChatViewModel by activityViewModels { ViewModelFactory() }

    private lateinit var userAvatar: ImageView
    private lateinit var userName: TextView
    private lateinit var cardChat: LinearLayout
    private lateinit var cardVoice: LinearLayout
    private lateinit var modelsContainer: LinearLayout
    private lateinit var modelStatus: TextView
    private lateinit var emptyModelsText: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyHistoryText: TextView
    private lateinit var btnViewAllHistory: TextView
    private lateinit var btnRefreshModels: TextView
    private lateinit var cardAudioNotes: LinearLayout

    private var currentLoadingModelId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openVoiceAssistant()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupClickListeners()
        observeViewModel()
        loadUserProfile()
    }

    private fun initViews(view: View) {
        userAvatar = view.findViewById(R.id.userAvatar)
        userName = view.findViewById(R.id.userName)
        cardChat = view.findViewById(R.id.cardChat)
        cardVoice = view.findViewById(R.id.cardVoice)
        modelsContainer = view.findViewById(R.id.modelsContainer)
        modelStatus = view.findViewById(R.id.modelStatus)
        emptyModelsText = view.findViewById(R.id.emptyModelsText)
        historyContainer = view.findViewById(R.id.historyContainer)
        emptyHistoryText = view.findViewById(R.id.emptyHistoryText)
        btnViewAllHistory = view.findViewById(R.id.btnViewAllHistory)
        btnRefreshModels = view.findViewById(R.id.btnRefreshModels)
        cardAudioNotes = view.findViewById(R.id.cardAudioNotes)
    }

    private fun setupClickListeners() {
        cardChat.setOnClickListener {
            (activity as? MainActivity)?.navigateToChat()
        }

        cardVoice.setOnClickListener {
            handleVoiceClick()
        }

        btnViewAllHistory.setOnClickListener {
            (activity as? MainActivity)?.navigateToHistory()
        }

        btnRefreshModels.setOnClickListener {
            chatViewModel.refreshModels()
            Toast.makeText(requireContext(), "Refreshing models...", Toast.LENGTH_SHORT).show()
        }
        
        cardAudioNotes.setOnClickListener {
            openAudioNotes()
        }
    }

    private fun observeViewModel() {
        // User settings for avatar and name
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.userSettings.collectLatest { settings ->
                updateUserProfile(settings.name, settings.gender)
            }
        }

        // Available models - show only downloaded ones
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.availableModels.collectLatest { models ->
                updateModelsUI(models)
            }
        }

        // Current loaded model
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.currentModelId.collectLatest { modelId ->
                currentLoadingModelId = null
                updateModelStatus(modelId)
                refreshModelCircles()
            }
        }

        // Chat sessions for history
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.chatSessions.collectLatest { sessions ->
                updateHistoryUI(sessions.take(3)) // Show only 3 recent
            }
        }

        // Loading state
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.isLoading.collectLatest { isLoading ->
                if (!isLoading) {
                    currentLoadingModelId = null
                    refreshModelCircles()
                }
            }
        }

        // Status message
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.statusMessage.collectLatest { message ->
                if (message.contains("Loading") || message.contains("loaded")) {
                    modelStatus.text = message
                }
            }
        }
    }

    private fun loadUserProfile() {
        // Initial load from settings
        val settings = chatViewModel.userSettings.value
        updateUserProfile(settings.name, settings.gender)
    }

    private fun updateUserProfile(name: String, gender: String) {
        userName.text = if (name.isNotBlank()) "Hi, $name!" else "Welcome!"
        
        // Set avatar based on gender
        val avatarRes = when (gender.lowercase()) {
            "female", "f" -> R.drawable.ic_avatar_female
            else -> R.drawable.ic_avatar_male
        }
        userAvatar.setImageResource(avatarRes)
    }

    private fun updateModelsUI(models: List<ModelInfo>) {
        modelsContainer.removeAllViews()
        
        // Filter only downloaded models
        val downloadedModels = models.filter { it.isDownloaded }
        
        if (downloadedModels.isEmpty()) {
            emptyModelsText.visibility = View.VISIBLE
            modelsContainer.visibility = View.GONE
        } else {
            emptyModelsText.visibility = View.GONE
            modelsContainer.visibility = View.VISIBLE
            
            downloadedModels.forEach { model ->
                addModelCircle(model)
            }
        }
    }

    private fun addModelCircle(model: ModelInfo) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_model_circle, modelsContainer, false)

        val modelCircle = itemView.findViewById<FrameLayout>(R.id.modelCircle)
        val modelIcon = itemView.findViewById<ImageView>(R.id.modelIcon)
        val modelNameView = itemView.findViewById<TextView>(R.id.modelName)
        val loadingIndicator = itemView.findViewById<ProgressBar>(R.id.loadingIndicator)
        
        // Star views
        val star1 = itemView.findViewById<ImageView>(R.id.star1)
        val star2 = itemView.findViewById<ImageView>(R.id.star2)
        val star3 = itemView.findViewById<ImageView>(R.id.star3)
        val star4 = itemView.findViewById<ImageView>(R.id.star4)
        val star5 = itemView.findViewById<ImageView>(R.id.star5)

        // Set model icon based on name
        val iconRes = getModelIcon(model.name, model.id)
        modelIcon.setImageResource(iconRes)
        
        // Clean model name (remove emojis) and show short name
        val cleanName = getCleanModelName(model.name)
        modelNameView.text = cleanName
        
        // Set star rating based on model power
        val stars = getModelStars(model.name)
        setStarRating(stars, star1, star2, star3, star4, star5)

        // Check if this model is currently loaded
        val isLoaded = chatViewModel.currentModelId.value == model.id
        val isLoading = currentLoadingModelId == model.id

        if (isLoaded) {
            modelCircle.setBackgroundResource(R.drawable.bg_model_circle_selected)
        } else {
            modelCircle.setBackgroundResource(R.drawable.bg_model_circle)
        }

        if (isLoading) {
            loadingIndicator.visibility = View.VISIBLE
            modelIcon.visibility = View.GONE
        } else {
            loadingIndicator.visibility = View.GONE
            modelIcon.visibility = View.VISIBLE
        }

        modelCircle.setOnClickListener {
            if (!isLoaded && currentLoadingModelId == null) {
                currentLoadingModelId = model.id
                refreshModelCircles()
                chatViewModel.loadModel(model.id)
                Toast.makeText(requireContext(), "Loading $cleanName...", Toast.LENGTH_SHORT).show()
            } else if (isLoaded) {
                Toast.makeText(requireContext(), "$cleanName is already loaded", Toast.LENGTH_SHORT).show()
            }
        }

        modelsContainer.addView(itemView)
    }
    
    private fun getModelIcon(name: String, id: String): Int {
        val lowerName = name.lowercase()
        val lowerId = id.lowercase()
        
        return when {
            lowerName.contains("llama") && lowerName.contains("tiny") -> R.drawable.ic_model_tinyllama
            lowerName.contains("llama") || lowerId.contains("llama") -> R.drawable.ic_model_llama
            lowerName.contains("phi") || lowerId.contains("phi") -> R.drawable.ic_model_phi
            lowerName.contains("gemma") || lowerId.contains("gemma") -> R.drawable.ic_model_gemma
            lowerName.contains("mistral") || lowerId.contains("mistral") -> R.drawable.ic_model_mistral
            lowerName.contains("qwen") || lowerId.contains("qwen") -> R.drawable.ic_model_qwen
            else -> R.drawable.ic_model_default
        }
    }
    
    private fun getCleanModelName(name: String): String {
        // Remove emojis and special characters, keep only alphanumeric and spaces
        val cleaned = name.replace(Regex("[^a-zA-Z0-9\\s.-]"), "").trim()
        
        // Extract short name
        return when {
            cleaned.lowercase().contains("llama") && cleaned.contains("3.2") -> "Llama 3.2"
            cleaned.lowercase().contains("llama") && cleaned.contains("3b") -> "Llama 3B"
            cleaned.lowercase().contains("tinyllama") -> "TinyLlama"
            cleaned.lowercase().contains("phi") && cleaned.contains("3") -> "Phi-3"
            cleaned.lowercase().contains("phi") -> "Phi"
            cleaned.lowercase().contains("gemma") && cleaned.contains("2b") -> "Gemma 2B"
            cleaned.lowercase().contains("gemma") -> "Gemma"
            cleaned.lowercase().contains("mistral") -> "Mistral"
            cleaned.lowercase().contains("qwen") -> "Qwen"
            else -> cleaned.take(10)
        }
    }
    
    private fun getModelStars(name: String): Int {
        val lowerName = name.lowercase()
        
        return when {
            // 5 stars - Most powerful
            lowerName.contains("7b") -> 5
            lowerName.contains("llama") && lowerName.contains("3.2") && lowerName.contains("3b") -> 5
            
            // 4 stars - Very good
            lowerName.contains("3b") -> 4
            lowerName.contains("phi") && lowerName.contains("3") -> 4
            lowerName.contains("mistral") -> 4
            
            // 3 stars - Good
            lowerName.contains("2b") -> 3
            lowerName.contains("gemma") -> 3
            lowerName.contains("qwen") -> 3
            
            // 2 stars - Basic
            lowerName.contains("1b") -> 2
            lowerName.contains("tiny") -> 2
            lowerName.contains("small") -> 2
            
            // Default
            else -> 3
        }
    }
    
    private fun setStarRating(stars: Int, s1: ImageView, s2: ImageView, s3: ImageView, s4: ImageView, s5: ImageView) {
        val starViews = listOf(s1, s2, s3, s4, s5)
        starViews.forEachIndexed { index, star ->
            if (index < stars) {
                star.setImageResource(R.drawable.ic_star_filled)
            } else {
                star.setImageResource(R.drawable.ic_star_empty)
            }
        }
    }

    private fun refreshModelCircles() {
        val models = chatViewModel.availableModels.value.filter { it.isDownloaded }
        modelsContainer.removeAllViews()
        models.forEach { addModelCircle(it) }
    }

    private fun updateModelStatus(modelId: String?) {
        modelStatus.text = if (modelId != null) {
            val modelName = chatViewModel.availableModels.value
                .find { it.id == modelId }?.name ?: modelId
            "✓ $modelName loaded"
        } else {
            "No model loaded"
        }
    }

    private fun updateHistoryUI(sessions: List<ChatSession>) {
        historyContainer.removeAllViews()

        if (sessions.isEmpty()) {
            emptyHistoryText.visibility = View.VISIBLE
            historyContainer.visibility = View.GONE
        } else {
            emptyHistoryText.visibility = View.GONE
            historyContainer.visibility = View.VISIBLE

            sessions.forEach { session ->
                addHistoryItem(session)
            }
        }
    }

    private fun addHistoryItem(session: ChatSession) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_history_home, historyContainer, false)

        val historyTitle = itemView.findViewById<TextView>(R.id.historyTitle)
        val historyDate = itemView.findViewById<TextView>(R.id.historyDate)

        historyTitle.text = session.title
        historyDate.text = formatDate(session.updatedAt)

        itemView.setOnClickListener {
            chatViewModel.loadSession(session.id)
            (activity as? MainActivity)?.navigateToChat()
        }

        historyContainer.addView(itemView)
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun handleVoiceClick() {
        if (hasMicrophonePermission()) {
            openVoiceAssistant()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openVoiceAssistant() {
        val intent = Intent(requireContext(), VoiceAssistantActivity::class.java)
        startActivity(intent)
    }
    
    private fun openAudioNotes() {
        val intent = Intent(requireContext(), com.runanywhere.startup_hackathon20.AudioNotesActivity::class.java)
        startActivity(intent)
    }
}
