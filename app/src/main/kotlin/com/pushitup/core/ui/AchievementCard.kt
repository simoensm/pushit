package com.pushitup.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pushitup.core.theme.*
import com.pushitup.domain.model.Achievement
import com.pushitup.domain.model.AchievementCondition
import kotlin.math.cos
import kotlin.math.sin

// ── Rarity ───────────────────────────────────────────────────────────────────

enum class AchievementRarity {
    COMMON, RARE, EPIC, LEGENDARY;

    companion object {
        fun of(starReward: Int): AchievementRarity = when {
            starReward > 5_000 -> LEGENDARY
            starReward > 1_000 -> EPIC
            starReward > 200   -> RARE
            else               -> COMMON
        }
    }
}

private val AchievementRarity.frameColors: List<Color>
    get() = when (this) {
        AchievementRarity.COMMON    -> listOf(Color(0xFF6B7280), Color(0xFF9CA3AF), Color(0xFF6B7280))
        AchievementRarity.RARE      -> listOf(Color(0xFF047857), Color(0xFF34D399), Color(0xFF047857))
        AchievementRarity.EPIC      -> listOf(Color(0xFF7C3AED), Color(0xFFBB86FC), Color(0xFF7C3AED))
        AchievementRarity.LEGENDARY -> listOf(Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFD97706))
    }

private val AchievementRarity.artBgColors: List<Color>
    get() = when (this) {
        AchievementRarity.COMMON    -> listOf(Color(0xFF1F2937), Color(0xFF111827))
        AchievementRarity.RARE      -> listOf(Color(0xFF064E3B), Color(0xFF022C22))
        AchievementRarity.EPIC      -> listOf(Color(0xFF2D1B69), Color(0xFF1A0E3F))
        AchievementRarity.LEGENDARY -> listOf(Color(0xFF78350F), Color(0xFF3D1A00))
    }

/** Star-rarity indicator count (★ = COMMON … ★★★★ = LEGENDARY). */
private val AchievementRarity.starCount: Int
    get() = ordinal + 1

private val AchievementRarity.glowColor: Color
    get() = frameColors[1].copy(alpha = 0.55f)

private val AchievementRarity.label: String
    get() = when (this) {
        AchievementRarity.COMMON    -> "COMMON"
        AchievementRarity.RARE      -> "RARE"
        AchievementRarity.EPIC      -> "EPIC"
        AchievementRarity.LEGENDARY -> "LEGENDARY"
    }

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Clash Royale-style achievement card.
 *
 * Layout (portrait card):
 *  ┌──────────────────┐
 *  │  [rarity label]  │  ← thin coloured banner
 *  │                  │
 *  │   CANVAS ART     │  ← top 55 % — bespoke illustration per condition type
 *  │                  │
 *  ├──────────────────┤
 *  │  Title           │  ← bottom 45 % — dark ribbon
 *  │  ●●● gem reward  │
 *  │  [claim button]  │
 *  └──────────────────┘
 *
 * Bevel frame is drawn separately on top so it sits above the art.
 *
 * @param isUnlocked  False → greyscale art + lock overlay + hologram shimmer.
 * @param isClaimed   False (and unlocked) → pulsing gold border + "Claim" label.
 * @param progress    Optional 0f–1f progress toward unlock — locked cards show
 *                    a slim progress bar + percentage when provided.
 * @param onClaim     Called when the user taps the claim chip.
 * @param onClick     Called on generic tap (show detail dialog).
 */
