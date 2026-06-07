package com.freedrama.adskipper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MEDIA_PROJECTION = 100
        private const val REQ_OVERLAY          = 101
        private const val REQ_NOTIFICATION     = 102
    }

    private lateinit var mpManager: MediaProjectionManager

    // Cached projection result
    private var projectionResultCode = -1
    private var projectionResultData: Intent? = null

    // Step UI refs
    private lateinit var stepOverlay       : View
    private lateinit var stepScreenCapture : View
    private lateinit var stepAccessibility : View
    private lateinit var stepReady         : View

    private lateinit var btnOverlay        : Button
    private lateinit var btnScreenCapture  : Button
    private lateinit var btnAccessibility  : Button
    private lateinit var btnLaunch         : Button

    private lateinit var checkOverlay      : ImageView
    private lateinit var checkCapture      : ImageView
    private lateinit var checkAccess       : ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        bindViews()
        setupClickListeners()
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionChecks()
    }

    private fun bindViews() {
        stepOverlay        = findViewById(R.id.stepOverlay)
        stepScreenCapture  = findViewById(R.id.stepScreenCapture)
        stepAccessibility  = findViewById(R.id.stepAccessibility)
        stepReady          = findViewById(R.id.stepReady)
        btnOverlay         = findViewById(R.id.btnGrantOverlay)
        btnScreenCapture   = findViewById(R.id.btnGrantCapture)
        btnAccessibility   = findViewById(R.id.btnGrantAccessibility)
        btnLaunch          = findViewById(R.id.btnLaunchService)
        checkOverlay       = findViewById(R.id.checkOverlay)
        checkCapture       = findViewById(R.id.checkCapture)
        checkAccess        = findViewById(R.id.checkAccess)
    }

    private fun setupClickListeners() {
        btnOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQ_OVERLAY)
        }

        btnScreenCapture.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivityForResult(
                mpManager.createScreenCaptureIntent(),
                REQ_MEDIA_PROJECTION
            )
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this,
                "Enable 'FreeDrama Ad Skipper' in Accessibility Services",
                Toast.LENGTH_LONG).show()
        }

        btnLaunch.setOnClickListener {
            if (allPermissionsGranted()) {
                launchService()
            } else {
                Toast.makeText(this, "Complete all steps first!", Toast.LENGTH_SHORT).show()
                refreshPermissionChecks()
            }
        }
    }

    private fun refreshPermissionChecks() {
        val overlayOk = Settings.canDrawOverlays(this)
        val captureOk = projectionResultCode != -1 && projectionResultData != null
        val accessOk  = AdSkipperAccessibilityService.isEnabled()
        val notifOk   = NotificationManagerCompat.from(this).areNotificationsEnabled()

        checkOverlay.visibility = if (overlayOk) View.VISIBLE else View.GONE
        checkCapture.visibility = if (captureOk) View.VISIBLE else View.GONE
        checkAccess.visibility  = if (accessOk)  View.VISIBLE else View.GONE

        btnOverlay.text = if (overlayOk) "✓ Granted" else "Grant Overlay"
        btnOverlay.isEnabled = !overlayOk

        btnScreenCapture.text = if (captureOk) "✓ Granted" else "Grant Screen Capture"
        btnScreenCapture.isEnabled = !captureOk

        btnAccessibility.text = if (accessOk) "✓ Enabled" else "Enable Accessibility"
        btnAccessibility.isEnabled = !accessOk

        val allDone = overlayOk && captureOk && accessOk
        stepReady.visibility = if (allDone) View.VISIBLE else View.GONE
        btnLaunch.isEnabled  = allDone

        if (allDone) {
            btnLaunch.setBackgroundResource(R.drawable.btn_gradient_active)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return Settings.canDrawOverlays(this) &&
                projectionResultCode != -1 &&
                projectionResultData != null &&
                AdSkipperAccessibilityService.isEnabled()
    }

    private fun launchService() {
        val serviceIntent = Intent(this, AdSkipperService::class.java).apply {
            action = SkipConfig.ACTION_START
            putExtra(AdSkipperService.EXTRA_RESULT_CODE, projectionResultCode)
            putExtra(AdSkipperService.EXTRA_RESULT_DATA, projectionResultData)
        }
        startForegroundService(serviceIntent)

        Toast.makeText(this, "🚀 Ad Skipper started! Check the floating panel.", Toast.LENGTH_LONG).show()
        finish() // Go to background — user controls from floating panel now
    }

    private fun animateEntrance() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 800
        findViewById<View>(R.id.rootLayout).startAnimation(fadeIn)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_MEDIA_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    projectionResultCode = resultCode
                    projectionResultData = data
                    Toast.makeText(this, "✓ Screen capture permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Screen capture denied. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        refreshPermissionChecks()
    }
}
