package com.runanywhere.startup_hackathon20.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.runanywhere.startup_hackathon20.ChatViewModel
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.ViewModelFactory
import com.runanywhere.startup_hackathon20.domain.model.UserSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment() {

    private val chatViewModel: ChatViewModel by activityViewModels { ViewModelFactory() }
    
    private lateinit var editName: TextInputEditText
    private lateinit var editAge: TextInputEditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var radioMale: MaterialRadioButton
    private lateinit var radioFemale: MaterialRadioButton
    private lateinit var editProfession: TextInputEditText
    private lateinit var editInterests: TextInputEditText
    private lateinit var editCustomInstructions: TextInputEditText
    private lateinit var switchTts: SwitchMaterial
    private lateinit var btnSave: MaterialButton
    private lateinit var voiceCharacterDropdown: AutoCompleteTextView
    private lateinit var btnPreviewVoice: MaterialButton
    private lateinit var voiceSpeedSlider: Slider
    private lateinit var voicePitchSlider: Slider
    
    private var tts: TextToSpeech? = null
    private var selectedVoiceIndex = 0
    private var availableVoices: List<android.speech.tts.Voice> = emptyList()
    private var ttsReady = false
    
    // Voice presets with speed/pitch - will be combined with actual TTS voices
    private val voiceCharacters = mutableListOf<VoiceCharacter>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        initTTS()
        setupVoiceDropdown()
        loadSettings()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        editName = view.findViewById(R.id.editName)
        editAge = view.findViewById(R.id.editAge)
        genderGroup = view.findViewById(R.id.genderGroup)
        radioMale = view.findViewById(R.id.radioMale)
        radioFemale = view.findViewById(R.id.radioFemale)
        editProfession = view.findViewById(R.id.editProfession)
        editInterests = view.findViewById(R.id.editInterests)
        editCustomInstructions = view.findViewById(R.id.editCustomInstructions)
        switchTts = view.findViewById(R.id.switchTts)
        btnSave = view.findViewById(R.id.btnSaveSettings)
        voiceCharacterDropdown = view.findViewById(R.id.voiceCharacterDropdown)
        btnPreviewVoice = view.findViewById(R.id.btnPreviewVoice)
        voiceSpeedSlider = view.findViewById(R.id.voiceSpeedSlider)
        voicePitchSlider = view.findViewById(R.id.voicePitchSlider)
    }
    
    private fun initTTS() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                loadAvailableVoices()
            }
        }
    }
    
    private fun loadAvailableVoices() {
        try {
            // Get actual TTS voices from device
            availableVoices = tts?.voices?.filter { voice ->
                voice.locale.language == "en" && !voice.isNetworkConnectionRequired
            }?.sortedBy { it.name } ?: emptyList()
            
            // Build voice characters from actual voices
            voiceCharacters.clear()
            
            // Add default presets first
            voiceCharacters.add(VoiceCharacter("Default", 1.0f, 1.0f, "Default voice", null))
            voiceCharacters.add(VoiceCharacter("Slow & Deep", 0.8f, 0.7f, "Slower, deeper tone", null))
            voiceCharacters.add(VoiceCharacter("Fast & High", 1.3f, 1.3f, "Faster, higher pitch", null))
            voiceCharacters.add(VoiceCharacter("Calm", 0.85f, 0.95f, "Calm and relaxed", null))
            voiceCharacters.add(VoiceCharacter("Energetic", 1.2f, 1.1f, "Fast and lively", null))
            
            // Add actual device voices
            availableVoices.take(10).forEachIndexed { index, voice ->
                val displayName = getVoiceDisplayName(voice, index)
                voiceCharacters.add(VoiceCharacter(displayName, 1.0f, 1.0f, voice.name, voice))
            }
            
            // Add custom option at end
            voiceCharacters.add(VoiceCharacter("Custom", 1.0f, 1.0f, "Use sliders", null))
            
            // Update dropdown on main thread
            activity?.runOnUiThread {
                setupVoiceDropdown()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to basic presets
            voiceCharacters.clear()
            voiceCharacters.add(VoiceCharacter("Default", 1.0f, 1.0f, "Default voice", null))
            voiceCharacters.add(VoiceCharacter("Slow", 0.8f, 1.0f, "Slower speech", null))
            voiceCharacters.add(VoiceCharacter("Fast", 1.3f, 1.0f, "Faster speech", null))
            voiceCharacters.add(VoiceCharacter("Deep", 1.0f, 0.7f, "Lower pitch", null))
            voiceCharacters.add(VoiceCharacter("High", 1.0f, 1.3f, "Higher pitch", null))
            voiceCharacters.add(VoiceCharacter("Custom", 1.0f, 1.0f, "Use sliders", null))
            
            activity?.runOnUiThread {
                setupVoiceDropdown()
            }
        }
    }
    
    private fun getVoiceDisplayName(voice: android.speech.tts.Voice, index: Int): String {
        val name = voice.name.lowercase()
        return when {
            name.contains("male") && !name.contains("female") -> "Male Voice ${index + 1}"
            name.contains("female") -> "Female Voice ${index + 1}"
            name.contains("en-us") -> "US English ${index + 1}"
            name.contains("en-gb") -> "British ${index + 1}"
            name.contains("en-au") -> "Australian ${index + 1}"
            name.contains("en-in") -> "Indian English ${index + 1}"
            else -> "Voice ${index + 1}"
        }
    }
    
    private fun setupVoiceDropdown() {
        if (voiceCharacters.isEmpty()) return
        
        val voiceNames = voiceCharacters.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, voiceNames)
        voiceCharacterDropdown.setAdapter(adapter)
        voiceCharacterDropdown.setText(voiceCharacters[0].name, false)
        
        voiceCharacterDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position < voiceCharacters.size) {
                selectedVoiceIndex = position
                val voice = voiceCharacters[position]
                
                // Update sliders if not custom
                if (voice.name != "Custom") {
                    voiceSpeedSlider.value = voice.speed.coerceIn(0.5f, 2.0f)
                    voicePitchSlider.value = voice.pitch.coerceIn(0.5f, 2.0f)
                }
                
                Toast.makeText(requireContext(), voice.description, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            chatViewModel.userSettings.collectLatest { settings ->
                editName.setText(settings.name)
                if (settings.age > 0) editAge.setText(settings.age.toString())
                
                when (settings.gender.lowercase()) {
                    "female", "f" -> radioFemale.isChecked = true
                    else -> radioMale.isChecked = true
                }
                
                editProfession.setText(settings.profession)
                editInterests.setText(settings.interests)
                editCustomInstructions.setText(settings.customInstructions)
                switchTts.isChecked = settings.ttsEnabled
                
                // Load voice settings with safe bounds
                voiceSpeedSlider.value = settings.voiceSpeed.coerceIn(0.5f, 2.0f)
                voicePitchSlider.value = settings.voicePitch.coerceIn(0.5f, 2.0f)
                
                // Find matching voice character (only if list is populated)
                if (voiceCharacters.isNotEmpty()) {
                    val matchingVoice = voiceCharacters.indexOfFirst { 
                        it.name == settings.voiceCharacter 
                    }.takeIf { it >= 0 } ?: 0
                    selectedVoiceIndex = matchingVoice
                    voiceCharacterDropdown.setText(voiceCharacters[matchingVoice].name, false)
                }
            }
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        switchTts.setOnCheckedChangeListener { _, isChecked ->
            chatViewModel.setTtsEnabled(isChecked)
        }
        
        btnPreviewVoice.setOnClickListener {
            previewVoice()
        }
        
        // Update to custom when sliders are manually changed
        voiceSpeedSlider.addOnChangeListener { _, _, fromUser ->
            if (fromUser && voiceCharacters.isNotEmpty() && selectedVoiceIndex < voiceCharacters.size) {
                if (voiceCharacters[selectedVoiceIndex].name != "Custom") {
                    val voice = voiceCharacters[selectedVoiceIndex]
                    if (voiceSpeedSlider.value != voice.speed || voicePitchSlider.value != voice.pitch) {
                        val customIndex = voiceCharacters.indexOfFirst { it.name == "Custom" }
                        if (customIndex >= 0) {
                            selectedVoiceIndex = customIndex
                            voiceCharacterDropdown.setText("Custom", false)
                        }
                    }
                }
            }
        }
        
        voicePitchSlider.addOnChangeListener { _, _, fromUser ->
            if (fromUser && voiceCharacters.isNotEmpty() && selectedVoiceIndex < voiceCharacters.size) {
                if (voiceCharacters[selectedVoiceIndex].name != "Custom") {
                    val voice = voiceCharacters[selectedVoiceIndex]
                    if (voiceSpeedSlider.value != voice.speed || voicePitchSlider.value != voice.pitch) {
                        val customIndex = voiceCharacters.indexOfFirst { it.name == "Custom" }
                        if (customIndex >= 0) {
                            selectedVoiceIndex = customIndex
                            voiceCharacterDropdown.setText("Custom", false)
                        }
                    }
                }
            }
        }
    }
    
    private fun previewVoice() {
        if (!ttsReady || tts == null) {
            Toast.makeText(requireContext(), "Voice not ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val speed = voiceSpeedSlider.value.coerceIn(0.5f, 2.0f)
            val pitch = voicePitchSlider.value.coerceIn(0.5f, 2.0f)
            
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
            
            // Set actual voice if available
            if (selectedVoiceIndex < voiceCharacters.size) {
                val selectedChar = voiceCharacters[selectedVoiceIndex]
                selectedChar.actualVoice?.let { voice ->
                    tts?.voice = voice
                }
            }
            
            val voiceName = if (selectedVoiceIndex < voiceCharacters.size) {
                voiceCharacters[selectedVoiceIndex].name.split(" ")[0]
            } else "Nova"
            
            val previewText = "Hi! I'm $voiceName. This is how I sound."
            tts?.speak(previewText, TextToSpeech.QUEUE_FLUSH, null, "preview")
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error playing voice", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun saveSettings() {
        val gender = if (radioFemale.isChecked) "female" else "male"
        
        val voiceCharName = if (selectedVoiceIndex < voiceCharacters.size) {
            voiceCharacters[selectedVoiceIndex].name
        } else "Default"
        
        val settings = UserSettings(
            name = editName.text.toString().trim(),
            age = editAge.text.toString().toIntOrNull() ?: 0,
            gender = gender,
            profession = editProfession.text.toString().trim(),
            interests = editInterests.text.toString().trim(),
            customInstructions = editCustomInstructions.text.toString().trim(),
            ttsEnabled = switchTts.isChecked,
            voiceCharacter = voiceCharName,
            voiceSpeed = voiceSpeedSlider.value.coerceIn(0.5f, 2.0f),
            voicePitch = voicePitchSlider.value.coerceIn(0.5f, 2.0f)
        )
        
        chatViewModel.saveUserSettings(settings)
        Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
    
    data class VoiceCharacter(
        val name: String,
        val speed: Float,
        val pitch: Float,
        val description: String,
        val actualVoice: android.speech.tts.Voice?
    )
}