@Composable
fun AchievementCard(
    achievement : Achievement,
    isUnlocked  : Boolean,
    isClaimed   : Boolean   = true,
    progress    : Float?    = null,
    onClaim     : (() -> Unit)? = null,
    onClick     : (() -> Unit)? = null,
    modifier    : Modifier  = Modifier,
) {
    val rarity     = AchievementRarity.of(achievement.starReward)
    val isClaiming = isUnlocked && !isClaimed

    // Pulsing glow animation for claimable state
    val pulse = rememberInfiniteTransition(label = "claim_pulse")
    val glowPulse by pulse.animateFloat(
        initialValue  = 0.40f,
        targetValue   = if (isClaiming) 0.95f else 0.40f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "glow_alpha",
    )

    // Continuous subtle glow on unlocked badges (1.5s cycle, brief spec)
    val ambient by pulse.animateFloat(
        initialValue  = 0.30f,
        targetValue   = if (isUnlocked && !isClaiming) 0.60f else 0.30f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "ambient_glow",
    )

    // Hologram shimmer sweep on locked badges
    val shimmer by pulse.animateFloat(
        initialValue  = -0.4f,
        targetValue   = 1.4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label         = "holo_shimmer",
    )

    val cardShape  = RoundedCornerShape(14.dp)
    val borderColor = when {
        isClaiming -> Color(0xFFFFCA28)
        else       -> rarity.frameColors[1].copy(alpha = if (isUnlocked) 0.80f else 0.30f)
    }

    Box(
        modifier = modifier
            .width(108.dp)
            .height(148.dp)
            .clip(cardShape)
            .clickable { onClick?.invoke() ?: onClaim?.invoke() }
            .alpha(if (isUnlocked) 1f else 0.45f)
            .border(
                width = if (isClaiming) 2.dp else 1.5.dp,
                brush = if (isClaiming)
                    Brush.verticalGradient(listOf(Color(0xFFFFCA28), Color(0xFFFBBF24), Color(0xFFFFCA28)))
                else
                    Brush.verticalGradient(rarity.frameColors),
                shape = cardShape,
            ),
    ) {
        // ── Art canvas (top 60 %) ─────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
        ) {
            // Background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (isUnlocked) rarity.artBgColors
                             else listOf(Color(0xFF1F2937), Color(0xFF111827)),
                ),
            )

            // Corner radiance glow
            if (isUnlocked) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(rarity.glowColor, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.6f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.5f, size.height * 0.6f),
                )
            }

            // Condition-specific illustration
            when (achievement.condition) {
                is AchievementCondition.TotalReps     -> drawFistArt(isUnlocked)
                is AchievementCondition.Streak        -> drawFlameArt(isUnlocked)
                is AchievementCondition.SingleSession -> drawLightningArt(isUnlocked)
                is AchievementCondition.FriendsCount  -> drawShieldsArt(isUnlocked)
            }

            // Locked overlay
            if (!isUnlocked) {
                drawRect(Color.Black.copy(alpha = 0.45f))
                // Hologram shimmer — diagonal light band sweeping across
                val bandX = size.width * shimmer
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF34D399).copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.10f),
                            Color(0xFF1DB954).copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        start = Offset(bandX - size.width * 0.35f, 0f),
                        end   = Offset(bandX + size.width * 0.35f, size.height),
                    ),
                )
                // Lock icon drawn manually (padlock body + shackle)
                val lx = size.width / 2f
                val ly = size.height / 2f
                val lw = size.width * 0.22f
                val lh = size.height * 0.18f
                // Shackle
                drawArc(
                    color     = Color.White.copy(alpha = 0.60f),
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter  = false,
                    topLeft   = Offset(lx - lw * 0.5f, ly - lh * 1.1f),
                    size      = Size(lw, lh),
                    style     = Stroke(width = size.width * 0.040f),
                )
                // Body
                drawRoundRect(
                    color        = Color.White.copy(alpha = 0.60f),
                    topLeft      = Offset(lx - lw * 0.62f, ly - lh * 0.30f),
                    size         = Size(lw * 1.24f, lh * 1.10f),
                    cornerRadius = CornerRadius(size.width * 0.04f),
                )
            }

            // Rarity label banner (top strip)
            val labelH = size.height * 0.18f
            drawRect(
                brush   = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, rarity.frameColors[1].copy(alpha = 0.45f), Color.Transparent)
                ),
                topLeft = Offset(0f, 0f),
                size    = Size(size.width, labelH),
            )
        }

        // ── Bottom ribbon ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0D0D0F), Color(0xFF181820))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Title
            Text(
                text       = achievement.title,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.40f),
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                fontSize   = 10.sp,
            )

            // Locked progress bar — "▓▓▓░░ 47%" per game brief
            if (!isUnlocked && progress != null) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1DB954), Color(0xFF34D399))
                                    )
                                ),
                        )
                    }
                    Text(
                        "${(progress * 100).toInt()}%",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = Color(0xFF34D399).copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 8.sp,
                    )
                }
            }

            // Gem reward row
            if (isUnlocked) {
                if (isClaiming) {
                    // Claimable chip
                    Row(
                        modifier              = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFCA28).copy(alpha = 0.18f * glowPulse))
                            .border(0.5.dp, Color(0xFFFFCA28).copy(alpha = glowPulse), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(8.dp))
                        Text(
                            "Claim +${achievement.starReward}",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = Color(0xFFFFCA28),
                            fontWeight = FontWeight.Black,
                            fontSize   = 8.sp,
                        )
                    }
                } else {
                    // Already claimed — muted gem count
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = StarColor.copy(alpha = 0.55f), modifier = Modifier.size(8.dp))
                        Text(
                            "+${achievement.starReward}",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = StarColor.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 9.sp,
                        )
                    }
                }
            }

            // Tag pill
            achievement.tag?.let { tag ->
                if (isUnlocked) {
                    val condColor = conditionColor(achievement.condition)
                    Text(
                        text       = tag,
                        modifier   = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(condColor.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = condColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 7.sp,
                        maxLines   = 1,
                    )
                }
            }
        }

        // ── Rarity text + star indicator in art area ──────────────────────────
        Column(
            modifier            = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isUnlocked) {
                Text(
                    text          = rarity.label,
                    style         = MaterialTheme.typography.labelSmall,
                    color         = rarity.frameColors[1],
                    fontWeight    = FontWeight.Black,
                    fontSize      = 7.sp,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text     = "★".repeat(rarity.starCount),
                style    = MaterialTheme.typography.labelSmall,
                color    = if (isUnlocked) rarity.frameColors[1].copy(alpha = 0.90f)
                           else Color.White.copy(alpha = 0.35f),
                fontSize = 7.sp,
            )
        }

        // ── Outer glow ring — strong pulse when claimable, subtle when unlocked ──
        if (isClaiming) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    color        = Color(0xFFFFCA28).copy(alpha = glowPulse * 0.25f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style        = Stroke(width = 6.dp.toPx()),
                )
            }
        } else if (isUnlocked) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    color        = rarity.glowColor.copy(alpha = ambient * 0.35f),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style        = Stroke(width = 4.dp.toPx()),
                )
            }
        }
    }
}

