package com.runanywhere.startup_hackathon20.domain.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Represents the current state of TTS playback.
 */
sealed class TTSState {
    /** TTS is idle and ready to speak */
    object Idle : TTSState()
    
    /** TTS is currently speaking */
    object Speaking : TTSState()
    
    /** TTS has completed speaking the current utterance */
    object Completed : TTSState()
    
    /** TTS encountered an error */
    data class Error(val message: String) : TTSState()
}

/**
 * Interface for Text-to-Speech service.
 * Wraps Android TTS for speaking assistant responses.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
interface TTSService {
    /**
     * Speaks the given text aloud.
     * Returns a Flow that emits state changes during playback.
     * 
     * @param text The text to speak
     * @return Flow of TTSState updates
     */
    fun speak(text: String): Flow<TTSState>
    
    /**
     * Stops any ongoing speech immediately.
     * Requirement 3.3: WHEN the user taps a stop button during TTS playback,
     * THE Translexa app SHALL immediately stop audio output
     */
    fun stop()
    
    /**
     * Checks if TTS auto-speak is enabled.
     * 
     * @return true if TTS is enabled
     */
    fun isEnabled(): Boolean
    
    /**
     * Enables or disables TTS auto-speak.
     * Requirement 3.4: WHEN TTS is enabled in settings,
     * THE Translexa app SHALL automatically speak all assistant responses
     * 
     * @param enabled true to enable, false to disable
     */
    fun setEnabled(enabled: Boolean)
    
    /**
     * Gets the current TTS state as a StateFlow.
     * 
     * @return StateFlow of current TTSState
     */
    fun getStateFlow(): StateFlow<TTSState>
    
    /**
     * Checks if TTS is currently speaking.
     * 
     * @return true if currently speaking
     */
    fun isSpeaking(): Boolean
    
    /**
     * Releases TTS resources. Call when done using the service.
     */
    fun shutdown()
}


/**
 * Voice options with different accents and genders
 */
data class VoiceOption(
    val id: String,
    val name: String,
    val locale: Locale,
    val gender: String, // "Male" or "Female"
    val country: String
)

/**
 * Available voice options - INDIAN ENGLISH AS DEFAULT
 */
object VoiceOptions {
    val VOICES = listOf(
        // Indian English FIRST (default)
        VoiceOption("en_in_male", "🇮🇳 English (IN) - Male", Locale("en", "IN"), "Male", "India"),
        VoiceOption(
            "en_in_female",
            "🇮🇳 English (IN) - Female",
            Locale("en", "IN"),
            "Female",
            "India"
        ),
        // US English
        VoiceOption("en_us_male", "🇺🇸 English (US) - Male", Locale.US, "Male", "United States"),
        VoiceOption(
            "en_us_female",
            "🇺🇸 English (US) - Female",
            Locale.US,
            "Female",
            "United States"
        ),
        // UK English
        VoiceOption("en_gb_male", "🇬🇧 English (UK) - Male", Locale.UK, "Male", "United Kingdom"),
        VoiceOption(
            "en_gb_female",
            "🇬🇧 English (UK) - Female",
            Locale.UK,
            "Female",
            "United Kingdom"
        ),
        // Australian
        VoiceOption(
            "en_au_male",
            "🇦🇺 English (AU) - Male",
            Locale("en", "AU"),
            "Male",
            "Australia"
        ),
        VoiceOption(
            "en_au_female",
            "🇦🇺 English (AU) - Female",
            Locale("en", "AU"),
            "Female",
            "Australia"
        ),
        // Spanish
        VoiceOption("es_es_male", "🇪🇸 Spanish (ES) - Male", Locale("es", "ES"), "Male", "Spain"),
        VoiceOption(
            "es_es_female",
            "🇪🇸 Spanish (ES) - Female",
            Locale("es", "ES"),
            "Female",
            "Spain"
        ),
        // French
        VoiceOption("fr_fr_male", "🇫🇷 French (FR) - Male", Locale.FRANCE, "Male", "France"),
        VoiceOption("fr_fr_female", "🇫🇷 French (FR) - Female", Locale.FRANCE, "Female", "France"),
        // German
        VoiceOption("de_de_male", "🇩🇪 German (DE) - Male", Locale.GERMANY, "Male", "Germany"),
        VoiceOption("de_de_female", "🇩🇪 German (DE) - Female", Locale.GERMANY, "Female", "Germany"),
        // Italian
        VoiceOption("it_it_male", "🇮🇹 Italian (IT) - Male", Locale.ITALY, "Male", "Italy"),
        VoiceOption("it_it_female", "🇮🇹 Italian (IT) - Female", Locale.ITALY, "Female", "Italy"),
        // Japanese
        VoiceOption("ja_jp_male", "🇯🇵 Japanese (JP) - Male", Locale.JAPAN, "Male", "Japan"),
        VoiceOption("ja_jp_female", "🇯🇵 Japanese (JP) - Female", Locale.JAPAN, "Female", "Japan"),
        // Korean
        VoiceOption("ko_kr_male", "🇰🇷 Korean (KR) - Male", Locale.KOREA, "Male", "South Korea"),
        VoiceOption(
            "ko_kr_female",
            "🇰🇷 Korean (KR) - Female",
            Locale.KOREA,
            "Female",
            "South Korea"
        ),
        // Chinese
        VoiceOption("zh_cn_male", "🇨🇳 Chinese (CN) - Male", Locale.CHINA, "Male", "China"),
        VoiceOption("zh_cn_female", "🇨🇳 Chinese (CN) - Female", Locale.CHINA, "Female", "China")
    )

