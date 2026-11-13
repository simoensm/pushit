package com.pushitup.core.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.pushitup.R
import com.pushitup.core.navigation.Screen

/**
 * @param target        Which UI region to spotlight.  null = full dark scrim (no cutout).
 * @param mascotOnRight Leo peeks from the bottom-right corner instead of bottom-left.
 * @param mascotRes     Drawable for this step's pose.
 */
data class TourStep(
    val id            : Int,
    val targetRoute   : String?,
    @StringRes val titleRes  : Int,
    @StringRes val bodyRes   : Int,
    val target        : TourTarget? = null,
    val mascotOnRight : Boolean     = false,
    @DrawableRes val mascotRes: Int = R.drawable.ic_mascot_crossed,
    val isLast        : Boolean     = false,
)

val ALL_TOUR_STEPS: List<TourStep> = listOf(

    // 0 ── Welcome — full scrim, navigate to Dashboard
    TourStep(
        id          = 0,
        targetRoute = Screen.Dashboard.route,
        titleRes    = R.string.tour_0_title,
        bodyRes     = R.string.tour_0_body,
        target      = null,
        mascotRes   = R.drawable.ic_mascot_crossed,
    ),

    // 1 ── RankHeroCard — rank tier + stars progress bar
    TourStep(
        id          = 1,
        targetRoute = null,
        titleRes    = R.string.tour_1_title,
        bodyRes     = R.string.tour_1_body,
        target      = TourTarget.RANK_HERO_CARD,
        mascotRes   = R.drawable.ic_mascot_pointing,
    ),

    // 2 ── Daily ring + streak row
    TourStep(
        id          = 2,
        targetRoute = null,
        titleRes    = R.string.tour_2_title,
        bodyRes     = R.string.tour_2_body,
        target      = TourTarget.RING_AND_STREAK,
        mascotRes   = R.drawable.ic_mascot_pointing,
    ),

    // 3 ── Start Workout button
    TourStep(
        id          = 3,
        targetRoute = null,
        titleRes    = R.string.tour_3_title,
        bodyRes     = R.string.tour_3_body,
        target      = TourTarget.START_WORKOUT_BTN,
        mascotRes   = R.drawable.ic_mascot_flex,
    ),

    // 4 ── Challenges screen — full scrim
    TourStep(
        id          = 4,
        targetRoute = Screen.Challenges.route,
        titleRes    = R.string.tour_4_title,
        bodyRes     = R.string.tour_4_body,
        target      = null,
        mascotRes   = R.drawable.ic_mascot_relaxed,
    ),

    // 5 ── Leaderboard — tab chip row (Global)
    TourStep(
        id            = 5,
        targetRoute   = Screen.Leaderboard.route,
        titleRes      = R.string.tour_5_title,
        bodyRes       = R.string.tour_5_body,
        target        = TourTarget.LEADERBOARD_TABS,
        mascotOnRight = true,
        mascotRes     = R.drawable.ic_mascot_crossed,
    ),

    // 6 ── Leaderboard — same chip row, explaining Friends tab
    TourStep(
        id            = 6,
        targetRoute   = null,
        titleRes      = R.string.tour_6_title,
        bodyRes       = R.string.tour_6_body,
        target        = TourTarget.LEADERBOARD_TABS,
        mascotOnRight = true,
        mascotRes     = R.drawable.ic_mascot_pointing,
    ),

    // 7 ── Friends — header row with "Add Friend" button
    TourStep(
        id          = 7,
        targetRoute = Screen.Friends.route,
        titleRes    = R.string.tour_7_title,
        bodyRes     = R.string.tour_7_body,
        target      = TourTarget.FRIENDS_HEADER,
        mascotRes   = R.drawable.ic_mascot_pointing,
    ),

    // 8 ── Profile — full scrim, last step
    TourStep(
        id          = 8,
        targetRoute = Screen.Profile.route,
        titleRes    = R.string.tour_8_title,
        bodyRes     = R.string.tour_8_body,
        target      = null,
        mascotRes   = R.drawable.ic_mascot_flex,
        isLast      = true,
    ),
)