// ── Canvas art illustrations ──────────────────────────────────────────────────

/** Clenched fist with radiating energy rings — for TotalReps achievements. */
private fun DrawScope.drawFistArt(isUnlocked: Boolean) {
    val w  = size.width
    val h  = size.height
    val cx = w * 0.50f
    val cy = h * 0.55f
    val artColor = if (isUnlocked) Color(0xFF4ADE80) else Color(0xFF6B7280)

    // Energy rings (behind fist)
    for (i in 3 downTo 1) {
        val r     = w * (0.22f + i * 0.13f)
        val alpha = (0.30f - i * 0.07f).coerceAtLeast(0.04f)
        drawCircle(
            color  = artColor.copy(alpha = alpha),
            radius = r,
            center = Offset(cx, cy),
            style  = Stroke(width = w * 0.025f),
        )
    }

    // Palm (rounded rect)
    val pw = w * 0.34f
    val ph = h * 0.28f
    drawRoundRect(
        color        = artColor,
        topLeft      = Offset(cx - pw / 2f, cy - ph * 0.35f),
        size         = Size(pw, ph),
        cornerRadius = CornerRadius(pw * 0.22f),
    )
    // Finger bar across top of palm
    drawRoundRect(
        color        = artColor.copy(alpha = 0.85f),
        topLeft      = Offset(cx - pw * 0.52f, cy - ph * 0.52f),
        size         = Size(pw * 1.04f, ph * 0.28f),
        cornerRadius = CornerRadius(pw * 0.14f),
    )
    // Thumb nub on right side
    drawRoundRect(
        color        = artColor.copy(alpha = 0.80f),
        topLeft      = Offset(cx + pw * 0.38f, cy - ph * 0.18f),
        size         = Size(pw * 0.26f, ph * 0.38f),
        cornerRadius = CornerRadius(pw * 0.13f),
    )
    // Inner knuckle lines
    for (i in 0..2) {
        val lx = cx - pw * 0.26f + i * pw * 0.26f
        drawLine(
            color       = Color.Black.copy(alpha = 0.25f),
            start       = Offset(lx, cy - ph * 0.50f),
            end         = Offset(lx, cy - ph * 0.24f),
            strokeWidth = w * 0.018f,
        )
    }
    // Highlight on fist
    drawRoundRect(
        color        = Color.White.copy(alpha = 0.22f),
        topLeft      = Offset(cx - pw * 0.38f, cy - ph * 0.30f),
        size         = Size(pw * 0.30f, ph * 0.45f),
        cornerRadius = CornerRadius(pw * 0.12f),
    )
}

