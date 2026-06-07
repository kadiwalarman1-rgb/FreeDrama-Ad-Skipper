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

    // Saved projection data
    private var projectionResultCode = -1
    private var projectionResultData: Intent? = null

    // ── Modern ActivityResultLauncher (fixes Android 14+ screen capture) ────
    private val screenCaptureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResultCode = result.resultCode
                projectionResultData  = result.data!!.clone() as Intent
                tvCaptureStatus.text  = "✅ Screen capture ready!"
                tvCaptureStatus.setTextColor(0xFF22C55E.toInt())
                Toast.makeText(this, "✓ Screen capture granted!", Toast.LENGTH_SHORT).show()
            } else {
                projectionResultCode = -1
                projectionResultData  = null
                tvCaptureStatus.text  = "❌ Denied — dobara try karo, 'Entire Screen' choose karo"
                tvCaptureStatus.setTextColor(0xFFEF4444.toInt())
            }
            refreshAll()
        }

    private val overlayLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAll() }

    private val accessLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshAll() }

    // ── Views ─────────────────────────────────────────────────────────────
    private lateinit var btnOverlay       : Button
    private lateinit var btnCapture       : Button
    private lateinit var btnAccess        : Button
    private lateinit var btnLaunch        : Button
    private lateinit var checkOverlay     : ImageView
    private lateinit var checkCapture     : ImageView
    private lateinit var checkAccess      : ImageView
    private lateinit var stepReady        : View
    private lateinit var tvCaptureStatus  : TextView

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
        refreshAll()
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
            tvCaptureStatus.text = "⏳ Permission dialog khul raha hai..."
            tvCaptureStatus.setTextColor(0xFFEAB308.toInt())
            // Use the launcher — this is the ONLY correct way on Android 14+
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        btnAccess.setOnClickListener {
            Toast.makeText(this,
                "List mein 'FreeDrama Ad Skipper' dhundho → Toggle ON karo",
                Toast.LENGTH_LONG).show()
            accessLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnLaunch.setOnClickListener {
            if (!allGranted()) {
                highlightMissing()
                return@setOnClickListener
            }
            launchService()
        }
    }

    private fun refreshAll() {
        val overlayOk = Settings.canDrawOverlays(this)
        val captureOk = projectionResultCode != -1 && projectionResultData != null
        val accessOk  = AdSkipperAccessibilityService.isEnabled()

        // Overlay
        checkOverlay.visibility = if (overlayOk) View.VISIBLE else View.GONE
        btnOverlay.text    = if (overlayOk) "✓ Overlay Granted" else "Grant Overlay"
        btnOverlay.isEnabled = !overlayOk
        btnOverlay.alpha     = if (overlayOk) 0.6f else 1.0f

        // Screen Capture
        checkCapture.visibility = if (captureOk) View.VISIBLE else View.GONE
        btnCapture.text    = if (captureOk) "✓ Screen Capture Granted" else "Grant Screen Capture"
        btnCapture.isEnabled = !captureOk && overlayOk
        btnCapture.alpha     = when {
            captureOk  -> 0.6f
            overlayOk  -> 1.0f
            else       -> 0.3f
        }
        if (captureOk && tvCaptureStatus.text.contains("⏳")) {
            tvCaptureStatus.text = "✅ Screen capture ready!"
            tvCaptureStatus.setTextColor(0xFF22C55E.toInt())
        }

        // Accessibility
        checkAccess.visibility = if (accessOk) View.VISIBLE else View.GONE
        btnAccess.text    = if (accessOk) "✓ Accessibility Enabled" else "Enable Accessibility"
        btnAccess.isEnabled = !accessOk
        btnAccess.alpha     = if (accessOk) 0.6f else 1.0f

        // Launch button
        val allDone = overlayOk && captureOk && accessOk
        btnLaunch.isEnabled = allDone
        btnLaunch.alpha     = if (allDone) 1.0f else 0.35f
        stepReady.visibility = if (allDone) View.VISIBLE else View.GONE
    }

    private fun allGranted() =
        Settings.canDrawOverlays(this) &&
        projectionResultCode != -1 &&
        projectionResultData != null &&
        AdSkipperAccessibilityService.isEnabled()

    private fun highlightMissing() {
        if (!Settings.canDrawOverlays(this))
            Toast.makeText(this, "Step 1: Overlay permission do!", Toast.LENGTH_SHORT).show()
        else if (projectionResultCode == -1)
            Toast.makeText(this, "Step 2: Screen Capture allow karo!", Toast.LENGTH_SHORT).show()
        else if (!AdSkipperAccessibilityService.isEnabled())
            Toast.makeText(this, "Step 3: Accessibility Service ON karo!", Toast.LENGTH_SHORT).show()
    }

    private fun launchService() {
        val intent = Intent(this, AdSkipperService::class.java).apply {
            action = SkipConfig.ACTION_START
            putExtra(AdSkipperService.EXTRA_RESULT_CODE, projectionResultCode)
            putExtra(AdSkipperService.EXTRA_RESULT_DATA, projectionResultData)
        }
        startForegroundService(intent)
        Toast.makeText(this,
            "🚀 Ad Skipper chal gaya! Floating panel dekho.", Toast.LENGTH_LONG).show()
        finish()
    }
}
