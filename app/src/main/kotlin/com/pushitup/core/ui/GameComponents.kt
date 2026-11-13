package com.pushitup.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushitup.core.theme.AccentGreen
import com.pushitup.core.theme.LocalPushItColors
import com.pushitup.core.theme.AccentGreenDark

// ─────────────────────────────────────────────────────────────────────────────
// Motion spec — the single source of truth for animation curves & durations.
// Brief: snappy feedback 100-300ms, medium 300-800ms, heavy 800ms+ (sparingly).
// ─────────────────────────────────────────────────────────────────────────────

object Motion {
    /** Snappy feedback — Material standard. */
    val Snappy    = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    /** Symmetric ease-in-out. */
    val EaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)
    /** Screen transitions. */
    val Screen    = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

    const val Micro  = 200   // button press, icon activation
    const val Medium = 400   // achievement pop, card entrance
    const val Slow   = 800   // celebrations

    /** Playful overshoot (~15%) for counters and pop-ins. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMedium,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Neon glow border — animated sweep along the card edge.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Glass card with an animated neon border: a bright highlight travels around
 * the card edge over a faint static border. Use for hero cards (today's target,
 * your leaderboard row). For plain cards keep using [glassCard].
 */
fun Modifier.neonGlassCard(
    glowColor    : Color = AccentGreen,
    cornerRadius : Dp    = 20.dp,
): Modifier = composed {
    val colors = LocalPushItColors.current
    val sweep  = rememberInfiniteTransition(label = "neon_sweep")
    val phase by sweep.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label         = "neon_phase",
    )

    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(colors.surfaceGlass)
        .drawWithContent {
            drawContent()
            val r      = cornerRadius.toPx()
            val stroke = 2.dp.toPx()
            // Static faint border
            drawRoundRect(
                color        = glowColor.copy(alpha = 0.25f),
                cornerRadius = CornerRadius(r),
                style        = Stroke(stroke),
            )
            // Travelling highlight — sweeping linear gradient
            val angle = phase * 2f * Math.PI.toFloat()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = kotlin.math.cos(angle) * size.width
            val dy = kotlin.math.sin(angle) * size.height
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        glowColor.copy(alpha = 0.90f),
                        Color.Transparent,
                    ),
                    start = Offset(cx - dx, cy - dy),
                    end   = Offset(cx + dx, cy + dy),
                ),
                cornerRadius = CornerRadius(r),
                style        = Stroke(stroke),
            )
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animated counter — smooth number transitions with overshoot bounce.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Number text that bounces (scale overshoot ~15%) every time [value] changes
 * and glows briefly in [glowColor]. Drop-in for rep counters, XP, streaks.
 */
@Composable
fun AnimatedCounter(
    value     : Int,
    modifier  : Modifier  = Modifier,
    style     : TextStyle = MaterialTheme.typography.displayLarge,
    color     : Color     = LocalPushItColors.current.textPrimary,
    glowColor : Color     = AccentGreen,
) {
    var previous by remember { mutableIntStateOf(value) }
    val bump = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value != previous) {
            previous = value
            bump.snapTo(1.15f)                       // overshoot
            bump.animateTo(1f, Motion.bouncy())      // settle
        }
    }

    val textColor by animateColorAsState(
        targetValue   = if (bump.value > 1.02f) glowColor else color,
        animationSpec = tween(Motion.Micro, easing = Motion.Snappy),
        label         = "counter_color",
    )

    Text(
        text     = value.toString(),
        modifier = modifier.scale(bump.value),
        style    = style,
        color    = textColor,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Pulsing neon CTA button — "START WORKOUT" style.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Large call-to-action with breathing glow ring and press-scale feedback.
 * Colors default to the cyan→purple brand gradient.
 */
@Composable
fun NeonCtaButton(
    text     : String,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
    icon     : androidx.compose.ui.graphics.vector.ImageVector? = null,
    colors   : List<Color> = listOf(AccentGreen, AccentGreenDark),
    enabled  : Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (pressed) 0.96f else 1f,
        animationSpec = tween(Motion.Micro, easing = Motion.Snappy),
        label         = "cta_press",
    )

    val breath = rememberInfiniteTransition(label = "cta_breath")
    val glow by breath.animateFloat(
        initialValue  = 0.35f,
        targetValue   = 0.85f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = Motion.EaseInOut), RepeatMode.Reverse,
        ),
        label = "cta_glow",
    )

    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .scale(pressScale)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Breathing glow halo behind the button
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color        = colors.first().copy(alpha = glow * 0.30f),
                cornerRadius = CornerRadius(22.dp.toPx()),
                topLeft      = Offset(-4.dp.toPx(), -4.dp.toPx()),
                size         = androidx.compose.ui.geometry.Size(
                    size.width + 8.dp.toPx(), size.height + 8.dp.toPx(),
                ),
                style = Stroke(width = 6.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        if (enabled) colors
                        else colors.map { it.copy(alpha = 0.35f) }
                    )
                )
                .clickable(
                    interactionSource = interaction,
                    indication        = null,
                    enabled           = enabled,
                    onClick           = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let {
                    androidx.compose.material3.Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint     = Color.Black,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text       = text,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color      = Color.Black,
                )
            }
        }
    }
}
