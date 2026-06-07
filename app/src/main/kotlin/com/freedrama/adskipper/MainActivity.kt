package com.freedrama.adskipper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mpManager: MediaProjectionManager

    companion object {
        // ── STATIC — survives Activity recreation (config changes, window focus) ──
        @Volatile var savedResultCode: Int    = -1
        @Volatile var savedResultData: Intent? = null
    }

    private val screenCaptureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Save STATICALLY — so Activity recreation doesn't lose this
                savedResultCode = result.resultCode
                savedResultData = result.data          // No clone — direct reference
                tvCaptureStatus.text = "✅ Screen capture ready! Ab Launch dabao."
                tvCaptureStatus.setTextColor(0xFF22C55E.toInt())
                Toast.makeText(this, "✓ Screen capture granted!", Toast.LENGTH_SHORT).show()
            } else {
                savedResultCode = -1
                savedResultData = null
                tvCaptureStatus.text = "❌ Dobara try karo — 'Entire Screen' choose karo"
                tvCaptureStatus.setTextColor(0xFFEF4444.toInt())
            }
            refreshAll()
        }

    private val overlayLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAll() }

    private val accessLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            window.decorView.postDelayed({ refreshAll() }, 500)
        }

    private lateinit var btnOverlay      : Button
    private lateinit var btnCapture      : Button
    private lateinit var btnAccess       : Button
    private lateinit var btnLaunch       : Button
    private lateinit var checkOverlay    : ImageView
    private lateinit var checkCapture    : ImageView
    private lateinit var checkAccess     : ImageView
    private lateinit var stepReady       : View
    private lateinit var tvCaptureStatus : TextView
    private lateinit var tvDebugStatus   : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        bindViews()
        setupClicks()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({ refreshAll() }, 300)
    }

    private fun bindViews() {
        btnOverlay      = findViewById(R.id.btnGrantOverlay)
        btnCapture      = findViewById(R.id.btnGrantCapture)
        btnAccess       = findViewById(R.id.btnGrantAccessibility)
        btnLaunch       = findViewById(R.id.btnLaunchService)
        checkOverlay    = findViewById(R.id.checkOverlay)
        checkCapture    = findViewById(R.id.checkCapture)
        checkAccess     = findViewById(R.id.checkAccess)
        stepReady       = findViewById(R.id.stepReady)
        tvCaptureStatus = findViewById(R.id.tvCaptureStatus)
        tvDebugStatus   = findViewById(R.id.tvDebugStatus)
    }

    private fun setupClicks() {

        btnOverlay.setOnClickListener {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
            )
        }

        btnCapture.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Pehle Step 1 (Overlay) complete karo!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Clear old stale data before requesting fresh permission
            savedResultCode = -1
            savedResultData = null
            tvCaptureStatus.text = "⏳ 'Entire Screen' choose karo → 'Start Now' dabao..."
            tvCaptureStatus.setTextColor(0xFFEAB308.toInt())
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        btnAccess.setOnClickListener {
            Toast.makeText(this,
                "List mein 'FreeDrama Ad Skipper' dhundho → toggle ON karo",
                Toast.LENGTH_LONG).show()
            accessLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Tap debug bar to force refresh
        tvDebugStatus.setOnClickListener {
            refreshAll()
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
        }

        btnLaunch.setOnClickListener {
            if (!allGranted()) {
                showMissingHint()
                return@setOnClickListener
            }
            launchService()
        }
    }

    private fun captureGranted() = savedResultCode != -1 && savedResultData != null

    private fun refreshAll() {
        val overlayOk = Settings.canDrawOverlays(this)
        val captureOk = captureGranted()
        val accessOk  = AdSkipperAccessibilityService.isEnabled(this)

        // ── Debug bar — tap to refresh ────────────────────────────────────────
        tvDebugStatus.text = "🔍 Overlay:${if(overlayOk)"✅" else "❌"}  " +
                             "Screen:${if(captureOk)"✅" else "❌"}  " +
                             "Access:${if(accessOk)"✅" else "❌"}  [tap=refresh]"
        tvDebugStatus.setTextColor(
            if (overlayOk && captureOk && accessOk) 0xFF22C55E.toInt() else 0xFFEAB308.toInt()
        )

        // Step 1 — Overlay
        checkOverlay.visibility = if (overlayOk) View.VISIBLE else View.GONE
        btnOverlay.text      = if (overlayOk) "✓ Overlay Granted" else "Grant Overlay"
        btnOverlay.isEnabled = !overlayOk
        btnOverlay.alpha     = if (overlayOk) 0.6f else 1.0f

        // Step 2 — Screen Capture
        checkCapture.visibility = if (captureOk) View.VISIBLE else View.GONE
        btnCapture.text      = if (captureOk) "✓ Screen Capture Granted" else "Grant Screen Capture"
        btnCapture.isEnabled = !captureOk && overlayOk
        btnCapture.alpha     = when { captureOk -> 0.6f; overlayOk -> 1.0f; else -> 0.3f }
        if (captureOk) {
            tvCaptureStatus.text = "✅ Screen capture ready! Ab Launch dabao."
            tvCaptureStatus.setTextColor(0xFF22C55E.toInt())
        }

        // Step 3 — Accessibility
        checkAccess.visibility = if (accessOk) View.VISIBLE else View.GONE
        btnAccess.text       = if (accessOk) "✓ Accessibility Enabled" else "Enable Accessibility Service"
        btnAccess.isEnabled  = !accessOk
        btnAccess.alpha      = if (accessOk) 0.6f else 1.0f

        // Launch
        val allDone = overlayOk && captureOk && accessOk
        btnLaunch.isEnabled  = allDone
        btnLaunch.alpha      = if (allDone) 1.0f else 0.35f
        stepReady.visibility = if (allDone) View.VISIBLE else View.GONE
    }

    private fun allGranted() =
        Settings.canDrawOverlays(this) && captureGranted() &&
        AdSkipperAccessibilityService.isEnabled(this)

    private fun showMissingHint() {
        val msg = when {
            !Settings.canDrawOverlays(this)              -> "❌ Step 1: Overlay permission do!"
            !captureGranted()                             -> "❌ Step 2: Screen Capture allow karo!"
            !AdSkipperAccessibilityService.isEnabled(this) ->
                "❌ Step 3: Accessibility ON karo!\nSettings → Accessibility → FreeDrama Ad Skipper → ON"
            else -> "Ready hai!"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun launchService() {
        startForegroundService(
            Intent(this, AdSkipperService::class.java).apply {
                action = SkipConfig.ACTION_START
                putExtra(AdSkipperService.EXTRA_RESULT_CODE, savedResultCode)
                putExtra(AdSkipperService.EXTRA_RESULT_DATA, savedResultData)
            }
        )
        Toast.makeText(this, "🚀 Ad Skipper chal gaya! Floating panel dekho.", Toast.LENGTH_LONG).show()
        finish()
    }
}
