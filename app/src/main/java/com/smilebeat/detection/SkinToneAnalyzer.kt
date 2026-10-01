package com.smilebeat.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Analyzes apparent visible skin-tone brightness from a face region.
 * Strictly visual color analysis, on-device, no identity or race inference.
 *
 * Approach:
 * - Takes a face bounding box and a full frame bitmap (or cropped bitmap)
 * - Defines a central facial region to avoid eyes, lips, hair, background
 * - Samples pixels with stride
 * - Filters non-skin-like pixels using HSV saturation/value heuristics
 * - Computes robust brightness metric using HSV Value channel + normalized luminance
 * - Returns normalized tone score 0..1 where 0=very dark appearance, 1=very bright
 */
class SkinToneAnalyzer {

    data class AnalysisResult(
        val toneScore: Float, // 0..1, dark->bright
        val confidence: Float, // 0..1 how reliable
        val sampleCount: Int,
        val isPoorLighting: Boolean,
        val debugAvgR: Int = 0,
        val debugAvgG: Int = 0,
        val debugAvgB: Int = 0
    )

    /**
     * Analyze from bitmap and face rect (face rect in bitmap coordinates).
     * Bitmap should be the full camera frame or a cropped face+margin.
     */
    fun analyze(bitmap: Bitmap, faceRect: Rect): AnalysisResult? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        if (faceRect.width() <= 10 || faceRect.height() <= 10) return null

        // Clamp face rect to bitmap bounds
        val clampedFace = Rect(
            max(0, faceRect.left),
            max(0, faceRect.top),
            min(bitmap.width, faceRect.right),
            min(bitmap.height, faceRect.bottom)
        )
        if (clampedFace.width() < 20 || clampedFace.height() < 20) return null

        // Define central region: avoid eyes (top 35%), mouth (bottom 30%), sides (15% each)
        // This targets nose/cheek central area
        val fw = clampedFace.width()
        val fh = clampedFace.height()
        val centralLeft = clampedFace.left + (fw * 0.25f).toInt()
        val centralTop = clampedFace.top + (fh * 0.35f).toInt()
        val centralRight = clampedFace.left + (fw * 0.75f).toInt()
        val centralBottom = clampedFace.top + (fh * 0.70f).toInt()

        val centralRect = Rect(
            max(0, centralLeft),
            max(0, centralTop),
            min(bitmap.width, centralRight),
            min(bitmap.height, centralBottom)
        )
        if (centralRect.width() < 10 || centralRect.height() < 10) return null

        // Sample pixels with stride to reduce work
        val stride = max(2, min(centralRect.width(), centralRect.height()) / 40) // ~40x40 samples
        val hsv = FloatArray(3)

        var sumV = 0.0
        var sumLuma = 0.0
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var validCount = 0
        var totalCount = 0
        var veryDarkCount = 0
        var veryBrightCount = 0

        // For median estimation, collect values
        val vValues = mutableListOf<Float>()

        for (y in centralRect.top until centralRect.bottom step stride) {
            for (x in centralRect.left until centralRect.right step stride) {
                totalCount++
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                Color.RGBToHSV(r, g, b, hsv)
                val h = hsv[0] // 0-360
                val s = hsv[1] // 0-1
                val v = hsv[2] // 0-1

                // Filter obvious non-skin/background:
                // - Very low saturation = gray/white/black background
                // - Very low value = near black (shadow, not skin)
                // - Very high value + low sat = glare/white
                // - Hue outside plausible skin hue range (roughly 0-50 and 340-360) but keep lenient to avoid bias
                // We use a permissive filter to avoid racial bias - only remove clear non-skin
                val isLowSat = s < 0.15f
                val isGlare = v > 0.95f && s < 0.25f
                val isNearBlack = v < 0.08f

                if (isLowSat || isGlare || isNearBlack) {
                    // count for lighting detection
                    if (v < 0.15f) veryDarkCount++
                    if (v > 0.9f) veryBrightCount++
                    continue
                }

                // Normalized luminance (perceived brightness) - ITU BT.709
                val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0

                sumV += v
                sumLuma += luma
                sumR += r
                sumG += g
                sumB += b
                validCount++
                vValues.add(v)
            }
        }

        if (validCount < 10) {
            // Not enough valid skin pixels
            return null
        }

        // Robust median for V to reduce outlier impact
        vValues.sort()
        val medianV = vValues[vValues.size / 2]

        // Mean V and Luma
        val meanV = (sumV / validCount).toFloat()
        val meanLuma = (sumLuma / validCount).toFloat()

        // Combined tone score: weighted blend of median V and mean luma for robustness
        // 70% median V (HSV) + 30% luma (perceived)
        // Both are 0..1 where 0 dark, 1 bright
        val combined = (medianV * 0.7f + meanLuma * 0.3f)

        // Optional: mild gamma to make mid-tones more discriminative
        // Use sqrt to expand darker range slightly
        val toneScore = sqrt(combined.coerceIn(0f, 1f))

        // Confidence based on valid ratio and lighting
        val validRatio = validCount.toFloat() / totalCount.coerceAtLeast(1)
        val lightingPenalty = if (veryDarkCount > totalCount * 0.6f || veryBrightCount > totalCount * 0.6f) 0.5f else 1f
        val confidence = (validRatio * lightingPenalty).coerceIn(0f, 1f)

        val isPoorLighting = (meanV < 0.18f || meanV > 0.92f || validRatio < 0.35f)

        return AnalysisResult(
            toneScore = toneScore.coerceIn(0f, 1f),
            confidence = confidence,
            sampleCount = validCount,
            isPoorLighting = isPoorLighting,
            debugAvgR = (sumR / validCount).toInt(),
            debugAvgG = (sumG / validCount).toInt(),
            debugAvgB = (sumB / validCount).toInt()
        )
    }

    /**
     * Analyze directly from YUV? For now we use bitmap path.
     * This is a lightweight alternative if you have RGB bytes.
     */
    fun analyzeFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        faceRect: Rect
    ): AnalysisResult? {
        // Convert IntArray to bitmap quickly (re-use if possible)
        // This is less efficient but works for testing
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        val result = analyze(bmp, faceRect)
        bmp.recycle()
        return result
    }
}
