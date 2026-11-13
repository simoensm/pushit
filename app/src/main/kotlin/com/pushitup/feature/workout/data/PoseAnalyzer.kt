package com.pushitup.feature.workout.data

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.pushitup.feature.workout.domain.DetectionResult
import com.pushitup.feature.workout.domain.LandmarkPoint
import com.pushitup.feature.workout.domain.PushUpDetector

/**
 * CameraX [ImageAnalysis.Analyzer] backed by ML Kit Pose Detection.
 *
 * ## Performance approach
 * The original 1 fps issue came from closing [ImageProxy] AFTER ML Kit finished
 * processing (~100 ms), which blocked CameraX from delivering the next frame.
 *
 * Fix:
 * 1. Copy the raw RGBA pixels into a reusable [Bitmap] — this is a fast memcpy.
 * 2. Close [imageProxy] IMMEDIATELY — CameraX can now queue the next frame.
 * 3. Rotate the bitmap to display orientation.
 * 4. Hand the bitmap off to [PoseDetector.process] — runs asynchronously on ML Kit's
 *    own thread.  The result arrives via [addOnSuccessListener] on the main thread.
 *
 * ## Coordinates
 * ML Kit returns landmark positions in pixels within the *analysed* image.
 * We pre-rotate the bitmap so landmarks are already in display orientation, then
 * normalise by the rotated image dimensions → [LandmarkPoint] with x, y ∈ [0, 1].
 *
 * ## Front-camera mirror
 * [androidx.camera.view.PreviewView] mirrors the live preview for the front camera.
 * ML Kit landmark x coords are NOT mirrored — callers must flip x when drawing:
 *   displayX = (1 − landmark.x) × canvasWidth
 */
class PoseAnalyzer(
    private val detector: PushUpDetector,
    private val onResult: (DetectionResult, List<LandmarkPoint>) -> Unit,
    private val onNoPersonDetected: () -> Unit = {},
) : ImageAnalysis.Analyzer {

    private val poseDetector: PoseDetector

    private var bitmapBuffer: Bitmap? = null
    private var rotatedBuffer: Bitmap? = null

    init {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
        Log.i(TAG, "ML Kit PoseDetector ready (STREAM_MODE accurate)")
    }

    override fun analyze(imageProxy: ImageProxy) {
        // ── 1. Copy RGBA pixels into a reusable buffer ────────────────────────
        val buf = bitmapBuffer?.takeIf {
            it.width == imageProxy.width && it.height == imageProxy.height
        } ?: Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            .also { bitmapBuffer = it }

        imageProxy.planes[0].buffer.rewind()
        buf.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        val rotDegrees = imageProxy.imageInfo.rotationDegrees

        // ── 2. Close IMMEDIATELY — pixel data is in `buf` ─────────────────────
        imageProxy.close()
        Log.v(TAG, "frame ${imageProxy.width}x${imageProxy.height} rot=$rotDegrees")

        // ── 3. Rotate to display orientation ──────────────────────────────────
        val rotated = rotateBitmap(buf, rotDegrees)

        // ── 4. Run ML Kit asynchronously ──────────────────────────────────────
        // InputImage.fromBitmap with rotation=0 because we already rotated.
        val inputImage = InputImage.fromBitmap(rotated, 0)
        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                val lms = buildLandmarks(pose, rotated.width, rotated.height)
                Log.d(TAG, "pose result: ${if (lms != null) "detected ${lms.size} pts" else "no person"}")
                if (lms == null) { onNoPersonDetected(); return@addOnSuccessListener }

                val nose      = lms[PoseLandmark.NOSE]
                val lShoulder = lms[PoseLandmark.LEFT_SHOULDER]
                val rShoulder = lms[PoseLandmark.RIGHT_SHOULDER]
                val lHip      = lms[PoseLandmark.LEFT_HIP]
                val rHip      = lms[PoseLandmark.RIGHT_HIP]
                val lAnkle    = lms[PoseLandmark.LEFT_ANKLE]
                val rAnkle    = lms[PoseLandmark.RIGHT_ANKLE]
                val lWrist    = lms[PoseLandmark.LEFT_WRIST]
                val rWrist    = lms[PoseLandmark.RIGHT_WRIST]

                if (lShoulder.visibility < 0.4f || rShoulder.visibility < 0.4f) {
                    onNoPersonDetected(); return@addOnSuccessListener
                }

                val detResult = detector.processPose(
                    noseY           = nose.y,
                    shoulderWidthN  = kotlin.math.abs(lShoulder.x - rShoulder.x),
                    noseConfidence  = nose.visibility,
                    leftHipY        = lHip.y,
                    rightHipY       = rHip.y,
                    leftShoulderY   = lShoulder.y,
                    rightShoulderY  = rShoulder.y,
                    leftAnkleY      = lAnkle.y,
                    rightAnkleY     = rAnkle.y,
                    leftWristX      = lWrist.x,
                    rightWristX     = rWrist.x,
                    leftShoulderX   = lShoulder.x,
                    rightShoulderX  = rShoulder.x,
                    wristVisibility = minOf(lWrist.visibility, rWrist.visibility),
                )
                onResult(detResult, lms)
            }
            .addOnFailureListener { e -> Log.w(TAG, "ML Kit inference failed: ${e.message}") }
    }

    /**
     * Convert ML Kit [com.google.mlkit.vision.pose.Pose] landmarks to a
     * list of [LandmarkPoint] with coordinates normalised to [0, 1].
     * Returns null if no person is detected (landmark list is empty).
     */
    private fun buildLandmarks(
        pose: com.google.mlkit.vision.pose.Pose,
        imageWidth: Int,
        imageHeight: Int,
    ): List<LandmarkPoint>? {
        if (pose.allPoseLandmarks.isEmpty()) return null
        return (0..32).map { idx ->
            val lm = pose.getPoseLandmark(idx)
            if (lm != null) {
                LandmarkPoint(
                    x = (lm.position.x / imageWidth).coerceIn(0f, 1f),
                    y = (lm.position.y / imageHeight).coerceIn(0f, 1f),
                    visibility = lm.inFrameLikelihood,
                )
            } else {
                LandmarkPoint(x = 0f, y = 0f, visibility = 0f)
            }
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val (rw, rh) = if (degrees == 90 || degrees == 270)
            src.height to src.width else src.width to src.height
        val dst = rotatedBuffer?.takeIf { it.width == rw && it.height == rh }
            ?: Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888).also { rotatedBuffer = it }
        val canvas = android.graphics.Canvas(dst)
        val m = Matrix().apply {
            postRotate(degrees.toFloat(), src.width / 2f, src.height / 2f)
            postTranslate((rw - src.width) / 2f, (rh - src.height) / 2f)
        }
        canvas.drawBitmap(src, m, null)
        return dst
    }

    fun close() {
        poseDetector.close()
        bitmapBuffer?.recycle(); bitmapBuffer = null
        rotatedBuffer?.recycle(); rotatedBuffer = null
    }

    companion object {
        private const val TAG = "PoseAnalyzer"
    }
}
