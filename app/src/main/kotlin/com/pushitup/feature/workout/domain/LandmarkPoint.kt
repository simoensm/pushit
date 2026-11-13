package com.pushitup.feature.workout.domain

/**
 * A single body landmark with normalized coordinates.
 *
 * [x] and [y] are in [0, 1] relative to the display-oriented image.
 * Multiply by canvas size to get pixel positions for drawing.
 *
 * [visibility] is a confidence value in [0, 1].  Points below ~0.2 are
 * typically off-screen or occluded and should not be drawn.
 *
 * Indices follow the MediaPipe / ML Kit 33-landmark convention:
 *   0=nose  11=L_shoulder  12=R_shoulder  13=L_elbow  14=R_elbow
 *   15=L_wrist  16=R_wrist  23=L_hip  24=R_hip
 *   25=L_knee  26=R_knee  27=L_ankle  28=R_ankle
 */
data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val visibility: Float = 1f,
)
