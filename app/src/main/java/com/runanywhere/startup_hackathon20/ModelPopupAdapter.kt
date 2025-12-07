package com.runanywhere.startup_hackathon20

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.runanywhere.sdk.models.ModelInfo

/**
 * Simple adapter for model selection popup in chat input
 */
class ModelPopupAdapter(
    private val onModelSelected: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelPopupAdapter.ViewHolder>() {

    private var models: List<ModelInfo> = emptyList()
    private var currentModelId: String? = null

    fun submitList(newModels: List<ModelInfo>, selectedId: String? = null) {
        models = newModels
        currentModelId = selectedId
        notifyDataSetChanged()
    }

    fun setCurrentModel(modelId: String?) {
        currentModelId = modelId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(models[position])
    }

    override fun getItemCount(): Int = models.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modelIcon: ImageView = itemView.findViewById(R.id.modelIcon)
        private val modelName: TextView = itemView.findViewById(R.id.modelName)
        private val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)
        private val powerLevel: TextView = itemView.findViewById(R.id.powerLevel)

        fun bind(model: ModelInfo) {
            // Use model.name like HomeFragment does, with clean name extraction
            modelName.text = getCleanModelName(model.name)
            modelIcon.setImageResource(getModelIcon(model.name, model.id))

            // Set power level text
            val power = getModelStars(model.name)
            powerLevel.text = "Power: $power/5"
            
            // Show check for current model
            val isSelected = model.id == currentModelId
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            itemView.setOnClickListener {
                onModelSelected(model)
            }
        }

        /**
         * Clean model name - same logic as HomeFragment
         */
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
                cleaned.lowercase().contains("deepseek") -> "DeepSeek"
                cleaned.lowercase().contains("stablelm") -> "StableLM"
                else -> cleaned.take(15)
            }
        }
        
        /**
         * Get star rating based on model power - same logic as HomeFragment
         */
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
                lowerName.contains("deepseek") || lowerId.contains("deepseek") -> R.drawable.ic_model_deepseek
                lowerName.contains("stablelm") || lowerId.contains("stablelm") -> R.drawable.ic_model_stablelm
                else -> R.drawable.ic_model_default
            }
        }
    }
}
