package com.freedrama.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val TAG = "AdSkipperA11y"

/**
 * Accessibility service that performs tap gestures on behalf of the app.
 * No window content scanning is needed — only gesture dispatch.
 *
 * This is the ONLY reliable way to click on another app's UI
 * without root on Android 7+.
 */
class AdSkipperAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AdSkipperAccessibilityService? = null
            private set

        fun isEnabled() = instance != null

        /**
         * Perform an instant tap at (x, y) on screen.
         * Called from AdSkipperService when a skip button is detected.
         */
        fun performTap(point: PointF): Boolean {
            val svc = instance ?: run {
                Log.w(TAG, "Accessibility service not connected")
                return false
            }
            return svc.tap(point)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected ✓")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed — we only dispatch gestures, not listen to events
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    /**
     * Dispatch a single tap gesture at the given screen coordinates.
     * Duration is 1ms — absolute minimum for instant response.
     */
    private fun tap(point: PointF): Boolean {
        return try {
            val path = Path().apply { moveTo(point.x, point.y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Tap completed at (${point.x}, ${point.y})")
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.w(TAG, "Tap cancelled at (${point.x}, ${point.y})")
                }
            }, null)

            Log.d(TAG, "Tap dispatched=$dispatched at (${point.x}, ${point.y})")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "Tap error: ${e.message}")
            false
        }
    }
}
