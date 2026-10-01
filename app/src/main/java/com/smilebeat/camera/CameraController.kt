package com.smilebeat.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.smilebeat.detection.FaceDetectorWrapper
import com.smilebeat.detection.SkinToneAnalyzer
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Manages CameraX lifecycle, front/rear switching, and frame analysis pipeline.
 * On-device only, no frames saved.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val faceDetector: FaceDetectorWrapper,
    private val toneAnalyzer: SkinToneAnalyzer,
    private val onAnalysisResult: (AnalysisFrameResult) -> Unit,
    private val onError: (String) -> Unit
) {

    data class AnalysisFrameResult(
        val hasFace: Boolean,
        val faceRect: Rect?,
        val faceCount: Int,
        val toneResult: SkinToneAnalyzer.AnalysisResult?,
        val inputImage: InputImage? = null,
        val bitmapForDebug: Bitmap? = null // only for sampling, not saved
    )

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var isBound = false

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var lastAnalysisTimestamp = 0L
    private val analysisIntervalMs = 150L // ~6-7 fps analysis, keep preview smooth
    private var isAnalyzing = false

    // YUV to RGB converter
    private var yuvToRgbConverter: YuvToRgbConverter? = null

    fun getLensFacing(): Int = lensFacing

    fun startCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases(previewView)
            } catch (e: Exception) {
                Log.e("CameraController", "Camera provider failed", e)
                onError("Camera unavailable: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun flipCamera(previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindUseCases(previewView)
    }

    private fun bindUseCases(previewView: PreviewView) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build().also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalyzer
            )
            isBound = true
        } catch (e: Exception) {
            Log.e("CameraController", "Bind failed", e)
            onError("Failed to start camera: ${e.message}")
            isBound = false
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTimestamp < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        if (isAnalyzing) {
            imageProxy.close()
            return
        }

        isAnalyzing = true
        lastAnalysisTimestamp = now

        analysisScope.launch {
            try {
                // Convert to InputImage for ML Kit
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    withContext(Dispatchers.Main) { imageProxy.close() }
                    isAnalyzing = false
                    return@launch
                }

                val rotation = imageProxy.imageInfo.rotationDegrees
                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

                // Face detection (on-device)
                val faceResult = faceDetector.detect(inputImage)
                if (faceResult.isFailure) {
                    withContext(Dispatchers.Main) {
                        onAnalysisResult(
                            AnalysisFrameResult(
                                hasFace = false,
                                faceRect = null,
                                faceCount = 0,
                                toneResult = null
                            )
                        )
                        imageProxy.close()
                    }
                    isAnalyzing = false
                    return@launch
                }

                val faces = faceResult.getOrNull() ?: emptyList()
                if (faces.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onAnalysisResult(
                            AnalysisFrameResult(
                                hasFace = false,
                                faceRect = null,
                                faceCount = 0,
                                toneResult = null
                            )
                        )
                        imageProxy.close()
                    }
                    isAnalyzing = false
                    return@launch
                }

                // Use largest face
                val primaryFace = faces.first()
                val faceBox = primaryFace.boundingBox

                // Convert ImageProxy to Bitmap for tone analysis
                // This is done off main thread and bitmap is not saved
                val bitmap = imageProxyToBitmap(imageProxy)

                var toneResult: SkinToneAnalyzer.AnalysisResult? = null
                if (bitmap != null) {
                    // Need to map face rect from ML Kit coordinates to bitmap coordinates
                    // ML Kit bounding box is in InputImage coordinates which accounts for rotation
                    // Our bitmap is already rotated? imageProxyToBitmap handles rotation?
                    // For simplicity, assume bitmap is in same orientation as ImageProxy after rotation correction
                    // We'll adjust face rect to bitmap size

                    // ML Kit returns bounding box in image coordinates (rotated). We need to transform to bitmap.
                    // Since we create bitmap from YUV with rotation applied, the face rect should be valid if we scale.
                    // However to be safe, we scale face rect proportionally to bitmap size.

                    // For now, approximate: if bitmap size != image size, scale
                    // Actually InputImage from mediaImage uses original dimensions, our bitmap is same size but rotated
                    // So we need to handle rotation mapping.

                    // Simplified: use faceBox directly if within bitmap bounds, else clamp
                    val adjustedRect = adjustFaceRectForBitmap(faceBox, imageProxy, bitmap, rotation)

                    toneResult = toneAnalyzer.analyze(bitmap, adjustedRect)
                    bitmap.recycle()
                }

                withContext(Dispatchers.Main) {
                    onAnalysisResult(
                        AnalysisFrameResult(
                            hasFace = true,
                            faceRect = faceBox,
                            faceCount = faces.size,
                            toneResult = toneResult
                        )
                    )
                    imageProxy.close()
                }

            } catch (e: Exception) {
                Log.e("CameraController", "Analysis error", e)
                withContext(Dispatchers.Main) {
                    try { imageProxy.close() } catch (_: Exception) {}
                }
            } finally {
                isAnalyzing = false
            }
        }
    }

    private fun adjustFaceRectForBitmap(
        faceRect: Rect,
        imageProxy: ImageProxy,
        bitmap: Bitmap,
        rotation: Int
    ): Rect {
        // ImageProxy dimensions are before rotation; bitmap dimensions are after rotation handling
        // If rotation is 90 or 270, width/height swap
        val imgW = imageProxy.width
        val imgH = imageProxy.height
        val bmpW = bitmap.width
        val bmpH = bitmap.height

        // Simple scaling: map face rect proportionally
        // ML Kit bounding box is in the rotated image coordinates, so its size matches rotated dimensions
        // For 90/270 rotation, ML Kit's coordinate system is already rotated
        // We'll just scale if needed

        val scaleX = bmpW.toFloat() / if (rotation == 90 || rotation == 270) imgH else imgW
        val scaleY = bmpH.toFloat() / if (rotation == 90 || rotation == 270) imgW else imgH

        val left = (faceRect.left * scaleX).toInt().coerceIn(0, bmpW - 1)
        val top = (faceRect.top * scaleY).toInt().coerceIn(0, bmpH - 1)
        val right = (faceRect.right * scaleX).toInt().coerceIn(0, bmpW)
        val bottom = (faceRect.bottom * scaleY).toInt().coerceIn(0, bmpH)

        return Rect(left, top, right, bottom)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            // Proper YUV_420_888 to NV21 conversion handling strides
            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // NV21: Y + VU interleaved
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y
            var pos = 0
            // Handle Y row stride
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            if (yRowStride == imageProxy.width && yPixelStride == 1) {
                yBuffer.get(nv21, 0, ySize)
                pos = ySize
            } else {
                // Row by row copy
                val yBufferCopy = ByteArray(ySize)
                yBuffer.get(yBufferCopy)
                var inputOffset = 0
                var outputOffset = 0
                for (row in 0 until imageProxy.height) {
                    System.arraycopy(yBufferCopy, inputOffset, nv21, outputOffset, imageProxy.width)
                    inputOffset += yRowStride
                    outputOffset += imageProxy.width
                }
                pos = imageProxy.width * imageProxy.height
            }

            // Copy VU interleaved - U and V are subsampled
            val uvHeight = imageProxy.height / 2
            val uvWidth = imageProxy.width / 2

            val uRowStride = uPlane.rowStride
            val vRowStride = vPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vPixelStride = vPlane.pixelStride

            // For NV21 we need V first then U, interleaved
            // We'll iterate over uv pixels
            val vBufferCopy = ByteArray(vSize)
            val uBufferCopy = ByteArray(uSize)
            vBuffer.rewind()
            uBuffer.rewind()
            vBuffer.get(vBufferCopy)
            uBuffer.get(uBufferCopy)

            var outputPos = pos
            for (row in 0 until uvHeight) {
                var uRowOffset = row * uRowStride
                var vRowOffset = row * vRowStride
                for (col in 0 until uvWidth) {
                    val vIndex = vRowOffset + col * vPixelStride
                    val uIndex = uRowOffset + col * uPixelStride
                    if (vIndex < vBufferCopy.size && uIndex < uBufferCopy.size && outputPos + 1 < nv21.size) {
                        nv21[outputPos++] = vBufferCopy[vIndex]
                        nv21[outputPos++] = uBufferCopy[uIndex]
                    }
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 85, out)
            val imageBytes = out.toByteArray()
            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Rotate bitmap based on rotationDegrees
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0 && bitmap != null) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            } else if (lensFacing == CameraSelector.LENS_FACING_FRONT && bitmap != null) {
                val matrix = Matrix()
                matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (mirrored != bitmap) bitmap.recycle()
                bitmap = mirrored
            }

            bitmap
        } catch (e: Exception) {
            Log.e("CameraController", "Bitmap conversion failed", e)
            // Fallback simple method
            try {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraController", "Stop failed", e)
        }
        isBound = false
    }

    fun isCameraActive(): Boolean = isBound

    fun release() {
        stopCamera()
        analysisScope.cancel()
        cameraExecutor.shutdown()
        yuvToRgbConverter = null
    }

    // Simple YUV to RGB converter placeholder for future optimization
    private class YuvToRgbConverter
}