    fun getVoiceById(id: String): VoiceOption? = VOICES.find { it.id == id }
    fun getDefaultVoice(): VoiceOption = VOICES[0] // 🇮🇳 Indian Male as default!
}

/**
 * Implementation of TTSService using Android TextToSpeech.
 * Provides text-to-speech functionality for assistant responses.
 * Now properly reads voice settings from UserSettings!
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
class TTSServiceImpl(
    private val context: Context
) : TTSService {

    companion object {
        private const val PREFS_NAME = "tts_preferences"
        private const val KEY_VOICE_ID = "selected_voice_id"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_CHARACTER = "voice_character"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var ttsEnabled = true
    private var currentVoice: VoiceOption = VoiceOptions.getDefaultVoice()
    private var voiceSpeed: Float = 1.0f
    private var voicePitch: Float = 1.0f

    private val _stateFlow = MutableStateFlow<TTSState>(TTSState.Idle)

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Read from user settings prefs (same as ChatSessionRepository)
    private val userPrefs by lazy {
        context.getSharedPreferences("translexa_data", Context.MODE_PRIVATE)
    }

    private val gson by lazy { com.google.gson.Gson() }

    init {
        // Load saved voice preference
        loadSavedVoice()
        initializeTTS()
    }

    /**
     * Loads the saved voice preference from SharedPreferences
     * Reads from UserSettings JSON stored by ChatSessionRepository
     */
    private fun loadSavedVoice() {
        try {
            // Read UserSettings JSON (stored by ChatSessionRepository)
            val settingsJson = userPrefs.getString("user_settings", null)
            if (settingsJson != null) {
                val settings = gson.fromJson(
                    settingsJson,
                    com.runanywhere.startup_hackathon20.domain.model.UserSettings::class.java
                )
                if (settings != null) {
                    voiceSpeed = settings.voiceSpeed.coerceIn(0.5f, 2.0f)
                    voicePitch = settings.voicePitch.coerceIn(0.5f, 2.0f)

                    android.util.Log.d(
                        "TTSService",
                        "Loaded: voice=${settings.voiceCharacter}, speed=$voiceSpeed, pitch=$voicePitch"
                    )

                    // Match voice character to VoiceOption
                    val matchedVoice = findVoiceByCharacterName(settings.voiceCharacter)
                    if (matchedVoice != null) {
                        currentVoice = matchedVoice
                        android.util.Log.d("TTSService", "Matched voice: ${currentVoice.name}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSService", "Error loading settings: ${e.message}")
        }

        // Fallback to direct voice ID
        val savedVoiceId = prefs.getString(KEY_VOICE_ID, null)
        if (savedVoiceId != null) {
            val voice = VoiceOptions.getVoiceById(savedVoiceId)
            if (voice != null && currentVoice.id == VoiceOptions.getDefaultVoice().id) {
                currentVoice = voice
            }
        }

        android.util.Log.d(
            "TTSService",
            "Using voice: ${currentVoice.name}, speed=$voiceSpeed, pitch=$voicePitch"
        )
    }

    /**
     * Matches voice character name from settings to VoiceOption
     * ALWAYS returns a valid English voice for reliability
     * Non-English languages default to English with similar style
     */
    private fun findVoiceByCharacterName(charName: String): VoiceOption? {
        val lowerName = charName.lowercase()
        android.util.Log.d("TTSService", "🔍 Matching: '$charName'")

        // Check gender
        val isFemale = lowerName.contains("female")
        val isMale = !isFemale && lowerName.contains("male")

        android.util.Log.d("TTSService", "Gender: female=$isFemale, male=$isMale")

        // ALWAYS return a valid English voice ID
        val voiceId = when {
            // Nova/Default → Indian English (default)
            lowerName.contains("nova") || lowerName.contains("default") -> {
                if (isFemale) "en_in_female" else "en_in_male"
            }
            // Indian English
            lowerName.contains("indian") -> {
                if (isFemale) "en_in_female" else "en_in_male"
            }
            // US English
            lowerName.contains("us english") || lowerName.contains("us ") ||
                    lowerName.contains("american") || lowerName.contains("united states") -> {
                if (isFemale) "en_us_female" else "en_us_male"
            }
            // British English
            lowerName.contains("british") || lowerName.contains("uk ") ||
                    lowerName.contains("united kingdom") || lowerName.contains("england") -> {
                if (isFemale) "en_gb_female" else "en_gb_male"
            }
            // Australian English
            lowerName.contains("australia") || lowerName.contains("aussie") -> {
                if (isFemale) "en_au_female" else "en_au_male"
            }
            // Canadian English → Use US English (very similar accent)
            lowerName.contains("canad") -> {
                if (isFemale) "en_us_female" else "en_us_male"
            }
            // Irish → Use UK English
            lowerName.contains("irish") -> {
                if (isFemale) "en_gb_female" else "en_gb_male"
            }
            // New Zealand → Use Australian
            lowerName.contains("zealand") -> {
                if (isFemale) "en_au_female" else "en_au_male"
            }
            // South African → Use UK English
            lowerName.contains("south african") || lowerName.contains("africa") -> {
                if (isFemale) "en_gb_female" else "en_gb_male"
            }
            // Voice styles - use US English with speed/pitch
            lowerName.contains("calm") || lowerName.contains("slow") -> {
                if (isFemale) "en_us_female" else "en_us_male"
            }

            lowerName.contains("fast") || lowerName.contains("energetic") -> {
                if (isFemale) "en_us_female" else "en_us_male"
            }

            lowerName.contains("deep") || lowerName.contains("narrator") -> {
                "en_gb_male" // British male for narrator/deep
            }

            lowerName.contains("high") || lowerName.contains("pitch") -> {
                "en_us_female" // Female for high pitch
            }

            lowerName.contains("custom") -> {
                if (isFemale) "en_us_female" else "en_us_male"
            }
            // ANY other language (Spanish, French, Catalan, etc.) → English fallback
            // This ensures TTS always works even if language not installed
            isFemale -> "en_us_female"
            isMale -> "en_us_male"
            // Ultimate fallback - Indian English Male
            else -> "en_in_male"
        }

        android.util.Log.d("TTSService", "✓ Voice ID: $voiceId")
        return VoiceOptions.getVoiceById(voiceId)
    }

    /**
     * Sets the voice for TTS and saves the preference
     */
    fun setVoice(voiceId: String) {
        val voice = VoiceOptions.getVoiceById(voiceId) ?: return
        currentVoice = voice

        // Save the preference
        prefs.edit().putString(KEY_VOICE_ID, voiceId).apply()

        if (isInitialized) {
            applyVoiceToTTS(voice)
        }
    }

    /**
     * Sets voice speed and pitch from settings
     */
    fun setVoiceSettings(speed: Float, pitch: Float) {
        voiceSpeed = speed.coerceIn(0.5f, 2.0f)
        voicePitch = pitch.coerceIn(0.5f, 2.0f)

        tts?.setSpeechRate(voiceSpeed)
        tts?.setPitch(voicePitch)

        android.util.Log.d("TTSService", "Applied speed=$voiceSpeed, pitch=$voicePitch")
    }

    /**
     * Reloads settings from SharedPreferences (call after settings change)
     */
    fun reloadSettings() {
        loadSavedVoice()
        if (isInitialized) {
            applyVoiceToTTS(currentVoice)
            tts?.setSpeechRate(voiceSpeed)
            tts?.setPitch(voicePitch)
        }
    }

    /**
     * Applies the voice settings to the TTS engine
     * Improved voice matching for different Android devices
     */
    private fun applyVoiceToTTS(voice: VoiceOption) {
        android.util.Log.d("TTSService", "🎤 Applying voice: ${voice.name}, locale: ${voice.locale}")

        // Set locale first
        val result = tts?.setLanguage(voice.locale)
        android.util.Log.d("TTSService", "setLanguage result: $result")

        val availableVoices = tts?.voices
        if (availableVoices == null || availableVoices.isEmpty()) {
            android.util.Log.w("TTSService", "No voices available!")
            return
        }

        // Log all available voices for debugging
        android.util.Log.d("TTSService", "Available voices (${availableVoices.size}):")
        availableVoices.take(10).forEach { v ->
            android.util.Log.d("TTSService", "  - ${v.name} [${v.locale}]")
        }

        val targetLang = voice.locale.language
        val targetCountry = voice.locale.country
        val wantFemale = voice.gender == "Female"

        // Score-based voice selection
        var bestVoice: android.speech.tts.Voice? = null
        var bestScore = -1

        for (v in availableVoices) {
            var score = 0
            val vName = v.name.lowercase()
            val vLang = v.locale.language
            val vCountry = v.locale.country

            // Language match (required)
            if (vLang != targetLang) continue
            score += 10

            // Country match (preferred)
            if (vCountry == targetCountry) {
                score += 5
            }

            // Gender matching
            val isFemaleVoice = vName.contains("female") || vName.contains("woman") ||
                    vName.contains("-f-") || vName.contains("#female") ||
                    vName.contains("_f_") || vName.endsWith("_f")
            val isMaleVoice = vName.contains("male") || vName.contains("man") ||
                    vName.contains("-m-") || vName.contains("#male") ||
                    vName.contains("_m_") || vName.endsWith("_m")

            if (wantFemale && isFemaleVoice) {
                score += 3
            } else if (!wantFemale && isMaleVoice) {
                score += 3
            } else if (wantFemale && !isMaleVoice) {
                // If we want female but voice isn't explicitly male, still okay
                score += 1
            } else if (!wantFemale && !isFemaleVoice) {
                // If we want male but voice isn't explicitly female, still okay
                score += 1
            }

            // Prefer local/network voices
            if (!v.isNetworkConnectionRequired) {
                score += 2
            }

            // Prefer higher quality voices (heuristic: longer names often mean more specific)
            if (vName.contains("high") || vName.contains("premium") || vName.contains("enhanced")) {
                score += 1
            }

            if (score > bestScore) {
                bestScore = score
                bestVoice = v
            }
        }

        if (bestVoice != null) {
            tts?.voice = bestVoice
            android.util.Log.d(
                "TTSService",
                "✅ Selected voice: ${bestVoice.name} (score: $bestScore)"
            )
        } else {
            android.util.Log.w("TTSService", "❌ No matching voice found for ${voice.name}")
        }
    }

    /**
     * Gets current voice
     */
    fun getCurrentVoice(): VoiceOption = currentVoice

    /**
     * Initializes the Android TextToSpeech engine.
     * Requirement 3.5: IF TTS initialization fails, THEN THE Translexa app
     * SHALL fall back to text-only mode and notify the user
     */
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true

                // Apply saved voice preference
                applyVoiceToTTS(currentVoice)
                
                // Set up utterance progress listener
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _stateFlow.value = TTSState.Speaking
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        _stateFlow.value = TTSState.Completed
                        // Reset to idle after a short delay
                        _stateFlow.value = TTSState.Idle
                    }
                    
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _stateFlow.value = TTSState.Error("Speech synthesis failed")
                    }
                    
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            TextToSpeech.ERROR_SYNTHESIS -> "Speech synthesis error"
                            TextToSpeech.ERROR_SERVICE -> "TTS service error"
                            TextToSpeech.ERROR_OUTPUT -> "Audio output error"
                            TextToSpeech.ERROR_NETWORK -> "Network error"
                            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid request"
                            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "TTS not installed"
                            else -> "Unknown TTS error"
                        }
                        _stateFlow.value = TTSState.Error(errorMessage)
                    }
                })
                
                _stateFlow.value = TTSState.Idle
            } else {
                isInitialized = false
                _stateFlow.value = TTSState.Error("TTS initialization failed")
            }
        }
    }
    
    /**
     * Speaks the given text aloud.
     * Requirement 3.1: WHEN the LLM completes generating a response,
     * THE Translexa app SHALL convert the response text to speech
     * using the on-device TTS engine
     */
    override fun speak(text: String): Flow<TTSState> {
        android.util.Log.d("TTSService", "🔊 speak() called, text length: ${text.length}")

        if (!isInitialized) {
            android.util.Log.e("TTSService", "❌ TTS not initialized!")
            _stateFlow.value = TTSState.Error("TTS not initialized")
            return _stateFlow.asStateFlow()
        }

        if (!ttsEnabled) {
            android.util.Log.w("TTSService", "⚠️ TTS disabled")
            _stateFlow.value = TTSState.Idle
            return _stateFlow.asStateFlow()
        }

        if (text.isBlank()) {
            android.util.Log.w("TTSService", "⚠️ Empty text")
            _stateFlow.value = TTSState.Idle
            return _stateFlow.asStateFlow()
        }

        // Reload settings before speaking to get latest voice preferences
        reloadSettingsFromPrefs()

        // Apply speed and pitch
        tts?.setSpeechRate(voiceSpeed)
        tts?.setPitch(voicePitch)

        android.util.Log.d(
            "TTSService",
            "🎤 Speaking: voice=${currentVoice.name}, speed=$voiceSpeed, pitch=$voicePitch"
        )
        android.util.Log.d("TTSService", "📝 Text: ${text.take(50)}...")

        // Generate unique utterance ID
        val utteranceId = UUID.randomUUID().toString()

        // Speak the text
        val result = tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        android.util.Log.d("TTSService", "🔊 tts.speak() result: $result (0=SUCCESS, -1=ERROR)")

        if (result == TextToSpeech.ERROR) {
            android.util.Log.e("TTSService", "❌ TTS speak returned ERROR!")
            _stateFlow.value = TTSState.Error("Failed to start speech")
        } else {
            android.util.Log.d("TTSService", "✅ TTS speaking started successfully")
        }

        return _stateFlow.asStateFlow()
    }

    /**
     * Reloads settings from SharedPreferences before speaking
     * Uses ACTUAL voice name saved from Settings preview!
     */
    private fun reloadSettingsFromPrefs() {
        try {
            val settingsJson = userPrefs.getString("user_settings", null)
            android.util.Log.d("TTSService", "📖 Reading settings...")

            if (settingsJson != null) {
                val settings = gson.fromJson(
                    settingsJson,
                    com.runanywhere.startup_hackathon20.domain.model.UserSettings::class.java
                )
                if (settings != null) {
                    // Always apply speed and pitch
                    voiceSpeed = settings.voiceSpeed.coerceIn(0.5f, 2.0f)
                    voicePitch = settings.voicePitch.coerceIn(0.5f, 2.0f)

                    android.util.Log.d(
                        "TTSService",
                        "📝 voiceChar='${settings.voiceCharacter}', actual='${settings.actualVoiceName}', speed=$voiceSpeed, pitch=$voicePitch"
                    )

                    // Apply speed and pitch to TTS
                    tts?.setSpeechRate(voiceSpeed)
                    tts?.setPitch(voicePitch)

                    val availableVoices = tts?.voices
                    if (availableVoices == null || availableVoices.isEmpty()) {
                        android.util.Log.w("TTSService", "⚠️ No voices available yet!")
                        return
                    }

                    // PRIORITY 1: Use actual voice name if available (exact match from Settings preview)
                    if (settings.actualVoiceName.isNotBlank()) {
                        val exactVoice =
                            availableVoices.find { it.name == settings.actualVoiceName }
                        if (exactVoice != null) {
                            tts?.voice = exactVoice
                            android.util.Log.d(
                                "TTSService",
                                "✅ Using EXACT voice: ${exactVoice.name}"
                            )
                            return
                        } else {
                            android.util.Log.w(
                                "TTSService",
                                "⚠️ Exact voice not found: ${settings.actualVoiceName}"
                            )

                            // PRIORITY 2: Try partial name matching (voice name may vary slightly)
                            val savedName = settings.actualVoiceName.lowercase()
                            val partialMatch = availableVoices.find { voice ->
                                val vName = voice.name.lowercase()
                                // Match by key parts of the voice name
                                vName.contains(savedName.substringBefore("#")) ||
                                        savedName.contains(vName.substringBefore("#"))
                            }
                            if (partialMatch != null) {
                                tts?.voice = partialMatch
                                android.util.Log.d(
                                    "TTSService",
                                    "✅ Using PARTIAL match voice: ${partialMatch.name}"
                                )
                                return
                            }
                        }
                    }

                    // PRIORITY 3: Match by voice character name (locale + gender)
                    val voiceChar = settings.voiceCharacter.lowercase()
                    android.util.Log.d("TTSService", "🔍 Matching by voiceChar: $voiceChar")

                    // Extract locale and gender from voice character name
                    val wantFemale = voiceChar.contains("female")
                    val wantMale = voiceChar.contains("male") && !wantFemale

                    // Determine target locale from character name
                    val targetLocale = when {
                        voiceChar.contains("indian") || voiceChar.contains("🇮🇳") -> java.util.Locale(
                            "en",
                            "IN"
                        )

                        voiceChar.contains("us ") || voiceChar.contains("us english") || voiceChar.contains(
                            "🇺🇸"
                        ) -> java.util.Locale.US

                        voiceChar.contains("british") || voiceChar.contains("uk ") || voiceChar.contains(
                            "🇬🇧"
                        ) -> java.util.Locale.UK

                        voiceChar.contains("australia") || voiceChar.contains("🇦🇺") -> java.util.Locale(
                            "en",
                            "AU"
                        )

                        voiceChar.contains("canad") || voiceChar.contains("🇨🇦") -> java.util.Locale.CANADA
                        voiceChar.contains("spanish") || voiceChar.contains("🇪🇸") -> java.util.Locale(
                            "es",
                            "ES"
                        )

                        voiceChar.contains("french") || voiceChar.contains("🇫🇷") -> java.util.Locale.FRANCE
                        voiceChar.contains("german") || voiceChar.contains("🇩🇪") -> java.util.Locale.GERMANY
                        voiceChar.contains("italian") || voiceChar.contains("🇮🇹") -> java.util.Locale.ITALY
                        voiceChar.contains("japanese") || voiceChar.contains("🇯🇵") -> java.util.Locale.JAPAN
                        voiceChar.contains("korean") || voiceChar.contains("🇰🇷") -> java.util.Locale.KOREA
                        voiceChar.contains("chinese") || voiceChar.contains("🇨🇳") -> java.util.Locale.CHINA
                        else -> java.util.Locale.US // Default to US
                    }

                    android.util.Log.d(
                        "TTSService",
                        "🎯 Target locale: $targetLocale, wantFemale: $wantFemale, wantMale: $wantMale"
                    )

                    // Find best matching voice from available voices
                    var bestVoice: android.speech.tts.Voice? = null
                    var bestScore = -1

                    for (voice in availableVoices) {
                        var score = 0
                        val vName = voice.name.lowercase()
                        val vLang = voice.locale.language
                        val vCountry = voice.locale.country

                        // Language must match
                        if (vLang != targetLocale.language) continue
                        score += 10

                        // Country match bonus
                        if (vCountry == targetLocale.country) {
                            score += 5
                        }

                        // Gender matching
                        val isFemaleVoice = vName.contains("female") || vName.contains("#female") ||
                                vName.contains("-f-") || vName.contains("_f_")
                        val isMaleVoice = vName.contains("male") && !vName.contains("female") ||
                                vName.contains("#male") || vName.contains("-m-") || vName.contains("_m_")

                        if (wantFemale && isFemaleVoice) {
                            score += 8  // Strong bonus for exact gender match
                        } else if (wantMale && isMaleVoice) {
                            score += 8
                        } else if (wantFemale && !isMaleVoice) {
                            score += 2  // Weak bonus if gender not explicit but not opposite
                        } else if (wantMale && !isFemaleVoice) {
                            score += 2
                        }

                        // Prefer local voices over network
                        if (!voice.isNetworkConnectionRequired) {
                            score += 3
                        }

                        if (score > bestScore) {
                            bestScore = score
                            bestVoice = voice
                        }
                    }

                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                        android.util.Log.d(
                            "TTSService",
                            "✅ Using MATCHED voice: ${bestVoice.name} (score: $bestScore)"
                        )
                        return
                    }

                    // PRIORITY 4: Fall back to locale/gender matching using VoiceOptions
                    val matchedVoice = findVoiceByCharacterName(settings.voiceCharacter)
                        ?: VoiceOptions.getDefaultVoice()
                    currentVoice = matchedVoice
                    android.util.Log.d("TTSService", "🎤 Final fallback to: ${currentVoice.name}")
                    applyVoiceToTTS(currentVoice)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSService", "❌ Error: ${e.message}")
            voiceSpeed = 1.0f
            voicePitch = 1.0f
            currentVoice = VoiceOptions.getDefaultVoice()
            tts?.language = currentVoice.locale
        }
    }
    
    /**
     * Stops any ongoing speech immediately.
     * Requirement 3.3: WHEN the user taps a stop button during TTS playback,
     * THE Translexa app SHALL immediately stop audio output
     */
    override fun stop() {
        tts?.stop()
        _stateFlow.value = TTSState.Idle
    }
    
    override fun isEnabled(): Boolean {
        return ttsEnabled
    }
    
    /**
     * Enables or disables TTS auto-speak.
     * Requirement 3.4: WHEN TTS is enabled in settings,
     * THE Translexa app SHALL automatically speak all assistant responses
     */
    override fun setEnabled(enabled: Boolean) {
        ttsEnabled = enabled
        if (!enabled) {
            stop()
        }
    }
    
    override fun getStateFlow(): StateFlow<TTSState> {
        return _stateFlow.asStateFlow()
    }
    
    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }
    
    /**
     * Releases TTS resources.
     */
    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

