package com.freedrama.adskipper

/**
 * Centralized configuration for the FreeDrama Ad-Skipper.
 * Edit this file to update skip button text patterns or detection sensitivity.
 */
object SkipConfig {

    // ── Text patterns to detect (case-insensitive) ──────────────────────────
    val SKIP_BUTTON_TEXTS = listOf(
        "skip ad",
        "ad skip",
        "skip ads",
        "skip",
        "skip >",
        "skip»",
        "close ad",
        "dismiss"
    )

    // ── Region of Interest (ROI) — Top-Right corner of screen ───────────────
    // Values are fractions of screen dimensions (0.0 = top/left, 1.0 = bottom/right)
    const val ROI_TOP_FRACTION    = 0.0f   // Start from very top
    const val ROI_BOTTOM_FRACTION = 0.20f  // Down to 20% of screen height
    const val ROI_LEFT_FRACTION   = 0.60f  // From 60% of width (right portion)
    const val ROI_RIGHT_FRACTION  = 1.0f   // To the right edge

    // ── Also check full top bar for X/close button ──────────────────────────
    const val CLOSE_BTN_ROI_TOP    = 0.0f
    const val CLOSE_BTN_ROI_BOTTOM = 0.12f
    const val CLOSE_BTN_ROI_LEFT   = 0.80f
    const val CLOSE_BTN_ROI_RIGHT  = 1.0f

    // ── Detection & Performance ──────────────────────────────────────────────
    const val CAPTURE_FPS          = 15        // Frames per second for screen analysis
    const val CLICK_COOLDOWN_MS    = 2000L     // Ms to wait after a click before detecting again
    const val MIN_TEXT_CONFIDENCE  = 0.6f      // Min OCR confidence (0–1)

    // ── Cross/X button detection ─────────────────────────────────────────────
    // Characters that represent a close/dismiss button
    val CLOSE_SYMBOLS = listOf("✕", "✖", "×", "X", "x", "❌", "⊗", "⊘")
    const val CLOSE_SYMBOL_MAX_LENGTH = 3  // Max chars for a symbol to be considered a close btn

    // ── Notification channel ─────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID   = "adskipper_channel"
    const val NOTIFICATION_CHANNEL_NAME = "FreeDrama Ad Skipper"
    const val NOTIFICATION_ID           = 1001

    // ── Service actions ──────────────────────────────────────────────────────
    const val ACTION_START  = "com.freedrama.adskipper.START"
    const val ACTION_PAUSE  = "com.freedrama.adskipper.PAUSE"
    const val ACTION_STOP   = "com.freedrama.adskipper.STOP"
    const val ACTION_STATUS = "com.freedrama.adskipper.STATUS"

    // ── Shared prefs ─────────────────────────────────────────────────────────
    const val PREFS_NAME          = "adskipper_prefs"
    const val PREF_FIRST_LAUNCH   = "first_launch"
    const val PREF_AUTO_START     = "auto_start_on_boot"
}
