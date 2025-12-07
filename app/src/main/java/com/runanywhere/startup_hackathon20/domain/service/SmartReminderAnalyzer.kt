package com.runanywhere.startup_hackathon20.domain.service

import android.util.Log
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.models.RunAnywhereGenerationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Result of smart reminder analysis.
 */
data class ReminderAnalysisResult(
    val isReminderRequest: Boolean,
    val task: String?,
    val dateTime: String?,          // Raw extracted date/time string
    val parsedTimeMs: Long?,        // Parsed timestamp in milliseconds
    val confidence: Float,          // 0.0 to 1.0 confidence score
    val rawResponse: String = ""    // Raw AI response for debugging
)

/**
 * Smart Reminder Analyzer
 *
 * Uses AI to understand natural language reminder requests.
 * Handles both English and Hindi (Hinglish) inputs.
 *
 * Examples it can understand:
 * - "yaad dilana ki kal meeting hai 3 baje"
 * - "remind me about doctor appointment tomorrow morning"
 * - "mujhe parso 5 baje gym jaana hai remind karna"
 * - "set a reminder for mom's birthday next week"
 * - "please remind me to call John at 4pm"
 */
class SmartReminderAnalyzer {

    companion object {
        private const val TAG = "SmartReminderAnalyzer"

        // ===== Reminder Intent Keywords (for quick pre-check) =====
        private val REMINDER_INDICATORS = listOf(
            // English
            "remind", "reminder", "remember", "don't forget", "alert me",
            "notify me", "tell me to", "set alarm", "schedule",
            // Hindi/Hinglish
            "yaad", "yaad dilana", "yaad karna", "batana", "bata dena",
            "alert karna", "notification", "bhool na", "bhulna mat"
        )

        // ===== Time-related keywords (hints that there's a time component) =====
        private val TIME_INDICATORS = listOf(
            // English
            "today", "tomorrow", "yesterday", "morning", "afternoon",
            "evening", "night", "am", "pm", "o'clock", "hour", "minute",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "next week", "next month", "at", "on", "by",
            // Hindi/Hinglish
            "kal", "parso", "aaj", "subah", "dopahar", "shaam", "raat",
            "baje", "ghante", "minute", "somvar", "mangalvar", "budhvar",
            "guruvar", "shukravar", "shanivar", "ravivar", "hafta", "mahina"
        )

        // ===== Hindi Time Mappings =====
        val HINDI_DAY_OFFSETS = mapOf(
            "aaj" to 0,
            "kal" to 1,
            "parso" to 2,
            "narso" to 3,
            "tarso" to 3
        )

        val HINDI_TIME_PERIODS = mapOf(
            "subah" to 9,       // 9 AM
            "dopahar" to 13,    // 1 PM
            "shaam" to 18,      // 6 PM
            "raat" to 21        // 9 PM
        )

        val ENGLISH_TIME_PERIODS = mapOf(
            "morning" to 9,
            "afternoon" to 14,
            "evening" to 18,
            "night" to 21
        )
    }

    /**
     * Analyzes user input to determine if it's a reminder request.
     * Uses quick heuristics first, then AI for complex cases.
     *
     * @param input User's natural language input
     * @param useAI Whether to use AI for analysis (requires loaded model)
     * @return ReminderAnalysisResult with extracted information
     */
    suspend fun analyze(input: String, useAI: Boolean = true): ReminderAnalysisResult {
        val normalizedInput = input.lowercase().trim()

        Log.d(TAG, "Analyzing input: $normalizedInput")

        // Step 1: Quick heuristic check
        val hasReminderIndicator = REMINDER_INDICATORS.any { normalizedInput.contains(it) }
        val hasTimeIndicator = TIME_INDICATORS.any { normalizedInput.contains(it) }

        Log.d(
            TAG,
            "Has reminder indicator: $hasReminderIndicator, Has time indicator: $hasTimeIndicator"
        )

        // Step 2: If both indicators present, high confidence - try rule-based extraction first
        if (hasReminderIndicator && hasTimeIndicator) {
            val ruleBasedResult = extractReminderRuleBased(normalizedInput)
            if (ruleBasedResult.isReminderRequest && ruleBasedResult.confidence >= 0.7f) {
                Log.d(TAG, "Rule-based extraction successful: ${ruleBasedResult.task}")
                return ruleBasedResult
            }
        }

        // Step 3: Use AI for complex/ambiguous cases
        if (useAI && (hasReminderIndicator || hasTimeIndicator)) {
            val aiResult = analyzeWithAI(normalizedInput)
            if (aiResult.isReminderRequest) {
                Log.d(TAG, "AI extraction successful: ${aiResult.task}")
                return aiResult
            }
        }

        // Step 4: If has reminder indicator but no time, still might be a reminder
        if (hasReminderIndicator) {
            val task = extractTaskOnly(normalizedInput)
            if (task.isNotBlank()) {
                return ReminderAnalysisResult(
                    isReminderRequest = true,
                    task = task,
                    dateTime = null,
                    parsedTimeMs = getDefaultReminderTime(),
                    confidence = 0.6f
                )
            }
        }

        return ReminderAnalysisResult(
            isReminderRequest = false,
            task = null,
            dateTime = null,
            parsedTimeMs = null,
            confidence = 0.0f
        )
    }

