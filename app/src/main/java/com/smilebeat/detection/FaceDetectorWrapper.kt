package com.smilebeat.detection

import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

data class DetectedFace(
    val boundingBox: Rect,
    val trackingId: Int? = null
)

class FaceDetectorWrapper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .enableTracking()
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)

    var isInitialized: Boolean = true
        private set

    /**
     * Detect faces from InputImage. Returns list sorted by largest bounding box first.
     * On-device only, no data leaves device.
     */
    suspend fun detect(image: InputImage): Result<List<DetectedFace>> {
        return try {
            val faces = detector.process(image).await()
            val mapped = faces.map { face: Face ->
                DetectedFace(
                    boundingBox = face.boundingBox,
                    trackingId = face.trackingId
                )
            }.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {}
    }
}
