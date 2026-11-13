package com.pushitup.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.RankTier
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas-drawn rank emblem badge — unique per tier.
 *
 * Each tier has its own symbolic motif:
 *  • Recruit  — plain circular shield (starter)
 *  • Soldier  — hexagonal military crest
 *  • Warrior  — angular diamond / anvil shape
 *  • Champion — six-pointed star / sunburst (gold)
 *  • Legend   — multi-layered crown
 *  • Immortal — flaming orb
 *
 * Division (I / II / III) is shown as small pips at the bottom of the emblem.
 *
 * @param stars Current star count — used to derive the correct tier + division.
 * @param size  Bounding square in dp.
 */
@Composable
fun RankEmblem(
    stars    : Long,
    modifier : Modifier = Modifier,
    size     : Dp       = 64.dp,
) {
    val tier = RankSystem.tierFor(stars)
    Canvas(modifier = modifier.size(size)) {
        drawRankEmblem(tier)
    }
}

/** Overload that accepts an explicit [RankTier] (e.g. for the ranks dialog). */
@Composable
fun RankEmblem(
    tier     : RankTier,
    modifier : Modifier = Modifier,
    size     : Dp       = 64.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawRankEmblem(tier)
    }
}

// ── Core draw ─────────────────────────────────────────────────────────────────

fun DrawScope.drawRankEmblem(tier: RankTier) {
    val s  = min(size.width, size.height)
    val cx = size.width  / 2f
    val cy = size.height / 2f
    val c  = tier.color

    when (tier.baseName) {
        "Recruit"  -> drawRecruitEmblem(cx, cy, s, c)
        "Soldier"  -> drawSoldierEmblem(cx, cy, s, c)
        "Warrior"  -> drawWarriorEmblem(cx, cy, s, c)
        "Champion" -> drawChampionEmblem(cx, cy, s, c)
        "Legend"   -> drawLegendEmblem(cx, cy, s, c)
        "Immortal" -> drawImmortalEmblem(cx, cy, s, c)
        else       -> drawRecruitEmblem(cx, cy, s, c)
    }

    // Division pips (I = 1 pip, II = 2, III = 3; null = no pips for Immortal)
    tier.division?.let { div ->
        val pipR   = s * 0.050f
        val pipY   = cy + s * 0.38f
        val spacing = pipR * 2.8f
        val totalW  = spacing * (div - 1)
        val startX  = cx - totalW / 2f
        repeat(div) { i ->
            drawCircle(
                color  = c,
                radius = pipR,
                center = Offset(startX + i * spacing, pipY),
            )
            drawCircle(
                color  = Color.White.copy(alpha = 0.35f),
                radius = pipR * 0.50f,
                center = Offset(startX + i * spacing - pipR * 0.20f, pipY - pipR * 0.20f),
            )
        }
    }
}

// ── Recruit: circular shield with star notch ──────────────────────────────────

private fun DrawScope.drawRecruitEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    val r = s * 0.36f
    // Outer ring
    drawCircle(color = c.copy(alpha = 0.25f), radius = r + s * 0.04f, center = Offset(cx, cy))
    drawCircle(color = c, radius = r, center = Offset(cx, cy), style = Stroke(width = s * 0.055f))
    // Inner "R" letter stand-in — simple concentric inner ring
    drawCircle(color = c.copy(alpha = 0.40f), radius = r * 0.55f, center = Offset(cx, cy))
    // Single gem
    val gemS = s * 0.28f
    drawGem(color = c, offsetX = 0f, offsetY = 0f, scale = 0.28f)
}

// ── Warrior: anvil-like diamond ───────────────────────────────────────────────

private fun DrawScope.drawWarriorEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    // Wide flat diamond
    val hw = s * 0.38f
    val hh = s * 0.32f
    val path = Path().apply {
        moveTo(cx, cy - hh)
        lineTo(cx + hw, cy)
        lineTo(cx, cy + hh)
        lineTo(cx - hw, cy)
        close()
    }
    drawPath(path, brush = Brush.verticalGradient(
        colors  = listOf(c.copy(alpha = 0.55f), c),
        startY  = cy - hh, endY = cy + hh,
    ))
    drawPath(path, color = c, style = Stroke(width = s * 0.045f))

    // Inner smaller diamond
    val path2 = Path().apply {
        moveTo(cx, cy - hh * 0.50f)
        lineTo(cx + hw * 0.50f, cy)
        lineTo(cx, cy + hh * 0.50f)
        lineTo(cx - hw * 0.50f, cy)
        close()
    }
    drawPath(path2, color = Color.White.copy(alpha = 0.20f))

    // Top highlight facet
    drawLine(
        color       = Color.White.copy(alpha = 0.50f),
        start       = Offset(cx, cy - hh),
        end         = Offset(cx + hw * 0.50f, cy),
        strokeWidth = s * 0.030f,
    )
}

// ── Soldier: hexagonal military crest ─────────────────────────────────────────

