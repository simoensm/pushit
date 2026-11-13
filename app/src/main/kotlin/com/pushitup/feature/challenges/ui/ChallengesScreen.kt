package com.pushitup.feature.challenges.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pushitup.R
import com.pushitup.core.theme.*
import com.pushitup.core.ui.glassCard
import com.pushitup.domain.model.*

@Composable
fun ChallengesScreen(viewModel: ChallengesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.toast) {
        if (uiState.toast != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDeep)) {

        LazyColumn(
            modifier            = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        stringResource(R.string.challenges_title),
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color      = TextPrimary,
                    )
                    Text(
                        stringResource(R.string.challenges_activate_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            item { DifficultyFilterRow(uiState, viewModel) }

            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGreen)
                    }
                }
            } else {
                items(uiState.filtered, key = { it.challenge.id }) { cws ->
                    ChallengeCard(
                        cws        = cws,
                        userStars  = uiState.userStars,
                        onActivate = { viewModel.activateChallenge(cws.challenge) },
                        onAbandon  = { viewModel.abandonChallenge(cws.challenge) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Toast
        AnimatedVisibility(
            visible  = uiState.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = AccentGreen) {
                Text(
                    uiState.toast ?: "",
                    color      = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ── Difficulty filter ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultyFilterRow(state: ChallengesUiState, viewModel: ChallengesViewModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding        = PaddingValues(horizontal = 4.dp),
    ) {
        item {
            FilterChip(
                selected = state.difficultyFilter == null,
                onClick  = { viewModel.setFilter(null) },
                label    = { Text("All", fontWeight = FontWeight.SemiBold) },
                colors   = chipColors(),
            )
        }
        items(Difficulty.entries) { diff ->
            FilterChip(
                selected = state.difficultyFilter == diff,
                onClick  = { viewModel.setFilter(diff) },
                label    = { Text(diff.displayName(), fontWeight = FontWeight.SemiBold) },
                colors   = chipColors(diff),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chipColors(diff: Difficulty? = null) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = if (diff == null) AccentGreen else diff.color(),
    selectedLabelColor     = Color.Black,
    containerColor         = SurfaceGlass,
    labelColor             = TextSecondary,
)

// ── Challenge card ────────────────────────────────────────────────────────────

@Composable
private fun ChallengeCard(
    cws        : ChallengeWithState,
    userStars  : Long,
    onActivate : () -> Unit,
    onAbandon  : () -> Unit,
) {
    val c           = cws.challenge
    val uc          = cws.userChallenge
    val isActive    = uc != null && !uc.completed && !cws.isExpired
    val isCompleted = uc?.completed == true
    val canAfford   = userStars >= c.starCost

    var showAbandon  by remember { mutableStateOf(false) }
    var showConfirm  by remember { mutableStateOf(false) }

    val animProgress by animateFloatAsState(
        targetValue = cws.progress, label = "prog_${c.id}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header row: icon + title + difficulty badge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChallengeIcon(c.type, c.difficulty)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    c.title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    c.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DifficultyBadge(c.difficulty)
        }

        // Progress (only when active or completed)
        if (isActive || isCompleted) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress   = { animProgress },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (isCompleted) AccentGreen else c.difficulty.color(),
                    trackColor = TextMuted.copy(alpha = 0.18f),
                    strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Text(
                    cws.progressLabel,
                    style  = MaterialTheme.typography.labelSmall,
                    color  = TextMuted,
                )
            }
        }

        // Footer: cost → reward + duration + action
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Entry cost (shown only when not yet active/completed)
                if (!isActive && !isCompleted && c.starCost > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Rounded.Star, null,
                            tint     = if (canAfford) ErrorRed.copy(alpha = 0.85f) else ErrorRed,
                            modifier = Modifier.size(12.dp))
                        Text(
                            "−${c.starCost} entry",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = if (canAfford) ErrorRed.copy(alpha = 0.85f) else ErrorRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // Reward
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(13.dp))
                    Text(
                        "+${c.starReward}",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = StarColor,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isActive && !isCompleted && c.starCost > 0) {
                        Text(
                            "net +${c.starReward - c.starCost}",
                            style  = MaterialTheme.typography.labelSmall,
                            color  = TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                "${c.durationDays}d",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )

            Spacer(Modifier.weight(1f))

            // Action button
            when {
                isCompleted   -> CompletedBadge()
                cws.isExpired -> ExpiredRow(onRetry = { showConfirm = true })
                isActive      -> ActiveButton(onAbandon = { showAbandon = true })
                else          -> StartButton(
                    onClick   = { showConfirm = true },
                    canAfford = canAfford,
                    starCost  = c.starCost,
                )
            }
        }
    }

    if (showAbandon) {
        AlertDialog(
            onDismissRequest = { showAbandon = false },
            containerColor   = SurfaceGlass,
            title = { Text("Abandon challenge?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("Your progress on \"${c.title}\" will be lost.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onAbandon(); showAbandon = false }) {
                    Text("Abandon", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandon = false }) {
                    Text("Keep going", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // ── Entry-fee confirmation dialog ─────────────────────────────────────────
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = SurfaceGlass,
            icon             = {
                Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    text       = if (c.starCost > 0) "Pay entry fee?" else "Start challenge?",
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (c.starCost > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "\"${c.title}\" costs ★${c.starCost} to enter.",
                            color = TextSecondary,
                        )
                        Text(
                            "Complete it to win ★${c.starReward} (net +${c.starReward - c.starCost} ★).",
                            color = TextSecondary,
                        )
                        Text(
                            "Fail or abandon and your ${c.starCost} ★ are gone.",
                            color = ErrorRed.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Text("Ready to start \"${c.title}\"?", color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { onActivate(); showConfirm = false }) {
                    Text(
                        if (c.starCost > 0) "Pay & Enter (★${c.starCost})" else "Let's go!",
                        color      = if (c.starCost > 0) Color(0xFFFF6B35) else AccentGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

// ── Challenge icon illustration ───────────────────────────────────────────────

@Composable
private fun ChallengeIcon(type: ChallengeType, difficulty: Difficulty) {
    val icon: ImageVector = when (type) {
        ChallengeType.REPS_TOTAL      -> Icons.Rounded.FitnessCenter
        ChallengeType.STREAK          -> Icons.Rounded.Whatshot
        ChallengeType.DAILY_REPS      -> Icons.Rounded.Repeat
        ChallengeType.SINGLE_SESSION  -> Icons.Rounded.FlashOn
    }
    Box(
        modifier         = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(difficulty.color().copy(alpha = 0.12f))
            .border(1.dp, difficulty.color().copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = difficulty.color(), modifier = Modifier.size(24.dp))
    }
}

// ── Difficulty badge ──────────────────────────────────────────────────────────

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    Surface(
        shape = RoundedCornerShape(50),
        color = difficulty.color().copy(alpha = 0.15f),
    ) {
        Text(
            difficulty.displayName(),
            color      = difficulty.color(),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun StartButton(
    onClick   : () -> Unit,
    canAfford : Boolean = true,
    starCost  : Int     = 0,
) {
    Button(
        onClick        = onClick,
        enabled        = canAfford,
        colors         = ButtonDefaults.buttonColors(
            containerColor         = if (starCost > 0) Color(0xFFFF6B35) else AccentGreen,
            disabledContainerColor = ErrorRed.copy(alpha = 0.30f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape          = RoundedCornerShape(50),
    ) {
        if (starCost > 0 && !canAfford) {
            Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Can't afford", color = TextMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        } else {
            Text("Enter", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ActiveButton(onAbandon: () -> Unit) {
    OutlinedButton(
        onClick       = onAbandon,
        border        = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        shape         = RoundedCornerShape(50),
    ) {
        Icon(Icons.Rounded.RadioButtonChecked, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Active", color = AccentGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CompletedBadge() {
    Surface(shape = RoundedCornerShape(50), color = AccentGreen.copy(alpha = 0.15f)) {
        Row(
            modifier       = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Check, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            Text("Done", color = AccentGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExpiredRow(onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = ErrorRed.copy(alpha = 0.12f)) {
            Text(
                "Expired",
                color    = ErrorRed,
                style    = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Button(
            onClick        = onRetry,
            colors         = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape          = RoundedCornerShape(50),
        ) {
            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Difficulty.displayName() = when (this) {
    Difficulty.AMATEUR  -> "Amateur"
    Difficulty.MEDIUM   -> "Medium"
    Difficulty.ADVANCED -> "Advanced"
}

private fun Difficulty.color() = when (this) {
    Difficulty.AMATEUR  -> Color(0xFF4CAF50)
    Difficulty.MEDIUM   -> Color(0xFFFFC107)
    Difficulty.ADVANCED -> Color(0xFFF44336)
}
