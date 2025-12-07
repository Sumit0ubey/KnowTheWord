package com.runanywhere.startup_hackathon20.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Image Analyzer using Google ML Kit
 *
 * Provides ACTUAL image understanding:
 * - Object/Scene detection (cat, dog, food, landscape, etc.)
 * - Text extraction (OCR) from images
 * - Face detection and counting
 *
 * This gives the LLM real information about what's IN the image!
 */
class ImageAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "ImageAnalyzer"
        private const val MIN_CONFIDENCE = 0.6f  // 60% confidence threshold
    }

    // ML Kit Image Labeler (object/scene detection)
    private val imageLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(MIN_CONFIDENCE)
            .build()
        ImageLabeling.getClient(options)
    }

    // ML Kit Text Recognizer (OCR)
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // ML Kit Face Detector - ACCURATE mode for better detection
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // Better detection!
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)  // Faster
            .setMinFaceSize(0.1f)  // Detect smaller faces (10% of image)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Analyze an image and return a detailed description.
     * This description can be sent to the LLM for intelligent responses.
     */
    suspend fun analyzeImage(uri: Uri): ImageAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting image analysis for: $uri")
                val startTime = System.currentTimeMillis()

                // Load bitmap
                val bitmap = loadBitmap(uri)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load bitmap")
                    return@withContext ImageAnalysisResult(
                        success = false,
                        error = "Could not load image"
                    )
                }

                val inputImage = InputImage.fromBitmap(bitmap, 0)

                // Run all analyses in parallel-ish (they're async)
                val labels = detectLabels(inputImage)
                val text = extractText(inputImage)
                val faces = detectFaces(inputImage)

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Image analysis completed in ${elapsed}ms")

                // Build comprehensive description
                val description = buildDescription(
                    labels = labels,
                    text = text,
                    faceCount = faces,
                    width = bitmap.width,
                    height = bitmap.height
                )

                ImageAnalysisResult(
                    success = true,
                    description = description,
                    labels = labels,
                    extractedText = text,
                    faceCount = faces,
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisTimeMs = elapsed
                )

            } catch (e: Exception) {
                Log.e(TAG, "Image analysis failed: ${e.message}", e)
                ImageAnalysisResult(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Analyze a bitmap directly.
     */
    suspend fun analyzeImage(bitmap: Bitmap): ImageAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting bitmap analysis")
                val startTime = System.currentTimeMillis()

                val inputImage = InputImage.fromBitmap(bitmap, 0)

                val labels = detectLabels(inputImage)
                val text = extractText(inputImage)
                val faces = detectFaces(inputImage)

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Bitmap analysis completed in ${elapsed}ms")

                val description = buildDescription(
                    labels = labels,
                    text = text,
                    faceCount = faces,
                    width = bitmap.width,
                    height = bitmap.height
                )

                ImageAnalysisResult(
                    success = true,
                    description = description,
                    labels = labels,
                    extractedText = text,
                    faceCount = faces,
                    width = bitmap.width,
                    height = bitmap.height,
                    analysisTimeMs = elapsed
                )

            } catch (e: Exception) {
                Log.e(TAG, "Bitmap analysis failed: ${e.message}", e)
                ImageAnalysisResult(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Detect objects and scenes in the image.
     */
    private suspend fun detectLabels(image: InputImage): List<DetectedLabel> {
        return try {
            val labels = imageLabeler.process(image).await()
            labels.map { label ->
                DetectedLabel(
                    name = label.text,
                    confidence = label.confidence
                )
            }.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Label detection failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract text from the image (OCR).
     */
    private suspend fun extractText(image: InputImage): String {
        return try {
            val result = textRecognizer.process(image).await()
            result.text.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed: ${e.message}")
            ""
        }
    }

    /**
     * Detect faces in the image.
     */
    private suspend fun detectFaces(image: InputImage): Int {
        return try {
            val faces = faceDetector.process(image).await()
            faces.size
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}")
            0
        }
    }

    /**
     * Load and resize bitmap from URI.
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, get dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate sample size for max 2048px (larger = better face detection)
                val maxDimension = 2048
                var sampleSize = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / sampleSize) >= maxDimension &&
                        (halfWidth / sampleSize) >= maxDimension
                    ) {
                        sampleSize *= 2
                    }
                }

                // Decode with sample size
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap: ${e.message}")
            null
        }
    }

    /**
     * Build description - IMPROVED LOGIC:
     * 1. Face OR person labels → Personal Photo (with count)
     * 2. Text (50+ chars) → Document
     * 3. Scene labels → Photo
     */
    private fun buildDescription(
        labels: List<DetectedLabel>,
        text: String,
        faceCount: Int,
        width: Int,
        height: Int
    ): String {
        val cleanText = text.trim()
        val labelNames = labels.map { it.name.lowercase() }

        // Check if labels indicate people (backup for face detection)
        val hasPersonLabel = labelNames.any {
            it.contains("person") || it.contains("people") ||
                    it.contains("crowd") || it.contains("audience") ||
                    it.contains("group") || it.contains("team")
        }

        Log.d(TAG, "=== ANALYZING IMAGE ===")
        Log.d(
            TAG,
            "Faces: $faceCount, PersonLabel: $hasPersonLabel, Text: ${cleanText.length}, Labels: $labelNames"
        )

        // 1. PEOPLE DETECTED (faces OR person labels)
        if (faceCount > 0 || hasPersonLabel) {
            // Use face count if available, otherwise estimate from labels
            val peopleDescription = when {
                faceCount >= 5 -> "many people (detected $faceCount faces)"
                faceCount > 0 -> "$faceCount person(s)"
                labelNames.any { it.contains("crowd") || it.contains("audience") } -> "a crowd/group of people"
                labelNames.any { it.contains("group") || it.contains("team") } -> "a group of people"
                else -> "people"
            }
            Log.d(TAG, "RESULT: Personal Photo - $peopleDescription")
            return "IMAGE TYPE: Personal Photo\nPeople detected: $peopleDescription"
        }

        // 2. TEXT = DOCUMENT
        if (cleanText.length >= 50) {
            Log.d(TAG, "RESULT: Document (${cleanText.length} chars text)")
            return "IMAGE TYPE: Document\nTEXT CONTENT:\n$cleanText"
        }

        // 3. LABELS = SCENE/OBJECT PHOTO
        if (labels.isNotEmpty()) {
            Log.d(TAG, "RESULT: Photo/Scene")
            val labelList = labels.take(6).joinToString(", ") { it.name }
            return "IMAGE TYPE: Photo\nDETECTED: $labelList"
        }

        // 4. NOTHING
        return "IMAGE TYPE: Unknown\nNo content detected"
    }

    /**
     * Quick analysis - just labels, faster.
     */
    suspend fun quickAnalyze(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(uri) ?: return@withContext "Could not load image"
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val labels = detectLabels(inputImage)

                if (labels.isEmpty()) {
                    "Image uploaded (no specific objects detected)"
                } else {
                    "Image contains: ${labels.take(3).joinToString(", ") { it.name }}"
                }
            } catch (e: Exception) {
                "Image uploaded"
            }
        }
    }
}

/**
 * Result of image analysis.
 */
data class ImageAnalysisResult(
    val success: Boolean,
    val description: String = "",
    val labels: List<DetectedLabel> = emptyList(),
    val extractedText: String = "",
    val faceCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val analysisTimeMs: Long = 0,
    val error: String? = null
)

/**
 * A detected label (object/scene) with confidence.
 */
data class DetectedLabel(
    val name: String,
    val confidence: Float
)
