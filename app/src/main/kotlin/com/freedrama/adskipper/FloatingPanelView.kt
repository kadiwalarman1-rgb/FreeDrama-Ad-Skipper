package com.freedrama.adskipper

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView

/**
 * Draggable floating control panel shown as a system overlay.
 * Persists across all apps. Can be dragged anywhere on screen.
 */
class FloatingPanelView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rootView: View = LayoutInflater.from(context).inflate(R.layout.floating_panel, null)

    // Callbacks from service
    var onStart: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onStop:  (() -> Unit)? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 16
        y = 200
    }

    // Views
    private val tvStatus   = rootView.findViewById<TextView>(R.id.tvStatus)
    private val btnStart   = rootView.findViewById<ImageButton>(R.id.btnStart)
    private val btnPause   = rootView.findViewById<ImageButton>(R.id.btnPause)
    private val btnStop    = rootView.findViewById<ImageButton>(R.id.btnStop)
    private val statusDot  = rootView.findViewById<View>(R.id.statusDot)

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        setupDrag()
        setupButtons()
    }

    private fun setupButtons() {
        btnStart.setOnClickListener {
            onStart?.invoke()
        }
        btnPause.setOnClickListener {
            onPause?.invoke()
        }
        btnStop.setOnClickListener {
            onStop?.invoke()
        }
    }

    private fun setupDrag() {
        val dragHandle = rootView.findViewById<View>(R.id.dragHandle)
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                else -> false
            }
        }
    }

    fun addToWindow() {
        try {
            windowManager.addView(rootView, params)
        } catch (e: Exception) {
            // Already added or no permission
        }
    }

    fun removeFromWindow() {
        try {
            windowManager.removeView(rootView)
        } catch (e: Exception) {
            // Already removed
        }
    }

    fun setState(state: AdSkipperService.State) {
        rootView.post {
            when (state) {
                AdSkipperService.State.RUNNING -> {
                    tvStatus.text = "Running"
                    statusDot.setBackgroundResource(R.drawable.dot_green)
                    btnStart.isEnabled = false
                    btnPause.isEnabled = true
                    btnStop.isEnabled  = true
                    btnStart.alpha = 0.4f
                    btnPause.alpha = 1.0f
                }
                AdSkipperService.State.PAUSED -> {
                    tvStatus.text = "Paused"
                    statusDot.setBackgroundResource(R.drawable.dot_yellow)
                    btnStart.isEnabled = true
                    btnPause.isEnabled = false
                    btnStop.isEnabled  = true
                    btnStart.alpha = 1.0f
                    btnPause.alpha = 0.4f
                }
                AdSkipperService.State.STOPPED, AdSkipperService.State.IDLE -> {
                    tvStatus.text = "Stopped"
                    statusDot.setBackgroundResource(R.drawable.dot_red)
                    btnStart.isEnabled = false
                    btnPause.isEnabled = false
                    btnStop.isEnabled  = false
                }
            }
        }
    }
}
