package com.freedrama.adskipper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AdSkipperService"

class AdSkipperService : Service() {

    enum class State { IDLE, RUNNING, PAUSED, STOPPED }

    companion object {
        const val EXTRA_RESULT_CODE   = "result_code"
        const val EXTRA_RESULT_DATA   = "result_data"

        @Volatile var currentState = State.IDLE
            private set

        fun getState() = currentState
    }

    // ── MediaProjection ──────────────────────────────────────────────────────
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // ── Screen metrics ───────────────────────────────────────────────────────
    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // ── Detection ────────────────────────────────────────────────────────────
    private var detector: SkipDetector? = null
    private var detectionJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Handler thread for image capture ────────────────────────────────────
    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    // ── Floating panel ───────────────────────────────────────────────────────
    private var floatingPanel: FloatingPanelView? = null

    // ── Wake lock ────────────────────────────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Cooldown flag ────────────────────────────────────────────────────────
    @Volatile private var clickCooldown = false
    @Volatile private var isProcessing  = false

    // ── Frame interval ───────────────────────────────────────────────────────
    private val frameIntervalMs = (1000L / SkipConfig.CAPTURE_FPS)

    override fun onCreate() {
        super.onCreate()
        setupNotificationChannel()
        getScreenMetrics()
        setupCaptureThread()

        // Acquire partial wake lock so service runs even when screen is off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FreeDramaAdSkipper::WakeLock"
        ).apply { acquire(60 * 60 * 1000L) } // 1 hour max

        Log.i(TAG, "Service created (${screenWidth}x${screenHeight} @${screenDensity}dpi)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SkipConfig.ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                startForeground(SkipConfig.NOTIFICATION_ID, buildNotification(State.RUNNING))

                if (resultCode != -1 && resultData != null) {
                    setupMediaProjection(resultCode, resultData)
                    setupFloatingPanel()
                    startDetection()
                } else {
                    Log.e(TAG, "Missing MediaProjection data")
                    stopSelf()
                }
            }
            SkipConfig.ACTION_PAUSE -> {
                pauseDetection()
            }
            SkipConfig.ACTION_STOP -> {
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    // ── MediaProjection setup ────────────────────────────────────────────────

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped externally")
                stopEverything()
            }
        }, captureHandler)

        // ImageReader: single slot, RGBA_8888, replace old frames immediately
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AdSkipperDisplay",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, captureHandler
        )

        detector = SkipDetector(screenWidth, screenHeight)
        Log.i(TAG, "MediaProjection + VirtualDisplay ready")
    }

    // ── Detection loop ───────────────────────────────────────────────────────

    private fun startDetection() {
        currentState = State.RUNNING
        updateNotification(State.RUNNING)
        floatingPanel?.setState(State.RUNNING)

        detectionJob = serviceScope.launch {
            Log.i(TAG, "Detection loop started at ${SkipConfig.CAPTURE_FPS} FPS")
            while (currentState == State.RUNNING) {
                val loopStart = System.currentTimeMillis()

                if (!clickCooldown && !isProcessing) {
                    isProcessing = true
                    captureAndAnalyze()
                    isProcessing = false
                }

                val elapsed = System.currentTimeMillis() - loopStart
                val sleep = frameIntervalMs - elapsed
                if (sleep > 0) delay(sleep)
            }
            Log.i(TAG, "Detection loop ended")
        }
    }

    private suspend fun captureAndAnalyze() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to exact screen width if needed
            val finalBitmap = if (bitmap.width != screenWidth) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            } else bitmap

            val clickPoint = detector?.detect(finalBitmap)
            if (clickPoint != null) {
                Log.i(TAG, "⚡ Skip button found! Clicking at (${clickPoint.x}, ${clickPoint.y})")
                val tapped = AdSkipperAccessibilityService.performTap(clickPoint)
                if (tapped) {
                    clickCooldown = true
                    serviceScope.launch {
                        delay(SkipConfig.CLICK_COOLDOWN_MS)
                        clickCooldown = false
                    }
                }
            }

            if (finalBitmap != bitmap) finalBitmap.recycle()
            bitmap.recycle()

        } finally {
            image.close()
        }
    }

    private fun pauseDetection() {
        currentState = State.PAUSED
        detectionJob?.cancel()
        updateNotification(State.PAUSED)
        floatingPanel?.setState(State.PAUSED)
        Log.i(TAG, "Detection paused")
    }

    fun resumeDetection() {
        if (currentState == State.PAUSED) {
            startDetection()
        }
    }

    private fun stopEverything() {
        currentState = State.STOPPED
        detectionJob?.cancel()
        floatingPanel?.removeFromWindow()
        floatingPanel = null
        cleanupProjection()
        detector?.close()
        wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Service fully stopped")
    }

    private fun cleanupProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    // ── Floating Panel ───────────────────────────────────────────────────────

    private fun setupFloatingPanel() {
        floatingPanel = FloatingPanelView(this).apply {
            addToWindow()
            setState(State.RUNNING)
            onStart  = { resumeDetection() }
            onPause  = { pauseDetection() }
            onStop   = { stopEverything() }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupCaptureThread() {
        captureThread = HandlerThread("AdSkipperCapture").also { it.start() }
        captureHandler = Handler(captureThread.looper)
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            SkipConfig.NOTIFICATION_CHANNEL_ID,
            SkipConfig.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "FreeDrama Ad Skipper running in background"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(state: State): Notification {
        val statusText = when (state) {
            State.RUNNING -> "🟢 Detecting ads..."
            State.PAUSED  -> "🟡 Paused"
            State.STOPPED -> "🔴 Stopped"
            State.IDLE    -> "⚪ Idle"
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AdSkipperService::class.java).apply { action = SkipConfig.ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AdSkipperService::class.java).apply { action = SkipConfig.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SkipConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("FreeDrama Ad Skipper")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(state: State) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SkipConfig.NOTIFICATION_ID, buildNotification(state))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        captureThread.quit()
        Log.i(TAG, "Service destroyed")
    }
}
