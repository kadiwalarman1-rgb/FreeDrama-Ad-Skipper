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
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile var currentState = State.IDLE
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var detector: SkipDetector? = null
    private var detectionJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var floatingPanel: FloatingPanelView? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var clickCooldown = false
    @Volatile private var isProcessing  = false

    private val frameIntervalMs = (1000L / SkipConfig.CAPTURE_FPS)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        getScreenMetrics()

        captureThread = HandlerThread("AdSkipperCapture").also { it.start() }
        captureHandler = Handler(captureThread.looper)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FreeDramaAdSkipper::WakeLock"
        ).apply { acquire(3600000L) }

        Log.i(TAG, "Service created — ${screenWidth}x${screenHeight}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            SkipConfig.ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)

                // ── CRITICAL: startForeground FIRST, then MediaProjection ────────
                // Android 14+ (and 16) requires foreground to be active before
                // using the MediaProjection token. This is the correct order.
                startForeground(
                    SkipConfig.NOTIFICATION_ID,
                    buildNotification(State.RUNNING)
                )

                if (code != -1 && data != null) {
                    setupProjectionAndStart(code, data)
                } else {
                    Log.e(TAG, "No projection data — stopping")
                    stopSelf()
                }
            }

            SkipConfig.ACTION_PAUSE -> pauseDetection()
            SkipConfig.ACTION_STOP  -> stopEverything()
        }
        return START_NOT_STICKY
    }

    // ── Setup projection AFTER foreground is started ─────────────────────────
    private fun setupProjectionAndStart(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped externally")
                stopEverything()
            }
        }, captureHandler)

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

        setupFloatingPanel()
        startDetection()
        Log.i(TAG, "MediaProjection + VirtualDisplay ready ✓")
    }

    private fun startDetection() {
        currentState = State.RUNNING
        updateNotification(State.RUNNING)
        floatingPanel?.setState(State.RUNNING)

        detectionJob = serviceScope.launch {
            Log.i(TAG, "Detection started @ ${SkipConfig.CAPTURE_FPS} FPS")
            while (currentState == State.RUNNING) {
                val t0 = System.currentTimeMillis()
                if (!clickCooldown && !isProcessing) {
                    isProcessing = true
                    captureAndAnalyze()
                    isProcessing = false
                }
                val sleep = frameIntervalMs - (System.currentTimeMillis() - t0)
                if (sleep > 0) delay(sleep)
            }
        }
    }

    private suspend fun captureAndAnalyze() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane      = image.planes[0]
            val buffer     = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride  = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)

            val final = if (bmp.width != screenWidth)
                Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
            else bmp

            val hit = detector?.detect(final)
            if (hit != null) {
                Log.i(TAG, "⚡ CLICK at (${hit.x}, ${hit.y})")
                val ok = AdSkipperAccessibilityService.performTap(hit)
                if (ok) {
                    clickCooldown = true
                    serviceScope.launch {
                        delay(SkipConfig.CLICK_COOLDOWN_MS)
                        clickCooldown = false
                    }
                }
            }

            if (final != bmp) final.recycle()
            bmp.recycle()
        } finally {
            image.close()
        }
    }

    private fun pauseDetection() {
        currentState = State.PAUSED
        detectionJob?.cancel()
        updateNotification(State.PAUSED)
        floatingPanel?.setState(State.PAUSED)
    }

    fun resumeDetection() {
        if (currentState == State.PAUSED) startDetection()
    }

    private fun stopEverything() {
        currentState = State.STOPPED
        detectionJob?.cancel()
        floatingPanel?.removeFromWindow()
        floatingPanel = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        detector?.close()
        wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupFloatingPanel() {
        floatingPanel = FloatingPanelView(this).apply {
            addToWindow()
            setState(State.RUNNING)
            onStart = { resumeDetection() }
            onPause = { pauseDetection() }
            onStop  = { stopEverything() }
        }
    }

    private fun getScreenMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            SkipConfig.NOTIFICATION_CHANNEL_ID,
            SkipConfig.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(state: State): Notification {
        val text = when (state) {
            State.RUNNING -> "🟢 Detecting ads..."
            State.PAUSED  -> "🟡 Paused"
            else          -> "🔴 Stopped"
        }

        val open  = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1,
            Intent(this, AdSkipperService::class.java).apply { action = SkipConfig.ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop  = PendingIntent.getService(this, 2,
            Intent(this, AdSkipperService::class.java).apply { action = SkipConfig.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, SkipConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("FreeDrama Ad Skipper")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pause)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setOngoing(true).setSilent(true).build()
    }

    private fun updateNotification(state: State) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(SkipConfig.NOTIFICATION_ID, buildNotification(state))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        captureThread.quit()
    }
}
