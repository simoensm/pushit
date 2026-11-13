package com.pushitup.feature.workout.domain

// PoseAngles is kept for the unit tests only.
// Active detection now uses nose-Y auto-calibration in PushUpDetector.

import kotlin.math.acos
import kotlin.math.sqrt

object PoseAngles {

    fun angleDegrees(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float,
    ): Float {
        val v1x = ax - bx; val v1y = ay - by
        val v2x = cx - bx; val v2y = cy - by
        val dot  = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        if (mag1 == 0f || mag2 == 0f) return 0f
        return Math.toDegrees(acos((dot / (mag1 * mag2)).coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    fun hipOffset(shoulderY: Float, hipY: Float, ankleY: Float): Float {
        val lineY = (shoulderY + ankleY) / 2f
        return kotlin.math.abs(hipY - lineY)
    }

    const val ELBOW_UP_THRESHOLD   = 150f
    const val ELBOW_DOWN_THRESHOLD =  80f
    const val NOSE_FLOOR_RATIO     =  0.85f
    const val BAD_FORM_THRESHOLD   =  0.08f
}
