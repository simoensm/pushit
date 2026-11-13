package com.pushitup.feature.workout.domain

import com.pushitup.domain.model.PushUpVariant

/**
 * Auto-calibrating push-up rep counter with variant-aware hand validation.
 *
 * ## Movement signal
 * Nose Y coordinate (0 = top, 1 = bottom) is the primary signal; shoulder width
 * (wider = closer to camera) is the fallback when nose confidence is low.
 *
 * ## Calibration
 * The first ~1.5 s of motion sets [upThreshold] / [downThreshold] from the 5th
 * and 95th percentiles of the nose Y buffer. Requires [MIN_RANGE] of travel.
 *
 * ## Variant hand validation
 * Before counting a rep, [validateHandPosition] checks that wrist placement
 * matches [currentVariant]:
 *   - DIAMS (Diamond): wrist distance < 35 % of shoulder width
 *   - ECARTEEES (Wide):  wrist distance > 130 % of shoulder width
 *   - MILITAIRE:        wrist distance between 40–100 % of shoulder width
 *   - NORMAL / PIQUEES / UNE_MAIN: no wrist constraint
 *
 * When the rep would count but hands are wrong, [DetectionResult.wrongHandPosition]
 * is set to true and the rep is NOT counted — prompting the user to fix their form.
 */
