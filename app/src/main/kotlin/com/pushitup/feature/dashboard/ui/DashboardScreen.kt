package com.pushitup.feature.dashboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pushitup.R
import com.pushitup.core.onboarding.OnboardingViewModel
import com.pushitup.core.onboarding.TourCoordinates
import com.pushitup.core.onboarding.TourTarget
import com.pushitup.core.theme.*
import com.pushitup.core.ui.AnimatedCounter
import com.pushitup.core.ui.NeonCtaButton
import com.pushitup.core.ui.RankEmblem
import com.pushitup.core.ui.RanksDialog
import com.pushitup.core.ui.formatStars
import com.pushitup.core.ui.glassCard
import com.pushitup.core.ui.neonGlassCard
import com.pushitup.domain.model.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    onStartWorkout    : () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel         : DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val tourVm: OnboardingViewModel = hiltViewModel()
    val tourComplete by tourVm.isComplete.collectAsState()
    // Lock scroll while tour is active so spotlight coords stay valid
    val scrollEnabled = tourComplete != false

    var showStarsDialog by remember { mutableStateOf(false) }
    var showRanksDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState(), enabled = scrollEnabled),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Logo
        Image(
            painter            = painterResource(R.drawable.logo_pushit_bg),
            contentDescription = "PushIt",
            modifier           = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .height(72.dp),
            contentScale       = androidx.compose.ui.layout.ContentScale.Fit,
        )

        Header(state, onStarsTap = { showStarsDialog = true })

        Column(
            modifier            = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // Register RankHeroCard position for tour spotlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { TourCoordinates.register(TourTarget.RANK_HERO_CARD, it) },
            ) {
                state.tier?.let { tier ->
                    RankHeroCard(
                        tier         = tier,
                        nextTier     = state.nextTier,
                        stars        = state.stars,
                        rankProgress = state.rankProgress,
                        onClick      = { showRanksDialog = true },
                    )
                }
            }

            // Register Ring+Streak row position for tour spotlight
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { TourCoordinates.register(TourTarget.RING_AND_STREAK, it) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DailyRingCard(
                    reps     = state.todayReps,
                    goal     = state.dailyGoal,
                    modifier = Modifier.weight(1.1f),
                    onClick  = onNavigateToProfile,
                )
                StreakCard(
                    streak   = state.currentStreak,
                    modifier = Modifier.weight(0.9f),
                    onClick  = onNavigateToProfile,
                )
            }

            // Register Start Workout button position for tour spotlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { TourCoordinates.register(TourTarget.START_WORKOUT_BTN, it) },
            ) {
                StartWorkoutButton(onClick = onStartWorkout)
            }

            if (state.activeChallenges.isNotEmpty()) {
                ActiveChallengesSection(state.activeChallenges)
            }

            WeeklyChart(logs = state.weeklyLogs)

            FriendsRankingSection(
                ranking  = state.friendsRanking,
                loading  = state.rankingLoading,
                onRefresh = viewModel::refreshRanking,
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showStarsDialog) {
        StarsRulesDialog(
            stars       = state.stars,
            streak      = state.currentStreak,
            onDismiss   = { showStarsDialog = false },
        )
    }

    if (showRanksDialog) {
        RanksDialog(
            currentTier = state.tier,
            userStars   = state.stars,
            onDismiss   = { showRanksDialog = false },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun Header(state: DashboardUiState, onStarsTap: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(greetingForNow(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (state.displayName.isNotEmpty()) {
                Text(state.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
            }
        }

        // Tappable stars pill
        Surface(
            shape    = RoundedCornerShape(50),
            color    = StarColor.copy(alpha = 0.10f),
            modifier = Modifier
                .border(1.dp, StarColor.copy(alpha = 0.30f), RoundedCornerShape(50))
                .clickable(onClick = onStarsTap),
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(15.dp))
                Text(formatStars(state.stars), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StarColor)
                Icon(Icons.Rounded.Info, null, tint = StarColor.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ── Rank hero card ────────────────────────────────────────────────────────────

@Composable
private fun RankHeroCard(
    tier        : RankTier,
    nextTier    : RankTier?,
    stars       : Long,
    rankProgress: Float,
    onClick     : () -> Unit,
) {
    val animProg by animateFloatAsState(targetValue = rankProgress, animationSpec = tween(900, easing = FastOutSlowInEasing), label = "rankProg")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neonGlassCard(glowColor = tier.color, cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RankEmblem(tier = tier, size = 52.dp)
                    Column {
                        Text(tier.name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = tier.color, letterSpacing = 2.sp)
                        Text(tier.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatStars(stars), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(12.dp))
                        Text("stars", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }

            if (nextTier != null) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    LinearProgressIndicator(
                        progress   = { animProg },
                        modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color      = tier.color,
                        trackColor = TextMuted.copy(alpha = 0.18f),
                        strokeCap  = StrokeCap.Round,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tier.name, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("${formatStars(nextTier.minStars - stars)} to ${nextTier.name}", style = MaterialTheme.typography.labelSmall, color = nextTier.color, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Text(stringResource(R.string.profile_max_rank), style = MaterialTheme.typography.labelSmall, color = tier.color, fontWeight = FontWeight.Bold)
            }

            // Tap hint
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.TouchApp, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.dashboard_tap_ranks), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

// ── Daily ring card ───────────────────────────────────────────────────────────

@Composable
private fun DailyRingCard(reps: Int, goal: Int, modifier: Modifier, onClick: () -> Unit) {
    val progress = (reps.toFloat() / goal).coerceIn(0f, 1f)
    val animProg by animateFloatAsState(targetValue = progress, animationSpec = tween(800, easing = FastOutSlowInEasing), label = "ringProg")

    // Capture theme-sensitive colour in composable scope — Canvas DrawScope is not composable.
    // In light mode SurfaceElevated (#D4EBD4) on a white card gives clear ring-track contrast.
    val ringTrackColor = SurfaceElevated

    Box(
        modifier         = modifier.aspectRatio(1f).glassCard().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
            val sw      = 14.dp.toPx()
            val r       = (size.minDimension - sw) / 2f
            val tl      = Offset((size.width - r * 2) / 2f, (size.height - r * 2) / 2f)
            val arcSize = Size(r * 2, r * 2)
            drawArc(color = ringTrackColor, -90f, 360f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (animProg > 0f) {
                drawArc(
                    brush      = Brush.sweepGradient(0f to AccentGreenDark, 1f to AccentGreen),
                    startAngle = -90f, sweepAngle = 360f * animProg,
                    useCenter  = false, topLeft = tl, size = arcSize,
                    style      = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedCounter(
                value = reps,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            )
            Text("/ $goal", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(stringResource(R.string.dashboard_today), style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 2.sp)
        }
    }
}

// ── Streak card ───────────────────────────────────────────────────────────────

@Composable
private fun StreakCard(streak: Int, modifier: Modifier, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 28f,
        targetValue   = if (streak > 0) 34f else 28f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "flame_size",
    )
    Box(
        modifier         = modifier.aspectRatio(1f).glassCard().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.padding(12.dp),
        ) {
            Icon(Icons.Rounded.Whatshot, null, tint = if (streak > 0) WarningAmber else TextMuted, modifier = Modifier.size(pulse.dp))
            Spacer(Modifier.height(6.dp))
            AnimatedCounter(
                value     = streak,
                style     = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color     = if (streak > 0) WarningAmber else TextMuted,
                glowColor = WarningAmber,
            )
            Text(stringResource(R.string.dashboard_streak), style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 2.sp)
        }
    }
}

// ── Start Workout CTA ─────────────────────────────────────────────────────────

@Composable
private fun StartWorkoutButton(onClick: () -> Unit) {
    NeonCtaButton(
        text     = stringResource(R.string.dashboard_start_workout),
        onClick  = onClick,
        icon     = Icons.Rounded.PlayArrow,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Active challenges ─────────────────────────────────────────────────────────

@Composable
private fun ActiveChallengesSection(challenges: List<ActiveChallengeDisplay>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.dashboard_active_challenges), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Surface(shape = RoundedCornerShape(50), color = AccentGreen.copy(alpha = 0.12f)) {
                Text("${challenges.size} running", style = MaterialTheme.typography.labelSmall, color = AccentGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(challenges) { ActiveChallengeCard(it) }
        }
    }
}

@Composable
private fun ActiveChallengeCard(display: ActiveChallengeDisplay) {
    val animProg by animateFloatAsState(targetValue = display.progress, animationSpec = tween(700), label = "chProg")
    val diffColor = difficultyColor(display.challenge.difficulty)

    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, diffColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(diffColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(challengeIcon(display.challenge.type), null, tint = diffColor, modifier = Modifier.size(16.dp))
            }
            Text(display.challenge.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress   = { animProg },
            modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color      = diffColor,
            trackColor = GlassBorder,
            strokeCap  = StrokeCap.Round,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(display.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(10.dp))
                Text("+${display.challenge.starReward}", style = MaterialTheme.typography.labelSmall, color = StarColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Weekly bar chart ──────────────────────────────────────────────────────────

@Composable
private fun WeeklyChart(logs: List<DailyLog>) {
    val today     = LocalDate.now()
    val days      = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val repsByDay = logs.associateBy { it.date }
    val maxReps   = (logs.maxOfOrNull { it.totalReps } ?: 1).coerceAtLeast(1)
    val weekTotal = logs.sumOf { it.totalReps }

    Column(
        modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 16.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dashboard_this_week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Surface(shape = RoundedCornerShape(50), color = SurfaceElevated) {
                Text("$weekTotal reps", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Row(
            modifier              = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                val reps    = repsByDay[day]?.totalReps ?: 0
                val frac    = reps.toFloat() / maxReps.toFloat()
                val isToday = day == today
                BarColumn(frac, day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), reps, isToday, index, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarColumn(fraction: Float, dayLabel: String, reps: Int, isToday: Boolean, index: Int, modifier: Modifier) {
    // Staggered entrance: each bar grows from 0 with a per-day delay (game-brief spec).
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animFrac by animateFloatAsState(
        targetValue   = if (entered) fraction else 0f,
        animationSpec = tween(600, delayMillis = index * 70, easing = FastOutSlowInEasing),
        label         = "bar",
    )

    // In dark mode the inactive bar is a subtle dark-gray → dark-green gradient.
    // In light mode that same translucent gray is nearly invisible, so we switch to
    // a branded green gradient with enough opacity to read on the white card.
    val isDark = IsAppDark
    val surfaceElev = SurfaceElevated
    val textMutedColor = TextMuted
    val inactiveBrush = if (isDark)
        Brush.verticalGradient(listOf(textMutedColor.copy(alpha = 0.5f), surfaceElev))
    else
        Brush.verticalGradient(listOf(AccentGreen.copy(alpha = 0.55f), AccentGreen.copy(alpha = 0.20f)))

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        if (reps > 0) {
            Text(reps.toString(), color = if (isToday) AccentGreen else TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
        }
        Box(
            modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(animFrac.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(
                    if (isToday) Brush.verticalGradient(listOf(AccentGreen, AccentGreenDark))
                    else inactiveBrush
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(dayLabel, color = if (isToday) AccentGreen else TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

// ── Stars Rules Dialog ────────────────────────────────────────────────────────

@Composable
private fun StarsRulesDialog(stars: Long, streak: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass)
                .border(1.dp, StarColor.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(StarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(stringResource(R.string.dashboard_how_stars_work), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(stringResource(R.string.dashboard_your_currency), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            HorizontalDivider(color = GlassBorder)

            // Your current stars
            Surface(
                shape    = RoundedCornerShape(14.dp),
                color    = StarColor.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth().border(1.dp, StarColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.dashboard_your_stars), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(20.dp))
                        Text(formatStars(stars), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = StarColor)
                    }
                }
            }

            // Rule 1: Reps
            StarRuleBlock(
                icon    = Icons.Rounded.FitnessCenter,
                tint    = AccentGreen,
                title   = "1 Push-Up = 1 Star",
                body    = "Every rep you complete earns you exactly one star. They stack up session after session, day after day.",
                formula = "reps × 1",
            )

            // Rule 2: Streak bonus
            StarRuleBlock(
                icon    = Icons.Rounded.Whatshot,
                tint    = WarningAmber,
                title   = "Streak Bonus",
                body    = "Each consecutive day you work out, you earn bonus stars on top of your reps. The longer your streak, the bigger the bonus.",
                formula = "bonus = streak days − 1",
            )

            // Streak table
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceGlass).border(1.dp, GlassBorder, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    Triple("Day 1", "+0 bonus", false),
                    Triple("Day 2", "+1 bonus", false),
                    Triple("Day 3", "+2 bonus", false),
                    Triple("Day 7", "+6 bonus", false),
                    Triple("Day 30", "+29 bonus", false),
                    Triple("Day ${streak.coerceAtLeast(1)} (you)", "+${(streak - 1).coerceAtLeast(0)} bonus", true),
                ).distinctBy { it.first }.forEach { (label, value, isYou) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (isYou) WarningAmber else TextSecondary, fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal)
                        Text(value, style = MaterialTheme.typography.bodySmall, color = if (isYou) WarningAmber else AccentGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Rule 3: Combo multiplier
            StarRuleBlock(
                icon    = Icons.Rounded.MilitaryTech,
                tint    = Color(0xFFFFA726),
                title   = "Combo Multiplier",
                body    = "Keep your reps coming with no more than 3 seconds between each one. Hit 20 consecutive reps to unlock ×2, then ×3 at 30, ×4 at 40 — up to ×6 for 60+ in a row without stopping.",
                formula = "×2 at 20 · ×3 at 30 · ×4 at 40 · … · ×6 at 60+",
            )

            // Rule 4: Inactivity penalty
            StarRuleBlock(
                icon    = Icons.Rounded.Warning,
                tint    = ErrorRed,
                title   = "Inactivity Penalty",
                body    = "Skipping a day costs you stars. Every full missed day deducts ${RankSystem.PENALTY_PER_MISSED_DAY} stars. Go a full week or more without training and an extra ${RankSystem.PENALTY_EXTRA_WEEK} stars are deducted on top. Stars can never drop below zero.",
                formula = "−${RankSystem.PENALTY_PER_MISSED_DAY} ★ / day  ·  −${RankSystem.PENALTY_EXTRA_WEEK} ★ extra after 7 days",
            )

            // Rule 5: Challenges
            StarRuleBlock(
                icon    = Icons.Rounded.EmojiEvents,
                tint    = Color(0xFFCD7F32),
                title   = "Challenge Rewards",
                body    = "Completing an activated challenge awards bonus stars ranging from 100 (Amateur) to 5,000 (Beast Mode). Challenges are the fastest way to earn big.",
                formula = "100 – 5,000 stars per challenge",
            )

            // Rule 6: Advancements
            StarRuleBlock(
                icon    = Icons.Rounded.AutoAwesome,
                tint    = Color(0xFF7C4DFF),
                title   = "Advancement Bonuses",
                body    = "One-time star bonuses for hitting lifetime milestones — total reps, streaks, and social achievements. Check your Profile to see what's unlocked.",
                formula = "50 – 15,000 stars per milestone",
            )

            Button(
                onClick        = onDismiss,
                modifier       = Modifier.fillMaxWidth(),
                colors         = ButtonDefaults.buttonColors(containerColor = StarColor),
                shape          = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.dashboard_got_it), color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StarRuleBlock(icon: ImageVector, tint: Color, title: String, body: String, formula: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
        Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.08f)) {
            Text(
                formula,
                style      = MaterialTheme.typography.labelSmall,
                color      = tint,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

// ── Friends daily ranking ─────────────────────────────────────────────────────

@Composable
private fun FriendsRankingSection(
    ranking   : List<FriendRankEntry>,
    loading   : Boolean,
    onRefresh : () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = StarColor, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.dashboard_todays_ranking), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                if (loading) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "Refresh", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (ranking.isEmpty() && !loading) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Group, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.dashboard_no_friends_ranking), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ranking.forEachIndexed { index, entry ->
                    FriendRankRow(position = index + 1, entry = entry)
                    if (index < ranking.lastIndex) {
                        HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRankRow(position: Int, entry: FriendRankEntry) {
    val medalColor = when (position) {
        1 -> StarColor   // gold
        2 -> Color(0xFFC0C0C0)   // silver
        3 -> Color(0xFFCD7F32)   // bronze
        else -> TextMuted
    }
    val rowBg = when {
        entry.isMe -> AccentGreen.copy(alpha = 0.06f)
        else       -> Color.Transparent
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Position number / medal
        Box(
            modifier         = Modifier.width(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (position <= 3) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = medalColor, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    "$position",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color      = TextMuted,
                )
            }
        }

        // Avatar circle (initials or photo)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(entry.tier.color.copy(alpha = 0.20f))
                .border(1.5.dp, entry.tier.color.copy(alpha = 0.50f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!entry.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = entry.photoUrl,
                    contentDescription = null,
                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    entry.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color      = entry.tier.color,
                )
            }
        }

        // Name + "you" badge
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
                    color      = if (entry.isMe) AccentGreen else TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (entry.isMe) {
                    Surface(shape = RoundedCornerShape(50), color = AccentGreen.copy(alpha = 0.12f)) {
                        Text(stringResource(R.string.common_you), style = MaterialTheme.typography.labelSmall, color = AccentGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Streak
            if (entry.currentStreak > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Rounded.Whatshot, null, tint = Color(0xFFFF6B35), modifier = Modifier.size(11.dp))
                    Text("${entry.currentStreak}d streak", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }

        // Today's reps — prominent
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${entry.todayReps}",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color      = if (entry.todayReps > 0) TextPrimary else TextMuted,
            )
            Text(
                stringResource(R.string.common_reps),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun greetingForNow(): String = when (java.time.LocalTime.now().hour) {
    in 5..11  -> stringResource(R.string.dashboard_greeting_morning)
    in 12..17 -> stringResource(R.string.dashboard_greeting_afternoon)
    else       -> stringResource(R.string.dashboard_greeting_evening)
}

private fun difficultyColor(d: Difficulty) = when (d) {
    Difficulty.AMATEUR  -> Color(0xFF4CAF50)
    Difficulty.MEDIUM   -> Color(0xFFFFC107)
    Difficulty.ADVANCED -> Color(0xFFF44336)
}

private fun challengeIcon(type: ChallengeType): ImageVector = when (type) {
    ChallengeType.REPS_TOTAL     -> Icons.Rounded.FitnessCenter
    ChallengeType.STREAK         -> Icons.Rounded.Whatshot
    ChallengeType.DAILY_REPS     -> Icons.Rounded.Repeat
    ChallengeType.SINGLE_SESSION -> Icons.Rounded.FlashOn
}
