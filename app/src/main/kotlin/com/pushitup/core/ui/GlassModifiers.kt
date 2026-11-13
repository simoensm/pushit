package com.pushitup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushitup.core.theme.LocalPushItColors

/**
 * Glass card modifier — automatically adapts to the current light/dark theme.
 *
 * Uses [Modifier.composed] so it can read [LocalPushItColors] at composition
 * time without requiring a @Composable call site (regular fun stays compatible
 * with all existing callers that chain `.glassCard()` on a modifier).
 */
fun Modifier.glassCard(cornerRadius: Dp = 20.dp): Modifier = composed {
    val colors = LocalPushItColors.current
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(colors.surfaceGlass)
        .border(1.dp, colors.glassBorder, RoundedCornerShape(cornerRadius))
}

/**
 * Semi-transparent glass banner for overlays on top of camera or rich visual
 * backgrounds (form warning, phone-moving alert, etc.).
 *
 * The tintColor drives both the fill alpha and the border — pass the semantic
 * colour ([WarningAmber], [ErrorRed], [AccentGreen] …) and set
 * [backgroundAlpha] low (0.12–0.20) for subtlety.
 *
 * Does NOT depend on the theme bundle because it always renders over a dark
 * camera feed, so fixed alpha works correctly in both light and dark mode.
 */
fun Modifier.glassBanner(
    tintColor       : Color,
    cornerRadius    : Dp = 14.dp,
    backgroundAlpha : Float = 0.16f,
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(tintColor.copy(alpha = backgroundAlpha))
    .border(1.dp, tintColor.copy(alpha = 0.40f), RoundedCornerShape(cornerRadius))
