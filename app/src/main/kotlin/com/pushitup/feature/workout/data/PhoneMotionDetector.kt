package com.pushitup.feature.workout.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Detects whether the phone is being moved/picked up using the accelerometer.
 *
 * ## Algorithm
 * A low-pass filter isolates the gravity component of the raw accelerometer
 * signal.  Subtracting gravity gives linear acceleration — near zero when the
 * phone is still, non-zero when it is being moved.
 *
 *   linearAccel = rawAccel − lowPassGravity
 *   magnitude   = √(x² + y² + z²)
 *
 * If [magnitude] exceeds [MOVE_THRESHOLD_MS2] the phone is flagged as moving
 * immediately.  It is only cleared after [STABLE_REQUIRED_MS] of continuous
 * readings below the threshold, preventing rapid flickering when the user
 * sets the phone back down.
 *
 * ## Usage
 * Call [start] when a workout session begins and [stop] when it ends (or in
 * onCleared).  Observe [isMoving] to gate rep counting.
 */
class PhoneMotionDetector(context: Context) {

    private val sensorManager  = context.getSystemService(SensorManager::class.java)
    private val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** True while the phone appears to be in motion. */
    private val _isMoving = MutableStateFlow(false)
    val isMoving: StateFlow<Boolean> = _isMoving.asStateFlow()

    // Low-pass filter state (tracks gravity component)
    private val gravity = FloatArray(3) { 0f }
    private var gravityInitialised = false

    // Stability timer
    private var stableSinceMs = 0L

    private var listener: SensorEventListener? = null

    // ── Tuning constants ──────────────────────────────────────────────────────

    companion object {
        /** Low-pass filter coefficient — higher = smoother gravity estimate. */
        private const val ALPHA = 0.85f

        /**
         * Linear acceleration magnitude (m/s²) above which the phone is
         * considered moving.  Picking up a phone typically exceeds 3–5 m/s²;
         * 2.0 m/s² is a safe threshold that avoids false positives from
         * minor vibrations while the phone sits on the floor.
         */
        private const val MOVE_THRESHOLD_MS2 = 2.0f

        /**
         * Milliseconds of continuous stable readings required before clearing
         * the moving flag.  Prevents flickering when the phone is placed back
         * down with a small bounce.
         */
        private const val STABLE_REQUIRED_MS = 1_500L

        private const val TAG = "PhoneMotionDetector"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer — motion detection disabled")
            return
        }
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = handleAccelerometer(event)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(l, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        listener = l
        Log.i(TAG, "Accelerometer registered")
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        gravityInitialised = false
        gravity.fill(0f)
        _isMoving.value = false
        Log.i(TAG, "Accelerometer unregistered")
    }

    // ── Signal processing ─────────────────────────────────────────────────────

    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Seed the gravity filter with the first reading so we don't get a
        // false positive spike on the very first frame.
        if (!gravityInitialised) {
            gravity[0] = ax; gravity[1] = ay; gravity[2] = az
            gravityInitialised = true
            return
        }

        // Low-pass: tracks slow gravity component
        gravity[0] = ALPHA * gravity[0] + (1f - ALPHA) * ax
        gravity[1] = ALPHA * gravity[1] + (1f - ALPHA) * ay
        gravity[2] = ALPHA * gravity[2] + (1f - ALPHA) * az

        // High-pass: removes gravity → linear acceleration
        val lx = ax - gravity[0]
        val ly = ay - gravity[1]
        val lz = az - gravity[2]
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

        val now = SystemClock.elapsedRealtime()

        if (magnitude > MOVE_THRESHOLD_MS2) {
            // Phone is moving — flag immediately and reset stability timer
            if (!_isMoving.value) {
                Log.d(TAG, "Phone moving — magnitude=%.2f m/s²".format(magnitude))
                _isMoving.value = true
            }
            stableSinceMs = now
        } else if (_isMoving.value) {
            // Phone appears stable — wait for STABLE_REQUIRED_MS before clearing
            if (now - stableSinceMs >= STABLE_REQUIRED_MS) {
                Log.d(TAG, "Phone stable again")
                _isMoving.value = false
            }
        }
    }
}