class PushUpDetector(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // All tuning values are read from DetectionConfig on each frame so the
    // admin panel can change them at runtime without restarting the session.
    /** Variant set from WorkoutViewModel when the user picks a push-up type. */
    var currentVariant: PushUpVariant = PushUpVariant.NORMAL

    // ── Calibration ───────────────────────────────────────────────────────────

    private val MIN_RANGE     = 0.07f
    private val CALIB_SAMPLES = 45   // ~1.5 s at 30 fps

    private val calibBuf = ArrayDeque<Float>(CALIB_SAMPLES * 2)
    private var calibrated = false

    private var upThreshold   = 0f
    private var downThreshold = 0f

    // ── State machine ─────────────────────────────────────────────────────────

    private var repState: PushUpState = PushUpState.IDLE
    private var lastRepMs: Long = 0L
    private var _repCount = 0
    val repCount: Int get() = _repCount

    /**
     * Number of consecutive frames the signal has been in the DOWN zone.
     * We require at least DetectionConfig.minDownFrames before accepting a DOWN→UP
     * transition, which prevents landmark noise spikes from triggering false reps.
     */
    private var downFrameCount = 0

    // ── Public API ────────────────────────────────────────────────────────────

    fun reset() {
        calibBuf.clear()
        calibrated     = false
        repState       = PushUpState.IDLE
        lastRepMs      = 0L
        _repCount      = 0
        downFrameCount = 0
    }

    /**
     * Feed one pose frame.
     *
     * Wrist / shoulder X coords are normalised to [0, 1] in display space.
     * They are used only for variant validation — if visibility is low, validation
     * is skipped (rep is counted normally).
     */
    fun processPose(
        noseY: Float,
        shoulderWidthN: Float,
        noseConfidence: Float,
        leftHipY: Float,
        rightHipY: Float,
        leftShoulderY: Float,
        rightShoulderY: Float,
        leftAnkleY: Float,
        rightAnkleY: Float,
        // Hand-position inputs for variant validation
        leftWristX: Float      = 0f,
        rightWristX: Float     = 0f,
        leftShoulderX: Float   = 0f,
        rightShoulderX: Float  = 0f,
        wristVisibility: Float = 0f,   // min of left/right wrist in-frame likelihood
    ): DetectionResult {

        val signal  = if (noseConfidence > 0.6f) noseY else (1f - shoulderWidthN)
        val badForm = detectBadForm(leftHipY, rightHipY, leftShoulderY, rightShoulderY, leftAnkleY, rightAnkleY)

        // ── Calibration phase ──────────────────────────────────────────────────
        if (!calibrated) {
            calibBuf.addLast(signal)
            if (calibBuf.size > CALIB_SAMPLES * 2) calibBuf.removeFirst()
            if (calibBuf.size >= CALIB_SAMPLES) tryCalibrate()

            return DetectionResult(
                signal            = signal,
                state             = PushUpState.CALIBRATING,
                repCounted        = false,
                repCount          = _repCount,
                badFormDetected   = badForm,
                wrongHandPosition = false,
                calibProgress     = (calibBuf.size.toFloat() / CALIB_SAMPLES).coerceIn(0f, 1f),
            )
        }

        // ── Detection phase ────────────────────────────────────────────────────
        val now               = clock()
        var repCounted        = false
        var wrongHandPosition = false

        when {
            signal >= downThreshold -> {
                // Accumulate consecutive DOWN frames; only commit to DOWN state once
                // we've seen MIN_DOWN_FRAMES in a row — this filters out noise spikes.
                downFrameCount++
                if (downFrameCount >= DetectionConfig.minDownFrames) {
                    repState = PushUpState.DOWN
                }
            }
            signal <= upThreshold -> {
                // Any frame outside the DOWN zone resets the DOWN frame counter.
                downFrameCount = 0

                if (repState == PushUpState.DOWN) {
                    // Valid DOWN→UP transition: user has genuinely reached the bottom
                    // and is now coming back up.
                    repState = PushUpState.UP
                    if (now - lastRepMs >= DetectionConfig.debounceMs) {
                        val handOk = validateHandPosition(
                            leftWristX, rightWristX,
                            leftShoulderX, rightShoulderX,
                            wristVisibility
                        )
                        if (handOk) {
                            _repCount++
                            lastRepMs = now
                            repCounted = true
                        } else {
                            wrongHandPosition = true
                        }
                    }
                } else if (repState == PushUpState.IDLE) {
                    repState = PushUpState.UP
                }
                // If repState is already UP: signal stays in UP zone between reps — no action needed.
            }
            // Signal is between upThreshold and downThreshold (dead zone): hold current state,
            // but stop accumulating DOWN frames since we left the DOWN zone.
            else -> {
                downFrameCount = 0
            }
        }

        return DetectionResult(
            signal            = signal,
            state             = repState,
            repCounted        = repCounted,
            repCount          = _repCount,
            badFormDetected   = badForm,
            wrongHandPosition = wrongHandPosition,
            calibProgress     = 1f,
        )
    }

    // ── Variant validation ────────────────────────────────────────────────────

    /**
     * Returns true if wrist placement matches [currentVariant].
     * Returns true unconditionally when wrists aren't visible or the variant
     * has no hand constraint (NORMAL, PIQUEES, UNE_MAIN).
     */
    private fun validateHandPosition(
        leftWristX: Float, rightWristX: Float,
        leftShoulderX: Float, rightShoulderX: Float,
        wristVisibility: Float,
    ): Boolean {
        if (wristVisibility < 0.4f) return true

        val wristDist    = kotlin.math.abs(leftWristX - rightWristX)
        val shoulderDist = kotlin.math.abs(leftShoulderX - rightShoulderX)
        if (shoulderDist < 0.01f) return true

        val ratio = wristDist / shoulderDist  // < 1 = close, > 1 = wide

        return when (currentVariant) {
            PushUpVariant.DIAMS     -> ratio < 0.35f             // diamond: hands touching
            PushUpVariant.ECARTEEES -> ratio > 1.30f             // wide: hands well outside shoulders
            PushUpVariant.MILITAIRE -> ratio in 0.40f..1.00f     // military: shoulder-width or narrower
            else                    -> true                       // no constraint
        }
    }

    // ── Bad-form detection ────────────────────────────────────────────────────

    private fun detectBadForm(
        leftHipY: Float, rightHipY: Float,
        leftShoulderY: Float, rightShoulderY: Float,
        leftAnkleY: Float, rightAnkleY: Float,
    ): Boolean {
        val avgHip      = (leftHipY + rightHipY) / 2f
        val avgShoulder = (leftShoulderY + rightShoulderY) / 2f
        val avgAnkle    = (leftAnkleY + rightAnkleY) / 2f
        val midLine     = (avgShoulder + avgAnkle) / 2f
        return kotlin.math.abs(avgHip - midLine) > 0.09f
    }

    // ── Calibration ───────────────────────────────────────────────────────────

    private fun tryCalibrate() {
        val sorted = calibBuf.sorted()
        val p05    = sorted[(sorted.size * 0.05f).toInt().coerceAtLeast(0)]
        val p95    = sorted[(sorted.size * 0.95f).toInt().coerceAtMost(sorted.size - 1)]
        val range  = p95 - p05
        if (range >= MIN_RANGE) {
            val m = DetectionConfig.thresholdMargin          // read live from config
            upThreshold   = p05 + range * m
            downThreshold = p95 - range * m
            calibrated    = true
        }
    }

    val isCalibrated: Boolean     get() = calibrated
    val upThresholdValue: Float   get() = upThreshold
    val downThresholdValue: Float get() = downThreshold

    /** Test-only: skip the calibration buffer and set thresholds directly. */
    internal fun forceCalibrate(upThresh: Float, downThresh: Float) {
        upThreshold   = upThresh
        downThreshold = downThresh
        calibrated    = true
    }
}

data class DetectionResult(
    val signal: Float,
    val state: PushUpState,
    val repCounted: Boolean,
    val repCount: Int,
    val badFormDetected: Boolean,
    /** True when a DOWN→UP transition occurred but wrists weren't in the right spot for the selected variant. */
    val wrongHandPosition: Boolean = false,
    /** 0.0–1.0 progress toward completing calibration. */
    val calibProgress: Float,
)
