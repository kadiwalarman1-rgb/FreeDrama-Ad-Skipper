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
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mpManager: MediaProjectionManager

    // ── Modern Activity Result API (fixes Android 14+ screen capture bug) ───
    private var projectionResultCode = -1
    private var projectionResultData: Intent? = null

    // Screen capture launcher — properly handles BOTH "Single App" & "Entire Screen"
    private val screenCaptureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResultCode = result.resultCode
                projectionResultData  = result.data
                Toast.makeText(this, "✓ Screen capture permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                projectionResultCode = -1
                projectionResultData  = null
                Toast.makeText(this,
                    "Screen capture denied — please try again and choose 'Entire Screen'",
                    Toast.LENGTH_LONG).show()
            }
            refreshPermissionChecks()
        }

    // Overlay settings launcher
    private val overlayLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionChecks()
        }

    // Accessibility settings launcher
    private val accessibilityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionChecks()
        }

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var btnOverlay        : Button
    private lateinit var btnScreenCapture  : Button
    private lateinit var btnAccessibility  : Button
    private lateinit var btnLaunch         : Button

    private lateinit var checkOverlay      : ImageView
    private lateinit var checkCapture      : ImageView
    private lateinit var checkAccess       : ImageView

    private lateinit var stepReady         : View
    private lateinit var tvCaptureStatus   : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        bindViews()
        setupClickListeners()
        refreshPermissionChecks()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionChecks()
    }

    private fun bindViews() {
        btnOverlay        = findViewById(R.id.btnGrantOverlay)
        btnScreenCapture  = findViewById(R.id.btnGrantCapture)
        btnAccessibility  = findViewById(R.id.btnGrantAccessibility)
        btnLaunch         = findViewById(R.id.btnLaunchService)
        checkOverlay      = findViewById(R.id.checkOverlay)
        checkCapture      = findViewById(R.id.checkCapture)
        checkAccess       = findViewById(R.id.checkAccess)
        stepReady         = findViewById(R.id.stepReady)
        tvCaptureStatus   = findViewById(R.id.tvCaptureStatus)
    }

    private fun setupClickListeners() {

        // Step 1: Overlay permission
        btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        }

        // Step 2: Screen capture — use ActivityResultLauncher (fixes Android 14+ bug!)
        btnScreenCapture.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                    "Step 1 pehle complete karo (Overlay permission)",
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Show hint before launching
            Toast.makeText(this,
                "💡 'Entire Screen' choose karo for best results",
                Toast.LENGTH_LONG).show()
            // Launch screen capture intent via modern launcher
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        // Step 3: Accessibility
        btnAccessibility.setOnClickListener {
            Toast.makeText(this,
                "FreeDrama Ad Skipper dhundho aur ON karo",
                Toast.LENGTH_LONG).show()
            accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Launch button
        btnLaunch.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) ->
                    Toast.makeText(this, "Step 1: Overlay permission do!", Toast.LENGTH_SHORT).show()
                projectionResultCode == -1 ->
                    Toast.makeText(this, "Step 2: Screen Capture allow karo!", Toast.LENGTH_SHORT).show()
                !AdSkipperAccessibilityService.isEnabled() ->
                    Toast.makeText(this, "Step 3: Accessibility Service ON karo!", Toast.LENGTH_SHORT).show()
                else -> launchService()
            }
        }
    }

    private fun refreshPermissionChecks() {
        val overlayOk  = Settings.canDrawOverlays(this)
        val captureOk  = projectionResultCode != -1 && projectionResultData != null
        val accessOk   = AdSkipperAccessibilityService.isEnabled()

        // ── Step 1: Overlay ──────────────────────────────────────────────────
        checkOverlay.visibility = if (overlayOk) View.VISIBLE else View.GONE
        if (overlayOk) {
            btnOverlay.text = "✓ Overlay Granted"
            btnOverlay.isEnabled = false
            btnOverlay.alpha = 0.7f
        } else {
            btnOverlay.text = "Grant Overlay"
            btnOverlay.isEnabled = true
            btnOverlay.alpha = 1.0f
        }

        // ── Step 2: Screen Capture ───────────────────────────────────────────
        checkCapture.visibility = if (captureOk) View.VISIBLE else View.GONE
        if (captureOk) {
            btnScreenCapture.text = "✓ Screen Capture Granted"
            btnScreenCapture.isEnabled = false
            btnScreenCapture.alpha = 0.7f
            tvCaptureStatus.text = "✅ Permission saved — ready!"
            tvCaptureStatus.setTextColor(0xFF22C55E.toInt())
        } else {
            btnScreenCapture.text = "Grant Screen Capture"
            btnScreenCapture.isEnabled = overlayOk // only enable after step 1
            btnScreenCapture.alpha = if (overlayOk) 1.0f else 0.4f
            tvCaptureStatus.text = "Tap button → system dialog aayega → 'Start Now' dabao"
            tvCaptureStatus.setTextColor(0xFF94A3B8.toInt())
        }

        // ── Step 3: Accessibility ────────────────────────────────────────────
        checkAccess.visibility = if (accessOk) View.VISIBLE else View.GONE
        if (accessOk) {
            btnAccessibility.text = "✓ Accessibility Enabled"
            btnAccessibility.isEnabled = false
            btnAccessibility.alpha = 0.7f
        } else {
            btnAccessibility.text = "Enable Accessibility"
            btnAccessibility.isEnabled = true
            btnAccessibility.alpha = 1.0f
        }

        // ── Launch button — unlock only when ALL 3 done ──────────────────────
        val allDone = overlayOk && captureOk && accessOk
        btnLaunch.isEnabled = allDone
        btnLaunch.alpha = if (allDone) 1.0f else 0.4f
        stepReady.visibility = if (allDone) View.VISIBLE else View.GONE

        if (allDone) {
            btnLaunch.text = "🚀  Launch Ad Skipper"
        }
    }

    private fun launchService() {
        val serviceIntent = Intent(this, AdSkipperService::class.java).apply {
            action = SkipConfig.ACTION_START
            putExtra(AdSkipperService.EXTRA_RESULT_CODE, projectionResultCode)
            putExtra(AdSkipperService.EXTRA_RESULT_DATA, projectionResultData)
        }
        startForegroundService(serviceIntent)
        Toast.makeText(this,
            "🚀 Ad Skipper start ho gaya! Floating panel dekhna.",
            Toast.LENGTH_LONG).show()
        finish()
    }
}
