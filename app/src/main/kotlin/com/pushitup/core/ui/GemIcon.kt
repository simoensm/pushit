package com.pushitup.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canvas-drawn faceted gem / crystal that replaces the star icon throughout the app.
 *
 * Shape: classic 6-point brilliant-cut gem profile —
 *   flat table on top, wide crown, narrow pavilion converging to a culet point.
 * Facets are shaded with white/black overlays to simulate a single top-left light source,
 * giving the gem a polished 3-D look without any external assets.
 *
 * @param color   Primary gem colour (defaults to [GemColorDefault]).
 * @param size    Bounding square size.
 * @param glow    Draw a soft radial glow behind the gem (good for highlighted states).
 */
@Composable
fun GemIcon(
    modifier : Modifier = Modifier,
    color    : Color    = GemColorDefault,
    size     : Dp       = 20.dp,
    glow     : Boolean  = false,
) {
    Canvas(modifier = modifier.size(size)) {
        drawGem(color = color, glowEnabled = glow)
    }
}

// ── Default gem colour (cyan-teal crystal) ────────────────────────────────────

val GemColorDefault = Color(0xFF00D4E8)

// ── Core draw function (reusable in larger Canvas blocks) ─────────────────────

fun DrawScope.drawGem(
    color       : Color,
    glowEnabled : Boolean = false,
    offsetX     : Float   = 0f,
    offsetY     : Float   = 0f,
    scale       : Float   = 1f,
) {
    val s  = size.minDimension * scale
    val cx = center.x + offsetX
    val cy = center.y + offsetY

    // ── Key control points ────────────────────────────────────────────────────
    //  tl ─────── tr      top edge of crown (table)
    //   \    F1   /
    //   lc ─── rc         inner crown point
    //  /  \  F6 /  \
    // lg  F2|F3   rg      girdle (widest)
    //  \  F4|F5  /
    //   \   |   /
    //    \  |  /
    //     \ | /
    //      cu             culet (bottom point)

    val tl = Offset(cx - s * 0.32f, cy - s * 0.46f)   // top-left table
    val tr = Offset(cx + s * 0.32f, cy - s * 0.46f)   // top-right table
    val lc = Offset(cx - s * 0.10f, cy - s * 0.20f)   // inner crown left
    val rc = Offset(cx + s * 0.10f, cy - s * 0.20f)   // inner crown right
    val lg = Offset(cx - s * 0.48f, cy + s * 0.04f)   // left girdle
    val rg = Offset(cx + s * 0.48f, cy + s * 0.04f)   // right girdle
    val cu = Offset(cx,             cy + s * 0.50f)   // culet

    // Build paths without vararg (Offset is an inline class — vararg prohibited)
    fun tri(a: Offset, b: Offset, c: Offset) = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
    }
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }

    val cm = Offset(cx, cy + s * 0.12f)   // centre-bottom pavilion divide point

    // ── Optional glow ─────────────────────────────────────────────────────────
    if (glowEnabled) {
        drawCircle(
            brush  = Brush.radialGradient(
                colors  = listOf(color.copy(alpha = 0.40f), Color.Transparent),
                center  = Offset(cx, cy),
                radius  = s * 0.75f,
            ),
            radius = s * 0.75f,
            center = Offset(cx, cy),
        )
    }

    // ── F1 — Table (top face): brightest ──────────────────────────────────────
    drawPath(quad(tl, tr, rc, lc), color = color)
    drawPath(quad(tl, tr, rc, lc), color = Color.White.copy(alpha = 0.42f))

    // ── F2 — Left crown: light (lit side) ────────────────────────────────────
    drawPath(tri(tl, lc, lg), color = color.copy(alpha = 0.90f))
    drawPath(tri(tl, lc, lg), color = Color.White.copy(alpha = 0.22f))

    // ── F3 — Right crown: slightly darker ────────────────────────────────────
    drawPath(tri(tr, rc, rg), color = color.copy(alpha = 0.88f))
    drawPath(tri(tr, rc, rg), color = Color.Black.copy(alpha = 0.08f))

    // ── F6 — Centre body (between crown points and girdle) ────────────────────
    drawPath(quad(lc, rc, rg, lg), color = color.copy(alpha = 0.85f))

    // ── F4 — Left pavilion: darker ────────────────────────────────────────────
    drawPath(tri(lg, cm, cu), color = color.copy(alpha = 0.72f))
    drawPath(tri(lg, cm, cu), color = Color.Black.copy(alpha = 0.14f))

    // ── F5 — Right pavilion: darkest ─────────────────────────────────────────
    drawPath(tri(rg, cm, cu), color = color.copy(alpha = 0.65f))
    drawPath(tri(rg, cm, cu), color = Color.Black.copy(alpha = 0.22f))

    // ── Sparkle highlight (top-left corner of table) ──────────────────────────
    val hlPath = Path().apply {
        moveTo(tl.x + s * 0.04f, tl.y + s * 0.04f)
        lineTo(tl.x + s * 0.19f, tl.y + s * 0.04f)
        lineTo(tl.x + s * 0.04f, tl.y + s * 0.16f)
        close()
    }
    drawPath(hlPath, color = Color.White.copy(alpha = 0.80f))
}