/** Stylised flame — for Streak achievements. */
private fun DrawScope.drawFlameArt(isUnlocked: Boolean) {
    val w  = size.width
    val h  = size.height
    val cx = w * 0.50f
    val by = h * 0.88f  // flame base y

    if (!isUnlocked) {
        drawFlameLayer(cx, by, w * 0.34f, h * 0.60f, Color(0xFF6B7280).copy(alpha = 0.60f))
        return
    }

    // Outer flame — deep orange
    drawFlameLayer(cx, by, w * 0.36f, h * 0.64f, Color(0xFFEA580C).copy(alpha = 0.90f))
    // Mid flame — orange
    drawFlameLayer(cx, by, w * 0.26f, h * 0.50f, Color(0xFFFB923C).copy(alpha = 0.95f))
    // Inner flame — yellow
    drawFlameLayer(cx, by, w * 0.18f, h * 0.38f, Color(0xFFFBBF24))
    // Core — white-yellow
    drawFlameLayer(cx, by, w * 0.10f, h * 0.24f, Color.White.copy(alpha = 0.80f))

    // Base streaks
    for (i in -1..1) {
        drawLine(
            color       = Color(0xFFEA580C).copy(alpha = 0.45f),
            start       = Offset(cx + i * w * 0.12f, by),
            end         = Offset(cx + i * w * 0.08f, by + h * 0.06f),
            strokeWidth = w * 0.022f,
        )
    }
}

private fun DrawScope.drawFlameLayer(cx: Float, by: Float, rw: Float, rh: Float, color: Color) {
    val path = Path().apply {
        // Base left
        moveTo(cx - rw, by)
        // Left curve up
        cubicTo(
            cx - rw * 1.1f, by - rh * 0.40f,
            cx - rw * 0.30f, by - rh * 0.60f,
            cx, by - rh,
        )
        // Right curve down
        cubicTo(
            cx + rw * 0.30f, by - rh * 0.60f,
            cx + rw * 1.1f, by - rh * 0.40f,
            cx + rw, by,
        )
        close()
    }
    drawPath(path, color = color)
}

/** Zigzag lightning bolt with electric glow — for SingleSession achievements. */
private fun DrawScope.drawLightningArt(isUnlocked: Boolean) {
    val w  = size.width
    val h  = size.height
    val artColor = if (isUnlocked) Color(0xFFFBBF24) else Color(0xFF6B7280)

    // Glow passes (behind bolt)
    if (isUnlocked) {
        repeat(3) { i ->
            drawLightningBolt(w, h, artColor.copy(alpha = 0.12f + i * 0.06f), strokeWidth = w * (0.18f - i * 0.04f))
        }
    }

    // Main bolt (filled)
    val boltPath = makeLightningPath(w, h)
    drawPath(boltPath, color = artColor)

    // Inner highlight
    drawLightningBolt(w, h, Color.White.copy(alpha = if (isUnlocked) 0.40f else 0.15f), strokeWidth = w * 0.04f)

    // Spark dots at top and bottom
    if (isUnlocked) {
        for (i in 0..2) {
            val angle = (i * 120f) * (Math.PI / 180f).toFloat()
            val sr    = w * 0.08f
            drawCircle(
                color  = Color.White.copy(alpha = 0.55f),
                radius = w * 0.018f,
                center = Offset(w * 0.50f + cos(angle) * sr, h * 0.15f + sin(angle) * sr),
            )
        }
    }
}

