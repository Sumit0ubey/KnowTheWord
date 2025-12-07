package com.runanywhere.startup_hackathon20

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.runanywhere.startup_hackathon20.domain.model.AttachmentType
import com.runanywhere.startup_hackathon20.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying chat messages with streaming support.
 * Uses direct list management for real-time streaming updates.
 */
class MessagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ASSISTANT = 1
    }
    
    private val messages = mutableListOf<ChatMessage>()

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_assistant, parent, false)
                AssistantMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
        }
    }
    
    /**
     * Submit new list - for normal updates
     */
    fun submitList(newMessages: List<ChatMessage>) {
        val oldSize = messages.size
        val newSize = newMessages.size
        
        messages.clear()
        messages.addAll(newMessages)
        
        // Check if this is a streaming update (last message changed)
        if (oldSize == newSize && newSize > 0) {
            // Just update last item for streaming
            notifyItemChanged(newSize - 1)
        } else if (newSize > oldSize) {
            // New message added
            notifyItemRangeInserted(oldSize, newSize - oldSize)
            if (oldSize > 0) {
                notifyItemChanged(oldSize - 1) // Update previous last item
            }
        } else {
            // Full refresh
            notifyDataSetChanged()
        }
    }
    
    /**
     * Update streaming message directly - for real-time streaming
     */
    fun updateStreamingMessage(text: String) {
        if (messages.isNotEmpty() && !messages.last().isUser) {
            messages[messages.lastIndex] = messages.last().copy(text = text, isStreaming = true)
            notifyItemChanged(messages.lastIndex, "streaming")
        }
    }
    
    /**
     * Finalize streaming message
     */
    fun finalizeStreaming(text: String) {
        if (messages.isNotEmpty() && !messages.last().isUser) {
            messages[messages.lastIndex] = messages.last().copy(text = text, isStreaming = false)
            notifyItemChanged(messages.lastIndex)
        }
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)

        // Attachment views
        private val attachmentContainer: LinearLayout? =
            itemView.findViewById(R.id.attachmentContainer)
        private val attachmentImageCard: CardView? = itemView.findViewById(R.id.attachmentImageCard)
        private val attachmentImage: ImageView? = itemView.findViewById(R.id.attachmentImage)
        private val attachmentDocContainer: LinearLayout? =
            itemView.findViewById(R.id.attachmentDocContainer)
        private val attachmentDocIcon: ImageView? = itemView.findViewById(R.id.attachmentDocIcon)
        private val attachmentDocName: TextView? = itemView.findViewById(R.id.attachmentDocName)
        private val attachmentDocSize: TextView? = itemView.findViewById(R.id.attachmentDocSize)

        fun bind(message: ChatMessage) {
            messageText.text = message.text
            messageTime.text = formatTime(message.timestamp)

            // Handle attachment display
            if (message.hasAttachment() && attachmentContainer != null) {
                attachmentContainer.visibility = View.VISIBLE
                val attachment = message.attachment!!

                when (attachment.type) {
                    AttachmentType.IMAGE -> {
                        // Show image thumbnail
                        attachmentImageCard?.visibility = View.VISIBLE
                        attachmentDocContainer?.visibility = View.GONE

                        if (attachment.thumbnail != null) {
                            attachmentImage?.setImageBitmap(attachment.thumbnail)
                        } else {
                            attachmentImage?.setImageResource(R.drawable.ic_document)
                        }
                    }

                    AttachmentType.PDF, AttachmentType.TEXT_FILE, AttachmentType.DOCUMENT -> {
                        // Show document info
                        attachmentImageCard?.visibility = View.GONE
                        attachmentDocContainer?.visibility = View.VISIBLE

                        attachmentDocName?.text = attachment.fileName
                        attachmentDocSize?.text = formatFileSize(attachment.fileSizeKB)

                        // Set appropriate icon
                        val iconRes = when (attachment.type) {
                            AttachmentType.PDF -> R.drawable.ic_document
                            AttachmentType.TEXT_FILE -> R.drawable.ic_document
                            else -> R.drawable.ic_document
                        }
                        attachmentDocIcon?.setImageResource(iconRes)
                    }
                }
            } else {
                attachmentContainer?.visibility = View.GONE
            }

            itemView.setOnLongClickListener {
                copyToClipboard(itemView.context, message.text)
                true
            }
        }

        private fun formatFileSize(sizeKB: Long): String {
            return when {
                sizeKB < 1024 -> "${sizeKB} KB"
                else -> String.format("%.1f MB", sizeKB / 1024.0)
            }
        }
    }

    class AssistantMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        
        private var typingAnimator: android.animation.ValueAnimator? = null
        private var lastAnimatedText: String? = null

        fun bind(message: ChatMessage) {
            // Cancel any ongoing animation
            typingAnimator?.cancel()
            
            if (message.isStreaming) {
                // Show "Thinking..." or streaming text
                messageText.text = message.text
                messageTime.visibility = View.GONE
                copyButton.visibility = View.GONE
            } else {
                // Check if this is a new message that needs typing animation
                val fullText = message.text
                val formattedText = parseMarkdown(fullText)
                
                if (lastAnimatedText != fullText && fullText.length > 10) {
                    // Start typing animation with markdown
                    startTypingAnimation(fullText, formattedText)
                    lastAnimatedText = fullText
                } else {
                    messageText.text = formattedText
                }
                
                messageTime.visibility = View.VISIBLE
                copyButton.visibility = View.VISIBLE
                messageTime.text = formatTime(message.timestamp)
            }
            
            copyButton.setOnClickListener {
                copyToClipboard(itemView.context, message.text.replace("▌", ""))
            }
            
            itemView.setOnLongClickListener {
                copyToClipboard(itemView.context, message.text.replace("▌", ""))
                true
            }
        }
        
        private fun startTypingAnimation(fullText: String, formattedText: CharSequence) {
            val words = fullText.split(" ")
            val totalWords = words.size
            
            // Calculate duration based on text length (faster for longer texts)
            val durationPerWord = when {
                totalWords > 100 -> 15L
                totalWords > 50 -> 20L
                else -> 30L
            }
            val totalDuration = (totalWords * durationPerWord).coerceAtMost(3000L)
            
            typingAnimator = android.animation.ValueAnimator.ofInt(0, totalWords).apply {
                duration = totalDuration
                interpolator = android.view.animation.LinearInterpolator()
                
                addUpdateListener { animator ->
                    val wordCount = animator.animatedValue as Int
                    val displayText = if (wordCount < totalWords) {
                        val partialText = words.take(wordCount).joinToString(" ") + " ▌"
                        parseMarkdown(partialText)
                    } else {
                        formattedText
                    }
                    messageText.text = displayText
                }
                
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        messageText.text = formattedText
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        messageText.text = formattedText
                    }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                
                start()
            }
        }
        
        /**
         * Parse markdown to SpannableString
         * Supports: **bold**, *italic*, `code`, ***bold italic***
         */
        private fun parseMarkdown(text: String): CharSequence {
            val spannable = SpannableStringBuilder()
            var currentIndex = 0
            val cleanText = text
            
            // Process text character by character
            var i = 0
            while (i < cleanText.length) {
                when {
                    // Bold + Italic: ***text***
                    cleanText.startsWith("***", i) -> {
                        val endIndex = cleanText.indexOf("***", i + 3)
                        if (endIndex != -1) {
                            val content = cleanText.substring(i + 3, endIndex)
                            val start = spannable.length
                            spannable.append(content)
                            spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = endIndex + 3
                        } else {
                            spannable.append(cleanText[i])
                            i++
                        }
                    }
                    // Bold: **text**
                    cleanText.startsWith("**", i) -> {
                        val endIndex = cleanText.indexOf("**", i + 2)
                        if (endIndex != -1) {
                            val content = cleanText.substring(i + 2, endIndex)
                            val start = spannable.length
                            spannable.append(content)
                            spannable.setSpan(StyleSpan(Typeface.BOLD), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = endIndex + 2
                        } else {
                            spannable.append(cleanText[i])
                            i++
                        }
                    }
                    // Italic: *text*
                    cleanText[i] == '*' && (i == 0 || cleanText[i-1] != '*') && i + 1 < cleanText.length && cleanText[i+1] != '*' -> {
                        val endIndex = cleanText.indexOf('*', i + 1)
                        if (endIndex != -1 && endIndex > i + 1) {
                            val content = cleanText.substring(i + 1, endIndex)
                            val start = spannable.length
                            spannable.append(content)
                            spannable.setSpan(StyleSpan(Typeface.ITALIC), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = endIndex + 1
                        } else {
                            spannable.append(cleanText[i])
                            i++
                        }
                    }
                    // Code: `text`
                    cleanText[i] == '`' -> {
                        val endIndex = cleanText.indexOf('`', i + 1)
                        if (endIndex != -1) {
                            val content = cleanText.substring(i + 1, endIndex)
                            val start = spannable.length
                            spannable.append(content)
                            spannable.setSpan(ForegroundColorSpan(0xFFDC143C.toInt()), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            spannable.setSpan(android.text.style.BackgroundColorSpan(0xFF2A2A2A.toInt()), start, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = endIndex + 1
                        } else {
                            spannable.append(cleanText[i])
                            i++
                        }
                    }
                    else -> {
                        spannable.append(cleanText[i])
                        i++
                    }
                }
            }
            
            return spannable
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
