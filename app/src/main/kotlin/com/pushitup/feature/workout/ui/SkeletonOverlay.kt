package com.pushitup.feature.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushitup.core.theme.AccentGreen
import com.pushitup.core.theme.ErrorRed
import com.pushitup.feature.workout.domain.LandmarkPoint

/**
 * Draws the skeleton overlay on top of the CameraX preview.
 *
 * [landmarks] are normalized: x, y ∈ [0, 1] relative to the display-oriented image.
 * Multiply by canvas size to get pixel positions.
 *
 * Front-camera mirror: [PreviewView] mirrors the live preview but ML Kit landmark
 * x values are not mirrored.  We flip x here:
 *   displayX = (1 − landmark.x) × canvasWidth
 */
@Composable
fun SkeletonOverlay(
    landmarks: List<LandmarkPoint>?,
    badForm: Boolean,
    modifier: Modifier = Modifier,
) {
    val lineColor  = if (badForm) ErrorRed else AccentGreen
    val jointColor = Color.White

    Canvas(modifier = modifier.fillMaxSize()) {
        if (landmarks.isNullOrEmpty() || landmarks.size < 29) return@Canvas

        fun lm(idx: Int): Offset? {
            val pt = landmarks.getOrNull(idx) ?: return null
            if (pt.visibility < 0.2f) return null
            return Offset(
                x = (1f - pt.x) * size.width,   // flip for front-camera mirror
                y = pt.y * size.height,
            )
        }

        // ── Bones ─────────────────────────────────────────────────────────────
        bone(lm(11), lm(13), lineColor)   // L shoulder → L elbow
        bone(lm(13), lm(15), lineColor)   // L elbow    → L wrist
        bone(lm(12), lm(14), lineColor)   // R shoulder → R elbow
        bone(lm(14), lm(16), lineColor)   // R elbow    → R wrist
        bone(lm(11), lm(12), lineColor)   // L shoulder ↔ R shoulder
        bone(lm(11), lm(23), lineColor)   // L shoulder → L hip
        bone(lm(12), lm(24), lineColor)   // R shoulder → R hip
        bone(lm(23), lm(24), lineColor)   // L hip      ↔ R hip
        bone(lm(23), lm(25), lineColor)   // L hip      → L knee
        bone(lm(25), lm(27), lineColor)   // L knee     → L ankle
        bone(lm(24), lm(26), lineColor)   // R hip      → R knee
        bone(lm(26), lm(28), lineColor)   // R knee     → R ankle

        // ── Joints ────────────────────────────────────────────────────────────
        listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28).forEach { idx ->
            lm(idx)?.let { drawCircle(jointColor, radius = 9f, center = it) }
        }
        lm(0)?.let { drawCircle(AccentGreen, radius = 12f, center = it) }  // nose
    }
}

private fun DrawScope.bone(a: Offset?, b: Offset?, color: Color) {
    if (a == null || b == null) return
    drawLine(color = color, start = a, end = b, strokeWidth = 5f, cap = StrokeCap.Round)
}
