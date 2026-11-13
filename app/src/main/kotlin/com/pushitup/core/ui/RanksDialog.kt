package com.pushitup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pushitup.core.theme.*
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.RankTier

// Note: BackgroundDeep, SurfaceGlass, GlassBorder, TextPrimary, TextMuted,
// AccentGreen are all composable-accessible via com.pushitup.core.theme.*.

// ── Public helpers ────────────────────────────────────────────────────────────

/** Icon matched by BASE name — "Soldier II" splits to "Soldier" → Shield. */
fun rankIcon(name: String): ImageVector = when (name.split(" ").first()) {
    "Recruit"  -> Icons.Rounded.DirectionsRun
    "Soldier"  -> Icons.Rounded.Shield
    "Warrior"  -> Icons.Rounded.FitnessCenter
    "Champion" -> Icons.Rounded.AutoAwesome
    "Legend"   -> Icons.Rounded.Diamond
    "Immortal" -> Icons.Rounded.EmojiEvents
    else       -> Icons.Rounded.EmojiEvents
}

fun formatStars(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> String.format("%.1fK", n / 1_000f)
    else           -> n.toString()
}

// ── Dialog ────────────────────────────────────────────────────────────────────

@Composable
fun RanksDialog(
    currentTier : RankTier?,
    userStars   : Long,
    onDismiss   : () -> Unit,
) {
    // Capture theme colours in composable scope before entering non-composable lambdas.
    val bgTop    = SurfaceGlass
    val bgBottom = BackgroundDeep

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
                .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = StarColor, modifier = Modifier.size(22.dp))
                    Text(
                        "RANK SYSTEM",
                        style         = MaterialTheme.typography.titleMedium,
                        fontWeight    = FontWeight.Black,
                        color         = TextPrimary,
                        letterSpacing = 2.sp,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // ── Current-rank summary badge ────────────────────────────────────
            if (currentTier != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentTier.color.copy(alpha = 0.10f))
                        .border(1.dp, currentTier.color.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RankEmblem(tier = currentTier, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("YOUR RANK", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.5.sp)
                        Text(currentTier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = currentTier.color)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("STARS", style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(14.dp))
                            Text(formatStars(userStars), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorder.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            // ── Grouped tier cards ────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RankSystem.baseNames.forEach { baseName ->
                    val divisions       = RankSystem.divisionsOf(baseName)
                    val isGroupUnlocked = userStars >= divisions.first().minStars
                    val isGroupCurrent  = currentTier?.baseName == baseName
                    RankGroupCard(
                        baseName        = baseName,
                        divisions       = divisions,
                        groupMaxStars   = RankSystem.maxStarsFor(baseName),
                        userStars       = userStars,
                        isGroupCurrent  = isGroupCurrent,
                        isGroupUnlocked = isGroupUnlocked,
                        currentTier     = currentTier,
                    )
                }

                // Bottom note
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stars never reset — every rep you complete moves you up permanently.",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ── One rank-group card (e.g. "IRON" with I / II / III pips) ─────────────────

@Composable
private fun RankGroupCard(
    baseName        : String,
    divisions       : List<RankTier>,
    groupMaxStars   : Long?,          // inclusive upper bound (null = Elite, unbounded)
    userStars       : Long,
    isGroupCurrent  : Boolean,
    isGroupUnlocked : Boolean,
    currentTier     : RankTier?,
) {
    val mainColor   = divisions.first().color
    val bgBrush     = if (isGroupCurrent)
        Brush.horizontalGradient(listOf(mainColor.copy(alpha = 0.13f), mainColor.copy(alpha = 0.03f)))
    else
        Brush.horizontalGradient(listOf(SurfaceGlass, SurfaceGlass))
    val borderColor = when {
        isGroupCurrent  -> mainColor.copy(alpha = 0.70f)
        isGroupUnlocked -> mainColor.copy(alpha = 0.28f)
        else            -> GlassBorder.copy(alpha = 0.45f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isGroupUnlocked) 1f else 0.38f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {

        // ── Header row ────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RankEmblem(tier = divisions.first(), size = 44.dp)
                Column {
                    Text(
                        baseName.uppercase(),
                        style         = MaterialTheme.typography.titleSmall,
                        fontWeight    = FontWeight.Black,
                        color         = mainColor,
                        letterSpacing = 1.5.sp,
                    )
                    // Show the true inclusive range for the whole base-rank group.
                    // groupMaxStars == null means Elite (no upper cap).
                    val rangeText = if (groupMaxStars != null)
                        "${formatStars(divisions.first().minStars)} – ${formatStars(groupMaxStars)} ★"
                    else
                        "${formatStars(divisions.first().minStars)}+ ★"
                    Text(rangeText, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }

            when {
                !isGroupUnlocked -> Icon(Icons.Rounded.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                !isGroupCurrent && userStars >= divisions.last().minStars ->
                    Surface(shape = RoundedCornerShape(50), color = AccentGreen.copy(alpha = 0.15f)) {
                        Text("✓ DONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = AccentGreen, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
            }
        }

        // ── Division pips I / II / III ────────────────────────────────────────
        if (divisions.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                divisions.forEachIndexed { idx, div ->
                    // Each division's upper bound is the next division's minStars - 1,
                    // or groupMaxStars for the last division.
                    val divMax: Long? = if (idx < divisions.size - 1)
                        divisions[idx + 1].minStars - 1L
                    else
                        groupMaxStars
                    val divLabel = when (div.division) { 1 -> "I"; 2 -> "II"; else -> "III" }
                    DivisionPip(
                        label     = divLabel,
                        minStars  = div.minStars,
                        maxStars  = divMax,
                        color     = mainColor,
                        unlocked  = userStars >= div.minStars,
                        isCurrent = currentTier?.name == div.name,
                        modifier  = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Progress bar within current group ─────────────────────────────────
        if (isGroupCurrent && currentTier != null) {
            Spacer(Modifier.height(10.dp))
            val nextTier = RankSystem.nextTier(userStars)
            if (nextTier != null) {
                val progress = RankSystem.progressToNext(userStars)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(currentTier.name, style = MaterialTheme.typography.labelSmall, color = mainColor, fontWeight = FontWeight.Bold)
                    Text("${formatStars(userStars)} / ${formatStars(nextTier.minStars)} ★", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                Spacer(Modifier.height(4.dp))
                RankProgressBar(progress = progress, color = mainColor)
                Spacer(Modifier.height(2.dp))
                Text("Next: ${nextTier.name}", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            } else {
                Text("⚡  MAX RANK — LEGENDARY", style = MaterialTheme.typography.labelSmall, color = mainColor, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

// ── Division pip ──────────────────────────────────────────────────────────────

@Composable
private fun DivisionPip(
    label     : String,
    minStars  : Long,
    maxStars  : Long?,   // null = no upper cap (Elite)
    color     : Color,
    unlocked  : Boolean,
    isCurrent : Boolean,
    modifier  : Modifier = Modifier,
) {
    val bg        = when { isCurrent -> color.copy(alpha = 0.25f); unlocked -> color.copy(alpha = 0.10f); else -> Color.Transparent }
    val border    = when { isCurrent -> color; unlocked -> color.copy(alpha = 0.45f); else -> GlassBorder.copy(alpha = 0.5f) }
    val textColor = when { isCurrent -> color; unlocked -> color.copy(alpha = 0.80f); else -> TextMuted.copy(alpha = 0.45f) }

    // e.g. "I  0–16 ★"  or  "III  34–49 ★"
    val rangeLabel = if (maxStars != null)
        "${formatStars(minStars)}–${formatStars(maxStars)}"
    else
        "${formatStars(minStars)}+"

    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (unlocked && !isCurrent) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(10.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold, color = textColor)
            if (isCurrent) Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(8.dp))
        }
        Text(
            text  = "$rangeLabel ★",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = textColor.copy(alpha = if (isCurrent) 0.85f else 0.60f),
        )
    }
}

// ── Thin progress bar ─────────────────────────────────────────────────────────

@Composable
private fun RankProgressBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))),
        )
    }
}
