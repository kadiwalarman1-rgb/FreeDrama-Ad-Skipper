package com.freedrama.adskipper

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "SkipDetector"

/**
 * Detects "Skip Ad", "Ad Skip", cross/X buttons using ML Kit OCR.
 * All detection is on-device with zero network dependency.
 *
 * Returns the CENTER point (in full-screen coordinates) of the detected button,
 * or null if nothing is found.
 */
class SkipDetector(private val screenWidth: Int, private val screenHeight: Int) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Precompute pixel coordinates of ROI rectangles
    private val skipRoiPx = RectF(
        screenWidth  * SkipConfig.ROI_LEFT_FRACTION,
        screenHeight * SkipConfig.ROI_TOP_FRACTION,
        screenWidth  * SkipConfig.ROI_RIGHT_FRACTION,
        screenHeight * SkipConfig.ROI_BOTTOM_FRACTION
    )

    private val closeRoiPx = RectF(
        screenWidth  * SkipConfig.CLOSE_BTN_ROI_LEFT,
        screenHeight * SkipConfig.CLOSE_BTN_ROI_TOP,
        screenWidth  * SkipConfig.CLOSE_BTN_ROI_RIGHT,
        screenHeight * SkipConfig.CLOSE_BTN_ROI_BOTTOM
    )

    /**
     * Analyze a full-screen bitmap and return click coordinates if a skip/close button is found.
     */
    suspend fun detect(fullBitmap: Bitmap): PointF? {
        // ── Pass 1: Check top-right ROI for skip-related text ─────────────────
        val skipResult = analyzeRegion(fullBitmap, skipRoiPx) { block ->
            val text = block.text.trim().lowercase()
            val isSkip = SkipConfig.SKIP_BUTTON_TEXTS.any { pattern ->
                text.contains(pattern.lowercase())
            }
            isSkip
        }
        if (skipResult != null) return skipResult

        // ── Pass 2: Check top-right ROI for X / close symbols ─────────────────
        val closeResult = analyzeRegion(fullBitmap, closeRoiPx) { block ->
            val text = block.text.trim()
            val isClose = text.length <= SkipConfig.CLOSE_SYMBOL_MAX_LENGTH &&
                    SkipConfig.CLOSE_SYMBOLS.any { sym -> text.contains(sym, ignoreCase = true) }
            isClose
        }
        return closeResult
    }

    /**
     * Crop the bitmap to [roi], run OCR, and find a text block matching [predicate].
     * Returns full-screen coordinates of the matched block center.
     */
    private suspend fun analyzeRegion(
        fullBitmap: Bitmap,
        roi: RectF,
        predicate: (com.google.mlkit.vision.text.Text.TextBlock) -> Boolean
    ): PointF? {
        val cropX = roi.left.toInt().coerceIn(0, fullBitmap.width - 1)
        val cropY = roi.top.toInt().coerceIn(0, fullBitmap.height - 1)
        val cropW = (roi.width().toInt()).coerceIn(1, fullBitmap.width - cropX)
        val cropH = (roi.height().toInt()).coerceIn(1, fullBitmap.height - cropY)

        val cropped = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
        val image = InputImage.fromBitmap(cropped, 0)

        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var found: PointF? = null
                    for (block in visionText.textBlocks) {
                        if (predicate(block)) {
                            val boundingBox = block.boundingBox
                            if (boundingBox != null) {
                                // Translate from crop coordinates → full-screen coordinates
                                val centerX = cropX + boundingBox.exactCenterX()
                                val centerY = cropY + boundingBox.exactCenterY()
                                Log.d(TAG, "Detected '${block.text}' at ($centerX, $centerY)")
                                found = PointF(centerX, centerY)
                                break
                            }
                        }
                    }
                    if (!cont.isCompleted) cont.resume(found)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "OCR failed: ${e.message}")
                    if (!cont.isCompleted) cont.resume(null)
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}
