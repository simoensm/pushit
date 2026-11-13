package com.pushitup.feature.workout.domain

/**
 * Global tuning knobs for the push-up detector.
 * Written by the admin panel at runtime; read by [PushUpDetector] on every frame.
 * All fields are @Volatile so writes from the Settings thread are immediately
 * visible to the camera analysis thread without needing synchronisation.
 */
object DetectionConfig {
    /** Minimum milliseconds between two counted reps. */
    @Volatile var debounceMs: Long = 700L

    /**
     * Consecutive DOWN-zone frames required before accepting a DOWN state.
     * Higher = more resistant to noise spikes; lower = more responsive.
     */
    @Volatile var minDownFrames: Int = 5

    /**
     * Fraction of the calibrated range used as the UP/DOWN threshold margin (0.10–0.40).
     * Lower value → wider dead-zone between UP and DOWN → harder to accidentally flip states.
     * Higher value → thresholds closer to extremes → more sensitive to small movements.
     */
    @Volatile var thresholdMargin: Float = 0.20f

    fun reset() {
        debounceMs      = 700L
        minDownFrames   = 5
        thresholdMargin = 0.20f
    }
}
