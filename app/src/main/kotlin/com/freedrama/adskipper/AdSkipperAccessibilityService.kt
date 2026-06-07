package com.freedrama.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val TAG = "AdSkipperA11y"

class AdSkipperAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AdSkipperAccessibilityService? = null
            private set

        /**
         * FIXED: Uses Settings.Secure to check if service is ENABLED in settings.
         * Old code used instance != null which fails when service isn't running yet.
         */
        fun isEnabled(context: Context? = null): Boolean {
            // Method 1: Check live instance (most reliable — service actually running)
            if (instance != null) return true

            // Method 2: Check via Settings.Secure (enabled in settings but maybe not started yet)
            if (context != null) {
                return isEnabledInSettings(context)
            }
            return false
        }

        fun isEnabledInSettings(context: Context): Boolean {
            val expectedComponent = ComponentName(context, AdSkipperAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentStr = colonSplitter.next()
                val component = ComponentName.unflattenFromString(componentStr)
                if (component != null && component == expectedComponent) {
                    return true
                }
            }
            return false
        }

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
        // Not needed — we only dispatch gestures
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

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
                    Log.w(TAG, "Tap cancelled")
                }
            }, null)
            Log.d(TAG, "Tap dispatched=$dispatched")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "Tap error: ${e.message}")
            false
        }
    }
}