    /**
     * Rule-based reminder extraction for common patterns.
     */
    private fun extractReminderRuleBased(input: String): ReminderAnalysisResult {
        // Pattern 1: "remind me to [task] at/on [time]"
        val englishPattern1 = Regex(
            "(?:remind(?:er)?|remember|alert)\\s*(?:me)?\\s*(?:to|about|of)?\\s*(.+?)\\s*(?:at|on|by|in)\\s*(.+)",
            RegexOption.IGNORE_CASE
        )

        // Pattern 2: "[task] remind/yaad [time]"
        val hindiPattern1 = Regex(
            "(?:mujhe|muje|muze)?\\s*(.+?)\\s*(?:yaad|remind|batana|bata)\\s*(?:dilana|karna|dena|kar)?\\s*(.+)?",
            RegexOption.IGNORE_CASE
        )

        // Pattern 3: "yaad dilana ki [task] [time]"
        val hindiPattern2 = Regex(
            "(?:yaad|remind)\\s*(?:dilana|karna|kar)?\\s*(?:ki|ke|ka)?\\s*(.+?)\\s*(?:hai|he|h)?\\s*(\\d+\\s*baje|kal|parso|aaj|subah|shaam|raat)?",
            RegexOption.IGNORE_CASE
        )

        // Pattern 4: "set reminder for [task] [time]"
        val setPattern = Regex(
            "set\\s*(?:a)?\\s*reminder\\s*(?:for|to|about)?\\s*(.+?)\\s*(?:at|on|by)?\\s*(.+)?",
            RegexOption.IGNORE_CASE
        )

        // Try English pattern
        englishPattern1.find(input)?.let { match ->
            val task = match.groupValues[1].trim()
            val timeStr = match.groupValues[2].trim()
            val parsedTime = parseTimeString(timeStr)

            if (task.isNotBlank()) {
                return ReminderAnalysisResult(
                    isReminderRequest = true,
                    task = cleanTask(task),
                    dateTime = timeStr,
                    parsedTimeMs = parsedTime,
                    confidence = 0.85f
                )
            }
        }

        // Try set pattern
        setPattern.find(input)?.let { match ->
            val task = match.groupValues[1].trim()
            val timeStr = match.groupValues.getOrNull(2)?.trim() ?: ""
            val parsedTime =
                if (timeStr.isNotBlank()) parseTimeString(timeStr) else getDefaultReminderTime()

            if (task.isNotBlank()) {
                return ReminderAnalysisResult(
                    isReminderRequest = true,
                    task = cleanTask(task),
                    dateTime = timeStr.ifBlank { null },
                    parsedTimeMs = parsedTime,
                    confidence = 0.85f
                )
            }
        }

        // Try Hindi patterns
        hindiPattern2.find(input)?.let { match ->
            val task = match.groupValues[1].trim()
            val timeStr = match.groupValues.getOrNull(2)?.trim() ?: ""
            val parsedTime =
                if (timeStr.isNotBlank()) parseTimeString(timeStr) else getDefaultReminderTime()

            if (task.isNotBlank() && task.length > 2) {
                return ReminderAnalysisResult(
                    isReminderRequest = true,
                    task = cleanTask(task),
                    dateTime = timeStr.ifBlank { null },
                    parsedTimeMs = parsedTime,
                    confidence = 0.8f
                )
            }
        }

        hindiPattern1.find(input)?.let { match ->
            val task = match.groupValues[1].trim()
            val timeStr = match.groupValues.getOrNull(2)?.trim() ?: ""
            val parsedTime =
                if (timeStr.isNotBlank()) parseTimeString(timeStr) else getDefaultReminderTime()

            if (task.isNotBlank() && task.length > 2) {
                return ReminderAnalysisResult(
                    isReminderRequest = true,
                    task = cleanTask(task),
                    dateTime = timeStr.ifBlank { null },
                    parsedTimeMs = parsedTime,
                    confidence = 0.75f
                )
            }
        }

        return ReminderAnalysisResult(
            isReminderRequest = false,
            task = null,
            dateTime = null,
            parsedTimeMs = null,
            confidence = 0.0f
        )
    }