private fun makeLightningPath(w: Float, h: Float): Path {
    // Classic chevron bolt, slightly right-leaning
    val x0 = w * 0.57f; val y0 = h * 0.08f  // top-right
    val x1 = w * 0.38f; val y1 = h * 0.08f  // top-left
    val x2 = w * 0.52f; val y2 = h * 0.46f  // mid-right
    val x3 = w * 0.35f; val y3 = h * 0.46f  // mid-left
    val x4 = w * 0.43f; val y4 = h * 0.92f  // bottom
    return Path().apply {
        moveTo(x1, y1)
        lineTo(x0, y0)
        lineTo(x2, y2)
        lineTo(w * 0.63f, y2)  // mid right wing
        lineTo(x4, y4)
        lineTo(x3, y3)
        lineTo(w * 0.27f, y3)  // mid left wing
        close()
    }
}

private fun DrawScope.drawLightningBolt(w: Float, h: Float, color: Color, strokeWidth: Float) {
    drawPath(makeLightningPath(w, h), color = color, style = Stroke(width = strokeWidth))
}

/** Two overlapping warrior shields — for FriendsCount achievements. */
private fun DrawScope.drawShieldsArt(isUnlocked: Boolean) {
    val w  = size.width
    val h  = size.height
    val artColor = if (isUnlocked) Color(0xFF34D399) else Color(0xFF6B7280)
    val darkColor = if (isUnlocked) Color(0xFF047857) else Color(0xFF374151)

    // Left shield (back layer)
    drawShield(
        cx     = w * 0.38f, cy = h * 0.50f,
        sw     = w * 0.38f, sh = h * 0.56f,
        fill   = darkColor.copy(alpha = 0.80f),
        border = artColor.copy(alpha = 0.70f),
        bw     = w * 0.025f,
    )
    // Right shield (front layer)
    drawShield(
        cx     = w * 0.62f, cy = h * 0.50f,
        sw     = w * 0.38f, sh = h * 0.56f,
        fill   = artColor.copy(alpha = 0.85f),
        border = Color.White.copy(alpha = 0.50f),
        bw     = w * 0.022f,
    )
    // Shine on right shield
    if (isUnlocked) {
        drawShield(
            cx     = w * 0.58f, cy = h * 0.36f,
            sw     = w * 0.14f, sh = h * 0.22f,
            fill   = Color.White.copy(alpha = 0.22f),
            border = Color.Transparent,
            bw     = 0f,
        )
    }
}

private fun DrawScope.drawShield(
    cx: Float, cy: Float,
    sw: Float, sh: Float,
    fill: Color, border: Color, bw: Float,
) {
    // Classic heraldic shield: flat top, straight sides, curved to a bottom point
    val path = Path().apply {
        val left   = cx - sw / 2f
        val right  = cx + sw / 2f
        val top    = cy - sh / 2f
        val bottom = cy + sh / 2f
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, cy + sh * 0.08f)
        cubicTo(right, cy + sh * 0.38f, cx + sw * 0.12f, bottom, cx, bottom)
        cubicTo(cx - sw * 0.12f, bottom, left, cy + sh * 0.38f, left, cy + sh * 0.08f)
        close()
    }
    drawPath(path, color = fill)
    if (bw > 0f) drawPath(path, color = border, style = Stroke(width = bw))
}

// ── Colour helper (mirrors ProfileScreen) ─────────────────────────────────────

internal fun conditionColor(condition: AchievementCondition): Color = when (condition) {
    is AchievementCondition.TotalReps     -> Color(0xFF4ADE80)
    is AchievementCondition.Streak        -> Color(0xFFF87171)
    is AchievementCondition.SingleSession -> Color(0xFFFBBF24)
    is AchievementCondition.FriendsCount  -> Color(0xFF34D399)
}
