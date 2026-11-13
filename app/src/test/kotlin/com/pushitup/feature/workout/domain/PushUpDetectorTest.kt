package com.pushitup.feature.workout.domain

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PushUpDetector].
 *
 * Strategy:
 * - Supply a controllable clock to simulate debounce timing without Thread.sleep.
 * - Call [PushUpDetector.forceCalibrate] to skip the 45-frame calibration buffer.
 * - Use synthetic noseY values: UP pose = 0.2 (well below upThreshold 0.4),
 *   DOWN pose = 0.8 (well above downThreshold 0.6).
 *
 * Thresholds forced in setup: upThreshold = 0.4f, downThreshold = 0.6f
 * So the dead-zone is 0.4–0.6 and a real rep needs:
 *   noseY ≥ 0.6 (DOWN) → noseY ≤ 0.4 (UP) to count.
 */
class PushUpDetectorTest {

    private var fakeTimeMs = 0L
    private lateinit var detector: PushUpDetector

    @Before
    fun setup() {
        fakeTimeMs = 0L
        DetectionConfig.debounceMs    = 300L  // fast debounce for tests
        DetectionConfig.minDownFrames = 1     // accept DOWN in one frame
        detector = PushUpDetector(clock = { fakeTimeMs })
        // Skip calibration; set known thresholds directly.
        detector.forceCalibrate(upThresh = 0.4f, downThresh = 0.6f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** noseY = 0.2 → well below upThreshold (0.4) → UP zone. */
    private fun feedUpPose(): DetectionResult = detector.processPose(
        noseY          = 0.2f,
        shoulderWidthN = 0.4f,
        noseConfidence = 1.0f,
        leftHipY       = 0.5f, rightHipY      = 0.5f,
        leftShoulderY  = 0.5f, rightShoulderY = 0.5f,
        leftAnkleY     = 0.9f, rightAnkleY    = 0.9f,
    )

    /** noseY = 0.8 → well above downThreshold (0.6) → DOWN zone. */
    private fun feedDownPose(): DetectionResult = detector.processPose(
        noseY          = 0.8f,
        shoulderWidthN = 0.4f,
        noseConfidence = 1.0f,
        leftHipY       = 0.5f, rightHipY      = 0.5f,
        leftShoulderY  = 0.5f, rightShoulderY = 0.5f,
        leftAnkleY     = 0.9f, rightAnkleY    = 0.9f,
    )

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is IDLE and rep count is 0`() {
        assertEquals(0, detector.repCount)
        assertFalse(detector.isCalibrated.not()) // forceCalibrate set it to true
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Test
    fun `UP pose transitions state to UP`() {
        val result = feedUpPose()
        assertEquals(PushUpState.UP, result.state)
        assertFalse(result.repCounted)
    }

    @Test
    fun `DOWN pose transitions state to DOWN`() {
        val result = feedDownPose()
        assertEquals(PushUpState.DOWN, result.state)
        assertFalse(result.repCounted)
    }

    @Test
    fun `DOWN then UP counts one rep`() {
        feedDownPose()         // → DOWN
        fakeTimeMs = 400L      // past debounce
        val result = feedUpPose()  // → UP + count
        assertTrue(result.repCounted)
        assertEquals(1, detector.repCount)
        assertEquals(PushUpState.UP, result.state)
    }

    @Test
    fun `full cycle UP-DOWN-UP counts exactly one rep`() {
        feedUpPose()
        feedDownPose()
        fakeTimeMs = 400L
        feedUpPose()
        assertEquals(1, detector.repCount)
    }

    @Test
    fun `three full cycles count three reps`() {
        repeat(3) {
            feedUpPose()
            feedDownPose()
            fakeTimeMs += 400L
            feedUpPose()
        }
        assertEquals(3, detector.repCount)
    }

    // ── Debounce ──────────────────────────────────────────────────────────────

    @Test
    fun `second DOWN-to-UP within debounce window does NOT count`() {
        feedDownPose()
        fakeTimeMs = 400L
        feedUpPose()          // rep 1 at t=400
        feedDownPose()
        fakeTimeMs = 600L     // only 200 ms after last rep (< 300 ms debounce)
        val result = feedUpPose()
        assertFalse(result.repCounted)
        assertEquals(1, detector.repCount)
    }

    @Test
    fun `second rep counts after debounce window expires`() {
        feedDownPose()
        fakeTimeMs = 400L
        feedUpPose()          // rep 1 at t=400
        feedDownPose()
        fakeTimeMs = 750L     // 350 ms after rep 1 → past 300 ms debounce
        feedUpPose()
        assertEquals(2, detector.repCount)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears rep count and recalibrates`() {
        feedDownPose()
        fakeTimeMs = 400L
        feedUpPose()
        assertEquals(1, detector.repCount)

        detector.reset()
        assertEquals(0, detector.repCount)
        assertFalse(detector.isCalibrated)
    }

    // ── No false positives ────────────────────────────────────────────────────

    @Test
    fun `UP-to-UP without DOWN in between does not count`() {
        feedUpPose()
        fakeTimeMs = 400L
        val result = feedUpPose()
        assertFalse(result.repCounted)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun `DOWN-to-DOWN does not count`() {
        feedDownPose()
        fakeTimeMs = 400L
        val result = feedDownPose()
        assertFalse(result.repCounted)
        assertEquals(0, detector.repCount)
    }

    @Test
    fun `DOWN from IDLE then UP counts (IDLE treated same as UP)`() {
        // No prior UP — but DOWN→UP from IDLE is a valid rep start
        feedDownPose()
        fakeTimeMs = 400L
        val result = feedUpPose()
        assertTrue(result.repCounted)
        assertEquals(1, detector.repCount)
    }

    // ── Bad form detection ────────────────────────────────────────────────────

    @Test
    fun `bad form flag raised when hips are significantly above midline`() {
        val result = detector.processPose(
            noseY          = 0.2f,
            shoulderWidthN = 0.4f,
            noseConfidence = 1.0f,
            leftHipY       = 0.15f, rightHipY      = 0.15f,  // hips very high
            leftShoulderY  = 0.5f,  rightShoulderY = 0.5f,
            leftAnkleY     = 0.9f,  rightAnkleY    = 0.9f,
        )
        // midLine = (0.5 + 0.9) / 2 = 0.7; |0.15 - 0.7| = 0.55 > 0.09 → bad form
        assertTrue(result.badFormDetected)
    }

    @Test
    fun `bad form flag clear when body is well aligned`() {
        val result = detector.processPose(
            noseY          = 0.2f,
            shoulderWidthN = 0.4f,
            noseConfidence = 1.0f,
            leftHipY       = 0.7f, rightHipY      = 0.7f,   // hips on midline
            leftShoulderY  = 0.5f, rightShoulderY = 0.5f,
            leftAnkleY     = 0.9f, rightAnkleY    = 0.9f,
        )
        // midLine = (0.5 + 0.9) / 2 = 0.7; |0.7 - 0.7| = 0.0 ≤ 0.09 → good form
        assertFalse(result.badFormDetected)
    }
}
