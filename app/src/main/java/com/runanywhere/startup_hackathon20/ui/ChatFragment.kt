package com.runanywhere.startup_hackathon20.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.runanywhere.startup_hackathon20.ChatViewModel
import com.runanywhere.startup_hackathon20.MessagesAdapter
import com.runanywhere.startup_hackathon20.ModelPopupAdapter
import com.runanywhere.startup_hackathon20.ModelsAdapter
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.ViewModelFactory
import com.runanywhere.sdk.models.ModelInfo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private val chatViewModel: ChatViewModel by activityViewModels { ViewModelFactory() }
    
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var modelSelectorRecyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var btnNewChat: MaterialButton
    private lateinit var btnModel: MaterialButton
    private lateinit var statusMessage: TextView
    private lateinit var statusIndicator: View
    private lateinit var btnModelIcon: ImageView
    private lateinit var modelPopupCard: CardView
    private lateinit var modelPopupList: RecyclerView
    
    private val messagesAdapter = MessagesAdapter()
    private val modelsAdapter by lazy {
        ModelsAdapter(
            onDownload = { modelId -> chatViewModel.downloadModel(modelId) },
            onLoad = { modelId -> chatViewModel.loadModel(modelId) }
        )
    }
    
    private lateinit var modelPopupAdapter: ModelPopupAdapter
    private var showModelSelector = false
    private var showModelPopup = false
    private var loadedModels: List<ModelInfo> = emptyList()
    private var selectedModelId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        modelSelectorRecyclerView = view.findViewById(R.id.modelSelectorRecyclerView)
        inputEditText = view.findViewById(R.id.inputEditText)
        sendButton = view.findViewById(R.id.sendButton)
        btnNewChat = view.findViewById(R.id.btnNewChat)
        btnModel = view.findViewById(R.id.btnModel)
        statusMessage = view.findViewById(R.id.statusMessage)
        statusIndicator = view.findViewById(R.id.statusIndicator)
        btnModelIcon = view.findViewById(R.id.btnModelIcon)
        modelPopupCard = view.findViewById(R.id.modelPopupCard)
        modelPopupList = view.findViewById(R.id.modelPopupList)
    }

    private fun setupRecyclerViews() {
        messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesRecyclerView.adapter = messagesAdapter

        modelSelectorRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        modelSelectorRecyclerView.adapter = modelsAdapter
        
        // Setup model popup list
        modelPopupAdapter = ModelPopupAdapter { model ->
            selectedModelId = model.id
            chatViewModel.loadModel(model.id)
            updateModelIcon(model.id)
            hideModelPopup()
        }
        modelPopupList.layoutManager = LinearLayoutManager(requireContext())
        modelPopupList.adapter = modelPopupAdapter
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            val text = inputEditText.text.toString().trim()
            if (text.isNotEmpty()) {
                chatViewModel.sendMessage(text)
                inputEditText.text.clear()
            }
        }

        btnNewChat.setOnClickListener {
            chatViewModel.startNewChat()
            Toast.makeText(requireContext(), "New chat started", Toast.LENGTH_SHORT).show()
        }

        btnModel.setOnClickListener {
            showModelSelector = !showModelSelector
            modelSelectorRecyclerView.visibility = if (showModelSelector) View.VISIBLE else View.GONE
        }

        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrBlank()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Model icon click - show dropdown
        btnModelIcon.setOnClickListener {
            toggleModelPopup()
        }
    }
    
    private fun updateModelIcon(modelId: String?) {
        val iconRes = when {
            modelId == null -> R.drawable.ic_model_default
            modelId.contains("phi", ignoreCase = true) -> R.drawable.ic_model_phi
            modelId.contains("llama", ignoreCase = true) && modelId.contains("tiny", ignoreCase = true) -> R.drawable.ic_model_tinyllama
            modelId.contains("llama", ignoreCase = true) -> R.drawable.ic_model_llama
            modelId.contains("gemma", ignoreCase = true) -> R.drawable.ic_model_gemma
            modelId.contains("qwen", ignoreCase = true) -> R.drawable.ic_model_qwen
            modelId.contains("mistral", ignoreCase = true) -> R.drawable.ic_model_mistral
            modelId.contains("deepseek", ignoreCase = true) -> R.drawable.ic_model_deepseek
            modelId.contains("stablelm", ignoreCase = true) -> R.drawable.ic_model_stablelm
            else -> R.drawable.ic_model_default
        }
        btnModelIcon.setImageResource(iconRes)
    }
    
    private fun toggleModelPopup() {
        showModelPopup = !showModelPopup
        modelPopupCard.visibility = if (showModelPopup) View.VISIBLE else View.GONE
    }
    
    private fun hideModelPopup() {
        showModelPopup = false
        modelPopupCard.visibility = View.GONE
    }
    
    private fun setupModelPopup(models: List<ModelInfo>) {
        // Filter only downloaded models
        loadedModels = models.filter { it.isDownloaded }
        
        if (loadedModels.isEmpty()) {
            return
        }
        
        // Update popup adapter
        modelPopupAdapter.submitList(loadedModels, selectedModelId)
        
        // Set current model
        val currentModelIdValue = chatViewModel.currentModelId.value
        if (currentModelIdValue != null) {
            selectedModelId = currentModelIdValue
        } else if (loadedModels.isNotEmpty()) {
            selectedModelId = loadedModels[0].id
        }
    }
    
    private fun getDisplayName(modelId: String): String {
        return when {
            modelId.contains("phi", ignoreCase = true) -> "Phi-3 Mini"
            modelId.contains("llama", ignoreCase = true) -> "Llama 3.2"
            modelId.contains("gemma", ignoreCase = true) -> "Gemma 2B"
            modelId.contains("qwen", ignoreCase = true) -> "Qwen 2.5"
            modelId.contains("mistral", ignoreCase = true) -> "Mistral 7B"
            modelId.contains("deepseek", ignoreCase = true) -> "DeepSeek"
            modelId.contains("tinyllama", ignoreCase = true) -> "TinyLlama"
            modelId.contains("stablelm", ignoreCase = true) -> "StableLM"
            else -> modelId.substringAfterLast("/").substringBefore(".").take(15)
        }
    }
    
    private fun getModelIcon(modelId: String): Int {
        return when {
            modelId.contains("phi", ignoreCase = true) -> R.drawable.ic_model_phi
            modelId.contains("llama", ignoreCase = true) -> R.drawable.ic_model_llama
            modelId.contains("gemma", ignoreCase = true) -> R.drawable.ic_model_gemma
            modelId.contains("qwen", ignoreCase = true) -> R.drawable.ic_model_qwen
            modelId.contains("mistral", ignoreCase = true) -> R.drawable.ic_model_mistral
            modelId.contains("deepseek", ignoreCase = true) -> R.drawable.ic_model_deepseek
            modelId.contains("tinyllama", ignoreCase = true) -> R.drawable.ic_model_tinyllama
            modelId.contains("stablelm", ignoreCase = true) -> R.drawable.ic_model_stablelm
            else -> R.drawable.ic_model_default
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.messages.collectLatest { messages ->
                messagesAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.statusMessage.collectLatest { message ->
                statusMessage.text = message
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.availableModels.collectLatest { models ->
                modelsAdapter.submitList(models)
                setupModelPopup(models)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.currentModelId.collectLatest { modelId ->
                modelsAdapter.setCurrentModel(modelId)
                selectedModelId = modelId
                modelPopupAdapter.setCurrentModel(modelId)
                updateModelIcon(modelId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.isLoading.collectLatest { isLoading ->
                sendButton.isEnabled = !isLoading && inputEditText.text.isNotBlank()
                updateStatusIndicator(isLoading)
            }
        }
    }

    private fun updateStatusIndicator(isLoading: Boolean) {
        val color = if (isLoading) {
            ContextCompat.getColor(requireContext(), R.color.accentOrange)
        } else {
            ContextCompat.getColor(requireContext(), R.color.accentGreen)
        }
        (statusIndicator.background as? GradientDrawable)?.setColor(color)
    }
}
