package com.pushitup.feature.workout.domain

enum class PushUpState {
    /** Waiting for calibration or session start. */
    IDLE,
    /** Person is in the UP (arms-extended) position. */
    UP,
    /** Person is in the DOWN (chest-near-floor) position. */
    DOWN,
    /** Collecting samples to find the person's range of motion. */
    CALIBRATING,
}