    /**
     * Uses AI to analyze complex reminder requests.
     */
    private suspend fun analyzeWithAI(input: String): ReminderAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildAnalysisPrompt(input)

                val options = RunAnywhereGenerationOptions(
                    maxTokens = 100,
                    temperature = 0.3f,  // Low temperature for consistent extraction
                    topP = 0.9f,
                    stopSequences = listOf("\n\n", "---"),
                    streamingEnabled = false
                )

                val responseBuilder = StringBuilder()

                try {
                    RunAnywhere.generateStream(prompt, options).collect { token ->
                        responseBuilder.append(token)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "AI generation error: ${e.message}")
                    return@withContext ReminderAnalysisResult(
                        isReminderRequest = false,
                        task = null,
                        dateTime = null,
                        parsedTimeMs = null,
                        confidence = 0.0f,
                        rawResponse = "Error: ${e.message}"
                    )
                }

                val response = responseBuilder.toString().trim()
                Log.d(TAG, "AI Response: $response")

                parseAIResponse(response, input)

            } catch (e: Exception) {
                Log.e(TAG, "AI analysis error: ${e.message}")
                ReminderAnalysisResult(
                    isReminderRequest = false,
                    task = null,
                    dateTime = null,
                    parsedTimeMs = null,
                    confidence = 0.0f,
                    rawResponse = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Builds the prompt for AI analysis.
     */
    private fun buildAnalysisPrompt(input: String): String {
        return """Extract reminder info from this text. Reply in format:
REMINDER: yes/no
TASK: [task description]
TIME: [time expression]

Text: "$input"

REMINDER:"""
    }

    /**
     * Parses the AI response to extract reminder information.
     */
    private fun parseAIResponse(response: String, originalInput: String): ReminderAnalysisResult {
        val lines = response.lowercase().split("\n")

        var isReminder = false
        var task: String? = null
        var timeStr: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("reminder:") -> {
                    val value = trimmedLine.substringAfter(":").trim()
                    isReminder = value == "yes" || value == "true" || value == "1"
                }

                trimmedLine.startsWith("task:") -> {
                    task = trimmedLine.substringAfter(":").trim()
                        .removeSurrounding("[", "]")
                        .removeSurrounding("\"")
                    if (task == "none" || task == "null" || task.isBlank()) {
                        task = null
                    }
                }

                trimmedLine.startsWith("time:") -> {
                    timeStr = trimmedLine.substringAfter(":").trim()
                        .removeSurrounding("[", "]")
                        .removeSurrounding("\"")
                    if (timeStr == "none" || timeStr == "null" || timeStr.isBlank()) {
                        timeStr = null
                    }
                }
                // Handle single-line "yes" response
                trimmedLine == "yes" || trimmedLine.startsWith("yes") -> {
                    isReminder = true
                }
            }
        }

        // If AI says yes but didn't extract task, try to extract from original
        if (isReminder && task == null) {
            task = extractTaskOnly(originalInput)
        }

        val parsedTime = timeStr?.let { parseTimeString(it) } ?: getDefaultReminderTime()

        return ReminderAnalysisResult(
            isReminderRequest = isReminder && !task.isNullOrBlank(),
            task = task?.let { cleanTask(it) },
            dateTime = timeStr,
            parsedTimeMs = if (isReminder) parsedTime else null,
            confidence = if (isReminder && !task.isNullOrBlank()) 0.8f else 0.3f,
            rawResponse = response
        )
    }

    /**
     * Extracts just the task from input when time parsing fails.
     */
    private fun extractTaskOnly(input: String): String {
        var task = input

        // Remove common reminder prefixes
        val prefixes = listOf(
            "remind me to", "remind me about", "remind me of", "remind me",
            "reminder to", "reminder for", "reminder about",
            "remember to", "don't forget to", "alert me to",
            "set reminder for", "set a reminder for", "set reminder to",
            "mujhe yaad dilana", "yaad dilana ki", "yaad dilana",
            "mujhe batana", "mujhe remind karna", "yaad karna"
        )

        for (prefix in prefixes) {
            if (task.lowercase().contains(prefix)) {
                task = task.lowercase().substringAfter(prefix).trim()
                break
            }
        }

        // Remove time expressions from end
        val timePatterns = listOf(
            Regex("\\s*(?:at|on|by|in)\\s+.+$", RegexOption.IGNORE_CASE),
            Regex("\\s*(?:kal|parso|aaj|subah|shaam|raat).*$", RegexOption.IGNORE_CASE),
            Regex("\\s*\\d+\\s*(?:baje|am|pm).*$", RegexOption.IGNORE_CASE)
        )

        for (pattern in timePatterns) {
            task = task.replace(pattern, "")
        }

        return cleanTask(task)
    }

    /**
     * Cleans up extracted task text.
     */
    private fun cleanTask(task: String): String {
        return task
            .replace(Regex("^(to|about|of|ki|ke|ka)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*(hai|he|h)$", RegexOption.IGNORE_CASE), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /**
     * Parses a time string into milliseconds since epoch.
     * Supports both English and Hindi time expressions.
     */
    fun parseTimeString(timeStr: String): Long {
        val normalizedTime = timeStr.lowercase().trim()
        val calendar = Calendar.getInstance()

        Log.d(TAG, "Parsing time: $normalizedTime")

        // Reset to start of day first
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Check for Hindi day offsets (kal, parso, etc.)
        for ((hindi, offset) in HINDI_DAY_OFFSETS) {
            if (normalizedTime.contains(hindi)) {
                calendar.add(Calendar.DAY_OF_MONTH, offset)
                break
            }
        }

        // Check for English day references
        when {
            normalizedTime.contains("tomorrow") -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            normalizedTime.contains("day after tomorrow") -> calendar.add(Calendar.DAY_OF_MONTH, 2)
            normalizedTime.contains("next week") -> calendar.add(Calendar.DAY_OF_MONTH, 7)
        }

        // Check for weekday names
        val weekdays = mapOf(
            "monday" to Calendar.MONDAY, "somvar" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY, "mangalvar" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "budhvar" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY, "guruvar" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "shukravar" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY, "shanivar" to Calendar.SATURDAY,
            "sunday" to Calendar.SUNDAY, "ravivar" to Calendar.SUNDAY
        )

        for ((day, calendarDay) in weekdays) {
            if (normalizedTime.contains(day)) {
                val today = calendar.get(Calendar.DAY_OF_WEEK)
                var daysUntil = calendarDay - today
                if (daysUntil <= 0) daysUntil += 7  // Next occurrence
                calendar.add(Calendar.DAY_OF_MONTH, daysUntil)
                break
            }
        }

        // Extract hour from patterns like "3 baje", "3pm", "15:00"
        var hourSet = false

        // Pattern 1: "X am/pm" or "X:XX am/pm" - CHECK THIS FIRST (highest priority)
        val amPmPattern =
            Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)", RegexOption.IGNORE_CASE)
        amPmPattern.find(normalizedTime)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[3].lowercase().replace(".", "")

            Log.d(TAG, "Parsed AM/PM pattern: hour=$hour, minute=$minute, amPm=$amPm")

            if (amPm == "pm" && hour != 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            hourSet = true
            Log.d(TAG, "Set time to: $hour:$minute (24-hour format)")
        }

        // Pattern 2: "X baje" (Hindi) - only if AM/PM not already matched
        if (!hourSet) {
            val bajePattern = Regex("(\\d{1,2})\\s*baje")
            bajePattern.find(normalizedTime)?.let { match ->
                var hour = match.groupValues[1].toInt()
                // Assume PM if between 1-11 and no AM indicator (subah)
                if (hour in 1..11 && !normalizedTime.contains("subah")) {
                    // Check context for morning/evening
                    if (normalizedTime.contains("shaam") || normalizedTime.contains("raat") || hour <= 6) {
                        if (hour != 12) hour += 12
                    }
                }
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, 0)
                hourSet = true
                Log.d(TAG, "Parsed baje pattern: hour=$hour")
            }
        }

        // Pattern: "HH:MM" 24-hour
        val time24Pattern = Regex("(\\d{1,2}):(\\d{2})")
        if (!hourSet) {
            time24Pattern.find(normalizedTime)?.let { match ->
                val hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                hourSet = true
            }
        }

        // If no specific hour, use time period defaults
        if (!hourSet) {
            // Hindi time periods
            for ((period, hour) in HINDI_TIME_PERIODS) {
                if (normalizedTime.contains(period)) {
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, 0)
                    hourSet = true
                    break
                }
            }

            // English time periods
            if (!hourSet) {
                for ((period, hour) in ENGLISH_TIME_PERIODS) {
                    if (normalizedTime.contains(period)) {
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, 0)
                        hourSet = true
                        break
                    }
                }
            }
        }

        // Default to 1 hour from now if no time specified
        if (!hourSet) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        // If time is in the past, move to next day
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        Log.d(TAG, "Parsed time result: ${calendar.time}")
        return calendar.timeInMillis
    }

    /**
     * Returns default reminder time (1 hour from now).
     */
    private fun getDefaultReminderTime(): Long {
        return System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour
    }

    /**
     * Quick check if input might be a reminder (for fast-path decisions).
     */
    fun mightBeReminder(input: String): Boolean {
        val normalized = input.lowercase()
        return REMINDER_INDICATORS.any { normalized.contains(it) } ||
                (TIME_INDICATORS.count { normalized.contains(it) } >= 2)
    }
}
