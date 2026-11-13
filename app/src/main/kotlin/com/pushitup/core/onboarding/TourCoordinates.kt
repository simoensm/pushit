package com.pushitup.core.onboarding

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

/** Each spotlighted UI region gets a key here. */
enum class TourTarget {
    RANK_HERO_CARD,
    RING_AND_STREAK,
    START_WORKOUT_BTN,
    LEADERBOARD_TABS,
    FRIENDS_HEADER,
}

/**
 * Process-wide singleton that collects the actual window-relative [Rect] for every
 * [TourTarget].  Composables call [register] from their [onGloballyPositioned] callback;
 * [OnboardingOverlay] reads [rects] to position the spotlight precisely, regardless of
 * screen size, font scale, or system-bar height.
 */
object TourCoordinates {
    /** Snapshot state map — reads in any composable will recompose automatically. */
    val rects = mutableStateMapOf<TourTarget, Rect>()

    fun register(target: TourTarget, coords: LayoutCoordinates) {
        rects[target] = coords.boundsInWindow()
    }

    fun clear() = rects.clear()
}
