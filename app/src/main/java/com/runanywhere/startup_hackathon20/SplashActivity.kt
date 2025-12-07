package com.runanywhere.startup_hackathon20

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

/**
 * Modern splash screen with smooth animations.
 * Duration: ~1.8 seconds for quick but impressive intro.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var logoImage: ImageView
    private lateinit var logoGlow: View
    private lateinit var gradientOverlay: View
    private lateinit var appNameText: TextView
    private lateinit var taglineText: TextView
    private lateinit var loadingDots: LinearLayout
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize views
        logoImage = findViewById(R.id.logoImage)
        logoGlow = findViewById(R.id.logoGlow)
        gradientOverlay = findViewById(R.id.gradientOverlay)
        appNameText = findViewById(R.id.appNameText)
        taglineText = findViewById(R.id.taglineText)
        loadingDots = findViewById(R.id.loadingDots)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        // Start animation sequence
        startAnimationSequence()

        // Navigate to main activity after 1.8 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMain()
        }, 1800)
    }

    private fun startAnimationSequence() {
        // Phase 1: Logo entrance (0-400ms)
        animateLogoEntrance()

        // Phase 2: Glow and gradient (200-600ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateGlowEffect()
        }, 200)

        // Phase 3: Text appearance (400-800ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateTextEntrance()
        }, 400)

        // Phase 4: Loading dots (600-1800ms)
        Handler(Looper.getMainLooper()).postDelayed({
            animateLoadingDots()
        }, 600)
    }

    /**
     * Logo scales up with bounce effect
     */
    private fun animateLogoEntrance() {
        logoImage.alpha = 0f
        logoImage.scaleX = 0.3f
        logoImage.scaleY = 0.3f
        logoImage.rotation = -30f

        val scaleX = ObjectAnimator.ofFloat(logoImage, "scaleX", 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(logoImage, "scaleY", 0.3f, 1f)
        val alpha = ObjectAnimator.ofFloat(logoImage, "alpha", 0f, 1f)
        val rotation = ObjectAnimator.ofFloat(logoImage, "rotation", -30f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha, rotation)
            duration = 500
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
    }

    /**
     * Glow pulses behind logo
     */
    private fun animateGlowEffect() {
        // Fade in glow
        logoGlow.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

        // Fade in gradient overlay
        gradientOverlay.animate()
            .alpha(0.8f)
            .setDuration(600)
            .start()

        // Pulse animation for glow
        val scaleUp = ObjectAnimator.ofFloat(logoGlow, "scaleX", 1f, 1.3f)
        val scaleUpY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 1f, 1.3f)
        val scaleDown = ObjectAnimator.ofFloat(logoGlow, "scaleX", 1.3f, 1f)
        val scaleDownY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 1.3f, 1f)

        AnimatorSet().apply {
            play(scaleUp).with(scaleUpY)
            play(scaleDown).with(scaleDownY).after(scaleUp)
            duration = 600
            start()
        }
    }

    /**
     * Text slides up and fades in
     */
    private fun animateTextEntrance() {
        // App name
        appNameText.translationY = 30f
        appNameText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Tagline (slightly delayed)
        Handler(Looper.getMainLooper()).postDelayed({
            taglineText.translationY = 20f
            taglineText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, 150)
    }

    /**
     * Loading dots bounce animation
     */
    private fun animateLoadingDots() {
        loadingDots.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        val dots = listOf(dot1, dot2, dot3)
        val handler = Handler(Looper.getMainLooper())

        // Bouncing dots animation
        fun animateDot(dot: View, delay: Long) {
            handler.postDelayed({
                dot.animate()
                    .scaleX(1.5f)
                    .scaleY(1.5f)
                    .alpha(1f)
                    .setDuration(150)
                    .withEndAction {
                        dot.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(0.5f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }, delay)
        }

        // Loop animation
        var iteration = 0
        val runnable = object : Runnable {
            override fun run() {
                if (iteration < 3) {
                    animateDot(dot1, 0)
                    animateDot(dot2, 100)
                    animateDot(dot3, 200)
                    iteration++
                    handler.postDelayed(this, 400)
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * Navigate to main with smooth transition
     */
    private fun navigateToMain() {
        // Fade out animation
        val rootView = findViewById<View>(R.id.splashRoot)
        rootView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                val intent =
                    Intent(this, com.runanywhere.startup_hackathon20.ui.MainActivity::class.java)
                startActivity(intent)
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            .start()
    }
}
