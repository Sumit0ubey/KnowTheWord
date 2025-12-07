package com.runanywhere.startup_hackathon20

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.runanywhere.sdk.models.ModelInfo

/**
 * RecyclerView adapter for displaying available AI models with icons.
 * Shows model-specific icons, download status and allows download/load actions.
 */
class ModelsAdapter(
    private val onDownload: (String) -> Unit,
    private val onLoad: (String) -> Unit
) : ListAdapter<ModelInfo, ModelsAdapter.ModelViewHolder>(ModelDiffCallback()) {

    private var currentModelId: String? = null

    fun setCurrentModel(modelId: String?) {
        currentModelId = modelId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view, onDownload, onLoad)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(getItem(position), currentModelId)
    }

    class ModelViewHolder(
        itemView: View,
        private val onDownload: (String) -> Unit,
        private val onLoad: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val modelIcon: ImageView = itemView.findViewById(R.id.modelIcon)
        private val modelName: TextView = itemView.findViewById(R.id.modelName)
        private val modelSize: TextView = itemView.findViewById(R.id.modelSize)
        private val modelStatus: TextView = itemView.findViewById(R.id.modelStatus)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.actionButton)

        fun bind(model: ModelInfo, currentModelId: String?) {
            // Clean model name (remove emojis)
            val cleanName = getCleanModelName(model.name)
            modelName.text = cleanName
            
            // Set model-specific icon based on model name/id
            val iconRes = getModelIcon(model.name, model.id)
            modelIcon.setImageResource(iconRes)
            
            // Get model size with power rating
            val powerLevel = getModelStars(model.name)
            val powerText = "Power: $powerLevel/5"
            val sizeText = getModelSizeText(model)
            modelSize.text = "$powerText  |  $sizeText"
            
            // Check if model is downloaded
            val isDownloaded = try {
                model.isDownloaded
            } catch (e: Exception) {
                false
            }
            
            val isLoaded = model.id == currentModelId
            
            // Set status and button with proper vertical alignment
            when {
                isLoaded -> {
                    modelStatus.text = "✓ Currently Active"
                    modelStatus.setTextColor(0xFF4CAF50.toInt()) // Green
                    actionButton.text = "Active"
                    actionButton.isEnabled = false
                    actionButton.gravity = android.view.Gravity.CENTER
                }
                isDownloaded -> {
                    modelStatus.text = "Ready to use"
                    modelStatus.setTextColor(0xFF888888.toInt())
                    actionButton.text = "Load"
                    actionButton.isEnabled = true
                    actionButton.gravity = android.view.Gravity.CENTER
                }
                else -> {
                    modelStatus.text = "Not downloaded"
                    modelStatus.setTextColor(0xFFDC143C.toInt()) // Crimson
                    actionButton.text = "Download"
                    actionButton.isEnabled = true
                    actionButton.gravity = android.view.Gravity.CENTER
                }
            }
            
            actionButton.setOnClickListener {
                if (isDownloaded || isLoaded) {
                    onLoad(model.id)
                } else {
                    onDownload(model.id)
                }
            }
            
            itemView.setOnClickListener {
                if (isDownloaded || isLoaded) {
                    onLoad(model.id)
                } else {
                    onDownload(model.id)
                }
            }
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
        
        private fun getModelSizeText(model: ModelInfo): String {
            val lowerName = model.name.lowercase()
            return when {
                lowerName.contains("3b") -> "3B • ~1.8GB"
                lowerName.contains("2b") -> "2B • ~1.2GB"
                lowerName.contains("1b") -> "1B • ~600MB"
                lowerName.contains("7b") -> "7B • ~4GB"
                lowerName.contains("tiny") -> "Tiny • ~500MB"
                lowerName.contains("small") -> "Small • ~800MB"
                else -> "AI Model"
            }
        }
        
        private fun getCleanModelName(name: String): String {
            // Remove emojis and special characters
            val cleaned = name.replace(Regex("[^a-zA-Z0-9\\s.-]"), "").trim()
            
            return when {
                cleaned.lowercase().contains("llama") && cleaned.contains("3.2") -> "Llama 3.2 3B"
                cleaned.lowercase().contains("llama") && cleaned.contains("3b") -> "Llama 3B"
                cleaned.lowercase().contains("tinyllama") -> "TinyLlama"
                cleaned.lowercase().contains("phi") && cleaned.contains("3") -> "Phi-3 Mini"
                cleaned.lowercase().contains("phi") -> "Phi"
                cleaned.lowercase().contains("gemma") && cleaned.contains("2b") -> "Gemma 2B"
                cleaned.lowercase().contains("gemma") -> "Gemma"
                cleaned.lowercase().contains("mistral") -> "Mistral 7B"
                cleaned.lowercase().contains("qwen") -> "Qwen 2"
                else -> cleaned
            }
        }
        
        private fun getModelStars(name: String): Int {
            val lowerName = name.lowercase()
            return when {
                lowerName.contains("7b") -> 5
                lowerName.contains("llama") && lowerName.contains("3b") -> 5
                lowerName.contains("3b") -> 4
                lowerName.contains("phi") -> 4
                lowerName.contains("mistral") -> 4
                lowerName.contains("2b") -> 3
                lowerName.contains("gemma") -> 3
                lowerName.contains("qwen") -> 3
                lowerName.contains("tiny") -> 2
                else -> 3
            }
        }
    }

    private class ModelDiffCallback : DiffUtil.ItemCallback<ModelInfo>() {
        override fun areItemsTheSame(oldItem: ModelInfo, newItem: ModelInfo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ModelInfo, newItem: ModelInfo): Boolean {
            return oldItem == newItem
        }
    }
}
