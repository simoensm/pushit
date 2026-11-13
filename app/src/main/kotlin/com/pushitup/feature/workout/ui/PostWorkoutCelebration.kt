package com.pushitup.feature.workout.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pushitup.R
import com.pushitup.core.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

// ── Confetti particle data ─────────────────────────────────────────────────────

private data class Confetti(
    val x          : Float,   // 0..1 normalized screen x
    val y          : Float,   // start y (< 0 = above screen)
    val size       : Float,
    val color      : Color,
    val speed      : Float,
    val wobble     : Float,
    val wobbleOff  : Float,
    val rotation   : Float,
)

private val CONFETTI_COLORS = listOf(
    Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFFFD700),
    Color(0xFFFF6B35), Color(0xFFEC4899), Color(0xFF8B5CF6),
)

private fun generateConfetti(count: Int = 60): List<Confetti> =
    List(count) {
        Confetti(
            x         = Random.nextFloat(),
            y         = -Random.nextFloat() * 0.4f - 0.05f,
            size      = Random.nextFloat() * 10f + 6f,
            color     = CONFETTI_COLORS.random(),
            speed     = Random.nextFloat() * 0.3f + 0.15f,
            wobble    = Random.nextFloat() * 0.08f + 0.02f,
            wobbleOff = Random.nextFloat() * 6.28f,
            rotation  = Random.nextFloat() * 360f,
        )
    }

// ── Main celebration overlay ───────────────────────────────────────────────────

@Composable
fun PostWorkoutCelebrationOverlay(
    rewards   : List<WorkoutReward>,
    onDismiss : () -> Unit,
) {
    if (rewards.isEmpty()) return

    // Particle list — stable across recompositions
    val confetti = remember { generateConfetti(70) }

    // Global entrance animation
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); entered = true }

    // Confetti time driver 0..∞
    val infiniteTransition = rememberInfiniteTransition(label = "confetti_time")
    val timeDriver by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label         = "time",
    )

    // Mascot bounce
    val mascotBounce by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "mascot_bounce",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0F1A), Color(0xFF060D0F), Color(0xFF020507))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {

        // ── Confetti layer ────────────────────────────────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()

            confetti.forEachIndexed { idx, p ->
                val t  = ((timeDriver + idx * 0.013f) % 1f)
                val cx = p.x * w + sin((t * 6.28f + p.wobbleOff).toDouble()).toFloat() * p.wobble * w
                val cy = (p.y + t * p.speed * 2.8f) * h
                if (cy > h + 20f) return@forEachIndexed
                Box(
                    modifier = Modifier
                        .size(p.size.dp)
                        .absoluteOffset { IntOffset(cx.toInt(), cy.toInt()) }
                        .rotate(t * p.rotation)
                        .clip(if (idx % 3 == 0) RoundedCornerShape(2.dp) else CircleShape)
                        .background(p.color.copy(alpha = 0.85f)),
                )
            }
        }

        // ── Radial glow ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(360.dp)
                .background(
                    Brush.radialGradient(
                        listOf(AccentGreen.copy(alpha = 0.10f), Color.Transparent)
                    )
                ),
        )

        // ── Main card ─────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = entered,
            enter   = fadeIn(tween(400)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { it / 2 },
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF0D1117)))
                    )
                    .border(1.dp, AccentGreen.copy(alpha = 0.30f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {

                // ── Leo mascot flex ───────────────────────────────────────────
                Image(
                    painter             = painterResource(R.drawable.ic_mascot_flex),
                    contentDescription  = "Leo celebrating",
                    contentScale        = ContentScale.Fit,
                    modifier            = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            translationY = -mascotBounce * 10f
                            scaleX       = 1f + mascotBounce * 0.04f
                            scaleY       = 1f + mascotBounce * 0.04f
                        },
                )

                // ── Header ────────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "COME ON!",
                        style         = MaterialTheme.typography.headlineSmall,
                        fontWeight    = FontWeight.Black,
                        color         = AccentGreen,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "You're doing well, keep it up!",
                        style  = MaterialTheme.typography.bodyMedium,
                        color  = TextSecondary,
                    )
                }

                // ── Reward cards ──────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rewards.forEachIndexed { idx, reward ->
                        RewardCard(reward = reward, index = idx)
                    }
                }

                // ── Continue button ───────────────────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape  = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.EmojiEvents, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Continue",
                        fontWeight = FontWeight.Black,
                        fontSize   = 16.sp,
                        color      = Color.Black,
                    )
                }
            }
        }
    }
}

// ── Single reward card ─────────────────────────────────────────────────────────

@Composable
private fun RewardCard(reward: WorkoutReward, index: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 180 + 350).toLong())
        visible = true
    }

    val (accentColor, icon, typeLabel) = when (reward.type) {
        WorkoutRewardType.ACHIEVEMENT -> Triple(StarColor, Icons.Rounded.EmojiEvents,   "ACHIEVEMENT")
        WorkoutRewardType.CHALLENGE   -> Triple(AccentGreen,       Icons.Rounded.Bolt,                "CHALLENGE")
        WorkoutRewardType.RANK_UP     -> Triple(Color(0xFF8B5CF6), Icons.Rounded.AutoAwesome,   "RANK UP")
    }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(300)) + slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 2 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.08f))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.5.dp, accentColor.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }

            // Text
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    typeLabel,
                    style         = MaterialTheme.typography.labelSmall,
                    color         = accentColor.copy(alpha = 0.70f),
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    reward.title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color      = TextPrimary,
                )
                if (reward.subtitle.isNotBlank()) {
                    Text(
                        reward.subtitle,
                        style   = MaterialTheme.typography.labelSmall,
                        color   = TextSecondary,
                        maxLines = 2,
                    )
                }
            }

            // Stars earned
            if (reward.stars > 0L) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(14.dp))
                    Text(
                        "+${reward.stars}",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color      = StarColor,
                    )
                }
            }
        }
    }
}
