package com.runanywhere.startup_hackathon20.domain.model

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName

/**
 * Types of attachments that can be included in a message.
 */
enum class AttachmentType {
    IMAGE,
    PDF,
    TEXT_FILE,
    DOCUMENT
}

/**
 * Represents an attachment in a chat message.
 */
data class MessageAttachment(
    @SerializedName("type")
    val type: AttachmentType,

    @SerializedName("fileName")
    val fileName: String,

    @SerializedName("fileSizeKB")
    val fileSizeKB: Long = 0,

    @SerializedName("extractedText")
    val extractedText: String? = null,

    @SerializedName("pageCount")
    val pageCount: Int = 1,

    @SerializedName("uriString")
    val uriString: String? = null,

    // Thumbnail bitmap - not persisted (transient)
    @Transient
    val thumbnail: Bitmap? = null
)

/**
 * Represents a single message in the chat conversation.
 * Supports JSON serialization for persistence.
 * Now supports file attachments (images, PDFs, documents).
 */
data class ChatMessage(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("text")
    val text: String,

    @SerializedName("isUser")
    val isUser: Boolean,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("metadata")
    val metadata: MessageMetadata? = null,

    @SerializedName("attachment")
    val attachment: MessageAttachment? = null,

    // For streaming display - not persisted
    @Transient
    val isStreaming: Boolean = false
) {
    /**
     * Returns true if this message has an attachment.
     */
    fun hasAttachment(): Boolean = attachment != null

    /**
     * Returns true if this is an image attachment.
     */
    fun isImageAttachment(): Boolean = attachment?.type == AttachmentType.IMAGE

    /**
     * Returns true if this is a document attachment.
     */
    fun isDocumentAttachment(): Boolean = attachment?.type in listOf(
        AttachmentType.PDF,
        AttachmentType.TEXT_FILE,
        AttachmentType.DOCUMENT
    )
}

/**
 * Additional metadata associated with a chat message.
 */
data class MessageMetadata(
    @SerializedName("actionIntent")
    val actionIntent: ActionIntent? = null,
    
    @SerializedName("transcriptionConfidence")
    val transcriptionConfidence: Float? = null
)