private fun DrawScope.drawSoldierEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    fun hexPath(r: Float, rotation: Float = 0f) = Path().apply {
        for (i in 0..5) {
            val angle = Math.toRadians((60.0 * i + rotation).toDouble()).toFloat()
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    val r1 = s * 0.38f
    val r2 = s * 0.26f
    val r3 = s * 0.14f

    // Fill outer hex
    drawPath(hexPath(r1, 0f), brush = Brush.radialGradient(
        colors  = listOf(c.copy(alpha = 0.50f), c.copy(alpha = 0.15f)),
        center  = Offset(cx, cy), radius = r1,
    ))
    drawPath(hexPath(r1, 0f), color = c, style = Stroke(width = s * 0.048f))
    // Middle ring
    drawPath(hexPath(r2, 30f), color = c.copy(alpha = 0.55f), style = Stroke(width = s * 0.032f))
    // Inner solid hex
    drawPath(hexPath(r3, 0f), color = c.copy(alpha = 0.80f))
    // Highlight
    drawLine(
        color       = Color.White.copy(alpha = 0.45f),
        start       = Offset(cx - r1 * 0.30f, cy - r1 * 0.60f),
        end         = Offset(cx + r1 * 0.12f, cy - r1 * 0.38f),
        strokeWidth = s * 0.030f,
    )
}

// ── Champion: six-pointed star / sunburst ─────────────────────────────────────

private fun DrawScope.drawChampionEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    val outerR = s * 0.40f
    val innerR = s * 0.20f
    val points = 6

    // Sunburst rays (behind)
    for (i in 0 until 12) {
        val angle = Math.toRadians((i * 30.0)).toFloat()
        drawLine(
            color       = c.copy(alpha = 0.25f),
            start       = Offset(cx + cos(angle) * innerR * 0.8f, cy + sin(angle) * innerR * 0.8f),
            end         = Offset(cx + cos(angle) * outerR * 1.15f, cy + sin(angle) * outerR * 1.15f),
            strokeWidth = s * 0.022f,
        )
    }

    // Star polygon
    val starPath = Path().apply {
        for (i in 0 until points * 2) {
            val angle  = Math.toRadians((i * 360.0 / (points * 2) - 90.0)).toFloat()
            val r      = if (i % 2 == 0) outerR else innerR
            val x      = cx + r * cos(angle)
            val y      = cy + r * sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(starPath, brush = Brush.radialGradient(
        colors = listOf(c, c.copy(alpha = 0.55f)),
        center = Offset(cx, cy), radius = outerR,
    ))
    drawPath(starPath, color = Color.White.copy(alpha = 0.25f), style = Stroke(width = s * 0.030f))

    // Center gem
    drawGem(color = Color.White.copy(alpha = 0.85f), scale = 0.22f)
}

// ── Legend: layered crown ─────────────────────────────────────────────────────

private fun DrawScope.drawLegendEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    val cw  = s * 0.70f   // crown width
    val ch  = s * 0.46f   // crown height
    val left  = cx - cw / 2f
    val right = cx + cw / 2f
    val base  = cy + ch * 0.30f
    val top   = cy - ch * 0.55f

    // Crown fill path: base rectangle + 5 points on top
    val crownPath = Path().apply {
        moveTo(left, base)
        lineTo(left, top + ch * 0.46f)         // left side up
        lineTo(cx - cw * 0.25f, top + ch * 0.10f) // left inner notch
        lineTo(cx, top)                          // centre peak
        lineTo(cx + cw * 0.25f, top + ch * 0.10f) // right inner notch
        lineTo(right, top + ch * 0.46f)         // right side up
        lineTo(right, base)
        // Notched base
        lineTo(right - cw * 0.10f, base + ch * 0.16f)
        lineTo(left  + cw * 0.10f, base + ch * 0.16f)
        close()
    }
    drawPath(crownPath, brush = Brush.verticalGradient(
        colors = listOf(c, c.copy(alpha = 0.55f)),
        startY = top, endY = base + ch * 0.16f,
    ))
    drawPath(crownPath, color = Color.White.copy(alpha = 0.25f), style = Stroke(width = s * 0.032f))

    // Three gem dots in crown
    val gemCenters = listOf(
        Offset(cx - cw * 0.22f, base - ch * 0.12f),
        Offset(cx, top + ch * 0.35f),
        Offset(cx + cw * 0.22f, base - ch * 0.12f),
    )
    gemCenters.forEach { pt ->
        drawCircle(color = Color.White.copy(alpha = 0.80f), radius = s * 0.040f, center = pt)
        drawCircle(color = c, radius = s * 0.022f, center = pt)
    }

    // Top gem
    drawGem(color = Color.White.copy(alpha = 0.90f), offsetY = -(s * 0.22f), scale = 0.18f)
}

// ── Immortal: flaming orb ─────────────────────────────────────────────────────

private fun DrawScope.drawImmortalEmblem(cx: Float, cy: Float, s: Float, c: Color) {
    val orbR = s * 0.28f

    // Outer glow rings
    for (i in 3 downTo 1) {
        drawCircle(
            color  = c.copy(alpha = 0.10f + i * 0.06f),
            radius = orbR + s * i * 0.065f,
            center = Offset(cx, cy),
        )
    }

    // Flame rays (spiky halo)
    for (i in 0 until 8) {
        val angle  = Math.toRadians((i * 45.0 - 22.5)).toFloat()
        val inner  = orbR * 1.05f
        val outer  = orbR * 1.55f + s * 0.04f * (if (i % 2 == 0) 1f else 0.60f)
        drawLine(
            color       = c.copy(alpha = 0.70f),
            start       = Offset(cx + cos(angle) * inner, cy + sin(angle) * inner),
            end         = Offset(cx + cos(angle) * outer, cy + sin(angle) * outer),
            strokeWidth = s * 0.038f,
        )
    }

    // Orb
    drawCircle(
        brush  = Brush.radialGradient(
            colors  = listOf(Color.White.copy(alpha = 0.70f), c, c.copy(alpha = 0.60f)),
            center  = Offset(cx - orbR * 0.20f, cy - orbR * 0.20f),
            radius  = orbR,
        ),
        radius = orbR,
        center = Offset(cx, cy),
    )

    // Inner dark core
    drawCircle(
        color  = Color.Black.copy(alpha = 0.25f),
        radius = orbR * 0.40f,
        center = Offset(cx, cy),
    )

    // "P" silhouette — small gem symbol
    drawGem(color = Color.White.copy(alpha = 0.90f), scale = 0.20f)
}
