package com.runanywhere.startup_hackathon20

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating ViewModels with dependencies from MyApplication.
 */
class ViewModelFactory : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = MyApplication.instance
        
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(
                    assistantController = app.assistantController,
                    messageRepository = app.messageRepository,
                    ttsService = app.ttsService
                ) as T
            }
            modelClass.isAssignableFrom(VoiceViewModel::class.java) -> {
                VoiceViewModel(
                    voiceInputHandler = app.voiceInputHandler
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