/**
 * Mock implementation of TTSService for testing.
 * Does not require Android Context.
 */
class MockTTSService : TTSService {
    
    private var enabled = true
    private var speaking = false
    private val _stateFlow = MutableStateFlow<TTSState>(TTSState.Idle)
    
    override fun speak(text: String): Flow<TTSState> {
        if (!enabled || text.isBlank()) {
            _stateFlow.value = TTSState.Idle
            return _stateFlow.asStateFlow()
        }
        
        speaking = true
        _stateFlow.value = TTSState.Speaking
        
        // Simulate completion (in real tests, you'd control this)
        // For now, immediately complete
        speaking = false
        _stateFlow.value = TTSState.Completed
        _stateFlow.value = TTSState.Idle
        
        return _stateFlow.asStateFlow()
    }
    
    override fun stop() {
        speaking = false
        _stateFlow.value = TTSState.Idle
    }
    
    override fun isEnabled(): Boolean = enabled
    
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            stop()
        }
    }
    
    override fun getStateFlow(): StateFlow<TTSState> = _stateFlow.asStateFlow()
    
    override fun isSpeaking(): Boolean = speaking
    
    override fun shutdown() {
        speaking = false
        _stateFlow.value = TTSState.Idle
    }
    
    // Test helper methods
    fun simulateSpeaking() {
        speaking = true
        _stateFlow.value = TTSState.Speaking
    }
    
    fun simulateCompleted() {
        speaking = false
        _stateFlow.value = TTSState.Completed
        _stateFlow.value = TTSState.Idle
    }
    
    fun simulateError(message: String) {
        speaking = false
        _stateFlow.value = TTSState.Error(message)
    }
}
