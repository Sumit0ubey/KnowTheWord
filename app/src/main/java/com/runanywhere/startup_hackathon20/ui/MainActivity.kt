package com.runanywhere.startup_hackathon20.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.runanywhere.startup_hackathon20.ChatViewModel
import com.runanywhere.startup_hackathon20.R
import com.runanywhere.startup_hackathon20.ViewModelFactory
import com.runanywhere.startup_hackathon20.VoiceAssistantActivity

/**
 * Main activity with bottom navigation.
 * Tabs: Home, Chat, History, Voice, Settings
 */
class MainActivity : AppCompatActivity() {

    private val chatViewModel: ChatViewModel by viewModels { ViewModelFactory() }
    
    private lateinit var bottomNav: BottomNavigationView
    
    private val homeFragment = HomeFragment()
    private val chatFragment = ChatFragment()
    private val historyFragment = HistoryFragment()
    private val settingsFragment = SettingsFragment()
    
    private var currentFragment: Fragment = homeFragment
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openVoiceAssistant()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_new)
        
        bottomNav = findViewById(R.id.bottomNavigation)
        
        // Add all fragments but hide all except home
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
            .add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
            .add(R.id.fragmentContainer, chatFragment, "chat").hide(chatFragment)
            .add(R.id.fragmentContainer, homeFragment, "home")
            .commit()
        
        setupBottomNavigation()
        
        // Start new chat on app open
        chatViewModel.startNewChat()
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    true
                }
                R.id.nav_chat -> {
                    switchFragment(chatFragment)
                    true
                }
                R.id.nav_history -> {
                    switchFragment(historyFragment)
                    true
                }
                R.id.nav_voice -> {
                    handleVoiceClick()
                    false // Don't select this tab
                }
                R.id.nav_settings -> {
                    switchFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        if (fragment == currentFragment) return
        
        supportFragmentManager.beginTransaction()
            .hide(currentFragment)
            .show(fragment)
            .commit()
        
        currentFragment = fragment
    }

    private fun handleVoiceClick() {
        if (hasMicrophonePermission()) {
            openVoiceAssistant()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openVoiceAssistant() {
        val intent = Intent(this, VoiceAssistantActivity::class.java)
        startActivity(intent)
    }

    fun navigateToChat() {
        bottomNav.selectedItemId = R.id.nav_chat
    }

    fun navigateToHistory() {
        bottomNav.selectedItemId = R.id.nav_history
    }
}
