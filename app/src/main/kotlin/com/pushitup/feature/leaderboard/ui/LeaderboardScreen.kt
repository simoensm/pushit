package com.pushitup.feature.leaderboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pushitup.R
import com.pushitup.core.onboarding.TourCoordinates
import com.pushitup.core.onboarding.TourTarget
import com.pushitup.core.theme.*
import com.pushitup.core.ui.PublicProfileDialog
import com.pushitup.core.ui.RankEmblem
import com.pushitup.core.ui.UserAvatar
import com.pushitup.core.ui.glassCard
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.UserProfile

@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel = hiltViewModel()) {
    val state          by viewModel.uiState.collectAsState()
    var viewingProfile by remember { mutableStateOf<UserProfile?>(null) }

    viewingProfile?.let { pub ->
        val myUid = state.currentUserProfile?.uid
        PublicProfileDialog(
            profile     = pub,
            todayReps   = state.viewingProfileTodayReps,
            isFriend    = viewModel.isFriend(pub.uid),
            onAddFriend = if (pub.uid != myUid && !viewModel.isFriend(pub.uid))
                ({ viewModel.sendFriendRequest(pub.uid) }) else null,
            onDismiss   = { viewingProfile = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .statusBarsPadding(),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.leaderboard_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = TextPrimary)
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Rounded.Refresh, "Refresh", tint = TextSecondary)
            }
        }

        // ── Tab chips ─────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { TourCoordinates.register(TourTarget.LEADERBOARD_TABS, it) },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LeaderboardTabChip(
                label    = stringResource(R.string.leaderboard_global),
                icon     = Icons.Rounded.Public,
                selected = state.tab == LeaderboardTab.GLOBAL,
                onClick  = { viewModel.setTab(LeaderboardTab.GLOBAL) },
                modifier = Modifier.weight(1f),
            )
            LeaderboardTabChip(
                label    = stringResource(R.string.leaderboard_friends),
                icon     = Icons.Rounded.Group,
                selected = state.tab == LeaderboardTab.FRIENDS,
                onClick  = { viewModel.setTab(LeaderboardTab.FRIENDS) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Content ───────────────────────────────────────────────────────────
        when (state.tab) {
            LeaderboardTab.GLOBAL  -> GlobalTab(state,  onOpenProfile = { viewingProfile = it; viewModel.loadProfileExtra(it.uid) })
            LeaderboardTab.FRIENDS -> FriendsTab(state, onOpenProfile = { viewingProfile = it; viewModel.loadProfileExtra(it.uid) })
        }
    }
}

// ── Tab chip ──────────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardTabChip(
    label    : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    selected : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val surface = SurfaceGlass
    val border  = GlassBorder
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(12.dp),
        // Selected: solid accent fill. Unselected: glass card.
        color    = if (selected) AccentGreen else surface,
        modifier = modifier.then(
            if (!selected) Modifier.border(1.dp, border, RoundedCornerShape(12.dp))
            else Modifier
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null,
                tint     = if (selected) Color.Black else TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = if (selected) Color.Black else TextMuted,
            )
        }
    }
}

// ── Global tab ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlobalTab(state: LeaderboardUiState, onOpenProfile: (UserProfile) -> Unit) {
    if (state.isLoadingGlobal) {
        LoadingState()
        return
    }
    if (state.globalEntries.isEmpty()) {
        EmptyState("No players yet.\nBe the first!")
        return
    }

    val myEntry = state.globalEntries.firstOrNull { it.isCurrentUser }

    LazyColumn(
        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.globalEntries.size >= 3) {
            // Podium for top 3, then rows for 4+
            item {
                Podium(
                    first         = state.globalEntries[0],
                    second        = state.globalEntries[1],
                    third         = state.globalEntries[2],
                    onOpenProfile = onOpenProfile,
                )
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(state.globalEntries.drop(3), key = { _, e -> e.profile.uid }) { i, entry ->
                LeaderboardRow(
                    entry         = entry,
                    index         = i,
                    modifier      = Modifier.animateItemPlacement(),
                    onOpenProfile = { onOpenProfile(entry.profile) },
                )
            }
        } else {
            // Fewer than 3 — skip podium, show flat list so entries are always visible
            itemsIndexed(state.globalEntries, key = { _, e -> e.profile.uid }) { i, entry ->
                LeaderboardRow(
                    entry         = entry,
                    index         = i,
                    modifier      = Modifier.animateItemPlacement(),
                    onOpenProfile = { onOpenProfile(entry.profile) },
                )
            }
        }

        // If current user is outside top 100, they're not in list — show separator + their card
        if (myEntry == null && state.currentUserProfile != null) {
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(Modifier.height(4.dp))
                Text("Your rank", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                val selfEntry = LeaderboardEntry(
                    profile       = state.currentUserProfile,
                    rank          = state.globalEntries.size + 1,
                    isCurrentUser = true,
                )
                LeaderboardRow(
                    entry         = selfEntry,
                    rankLabel     = "100+",
                    onOpenProfile = { onOpenProfile(state.currentUserProfile) },
                )
            }
        }
    }
}

// ── Friends tab ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendsTab(state: LeaderboardUiState, onOpenProfile: (UserProfile) -> Unit) {
    if (state.isLoadingFriends) {
        LoadingState()
        return
    }
    if (state.friendsEntries.isEmpty()) {
        EmptyState("Add friends to see\nhow you compare!")
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.friendsEntries.size >= 3) {
            item {
                Podium(
                    first         = state.friendsEntries[0],
                    second        = state.friendsEntries[1],
                    third         = state.friendsEntries[2],
                    onOpenProfile = onOpenProfile,
                )
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(state.friendsEntries.drop(3), key = { _, e -> e.profile.uid }) { i, entry ->
                LeaderboardRow(
                    entry         = entry,
                    index         = i,
                    modifier      = Modifier.animateItemPlacement(),
                    onOpenProfile = { onOpenProfile(entry.profile) },
                )
            }
        } else {
            // Fewer than 3 friends — flat list so entries are always visible
            itemsIndexed(state.friendsEntries, key = { _, e -> e.profile.uid }) { i, entry ->
                LeaderboardRow(
                    entry         = entry,
                    index         = i,
                    modifier      = Modifier.animateItemPlacement(),
                    onOpenProfile = { onOpenProfile(entry.profile) },
                )
            }
        }
    }
}

// ── Podium ────────────────────────────────────────────────────────────────────

@Composable
private fun Podium(
    first         : LeaderboardEntry,
    second        : LeaderboardEntry,
    third         : LeaderboardEntry,
    onOpenProfile : (UserProfile) -> Unit,
) {
    val gold   = StarColor
    val silver = Color(0xFFBDBDBD)
    val bronze = Color(0xFFCD7F32)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceGlass.copy(alpha = 0.8f), SurfaceGlass)
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.Bottom,
        ) {
            // 2nd place (left, shorter)
            PodiumPillar(entry = second, medalColor = silver, height = 90.dp,  crownSize = 18.dp, riseDelay = 120, onClick = { onOpenProfile(second.profile) })
            // 1st place (center, tallest)
            PodiumPillar(entry = first,  medalColor = gold,   height = 120.dp, crownSize = 24.dp, showCrown = true, riseDelay = 0, onClick = { onOpenProfile(first.profile) })
            // 3rd place (right, shortest)
            PodiumPillar(entry = third,  medalColor = bronze, height = 70.dp,  crownSize = 16.dp, riseDelay = 240, onClick = { onOpenProfile(third.profile) })
        }
    }
}

@Composable
private fun PodiumPillar(
    entry      : LeaderboardEntry,
    medalColor : Color,
    height     : androidx.compose.ui.unit.Dp,
    crownSize  : androidx.compose.ui.unit.Dp,
    showCrown  : Boolean = false,
    riseDelay  : Int = 0,
    onClick    : () -> Unit = {},
) {
    val tier = RankSystem.tierFor(entry.profile.stars)

    // Pillars rise from the floor with a stagger (1st → 2nd → 3rd) — once.
    // rememberSaveable prevents replaying the rise every time the podium
    // scrolls back into view (LazyColumn recomposition).
    var risen by rememberSaveable { mutableStateOf(false) }
    val riseFrac by animateFloatAsState(
        targetValue   = if (risen) 1f else 0f,
        animationSpec = tween(500, delayMillis = riseDelay, easing = FastOutSlowInEasing),
        label         = "podium_rise",
    )
    LaunchedEffect(Unit) { risen = true }

    // Champion crown pulses gold
    val crownScale = if (showCrown) {
        val breath = rememberInfiniteTransition(label = "crown")
        val s by breath.animateFloat(
            initialValue  = 1f,
            targetValue   = 1.15f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "crown_scale",
        )
        s
    } else 1f
    // In light mode the card surface is white — low-alpha fills (0.08–0.25) wash out.
    // Boost alpha so pillars remain visible regardless of theme.
    val isDark = IsAppDark
    val pillarTopAlpha    = if (isDark) 0.25f else 0.50f
    val pillarBottomAlpha = if (isDark) 0.08f else 0.22f
    val pillarBorderAlpha = if (isDark) 0.35f else 0.65f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier            = Modifier.width(100.dp).clickable { onClick() },
    ) {
        // Crown for 1st — pulsing
        if (showCrown) {
            Icon(
                Icons.Rounded.EmojiEvents, null,
                tint     = medalColor,
                modifier = Modifier.size(crownSize).graphicsLayer {
                    scaleX = crownScale
                    scaleY = crownScale
                },
            )
            Spacer(Modifier.height(2.dp))
        }

        // Avatar
        UserAvatar(
            photoUrl    = entry.profile.photoDisplayUrl,
            displayName = entry.profile.displayName,
            size        = 52.dp,
            tierColor   = tier.color,
            borderWidth = 2.dp,
            cacheKey    = "avatar_${entry.profile.uid}_${entry.profile.photoUpdatedAt}",
        )

        Spacer(Modifier.height(6.dp))

        // Name
        Text(
            entry.profile.displayName,
            style     = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color     = if (entry.isCurrentUser) AccentGreen else TextPrimary,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        // Stars — always gold
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(10.dp))
            Text(formatStars(entry.profile.stars), style = MaterialTheme.typography.labelSmall, color = StarColor, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(6.dp))

        // Pillar block — height animates up on entrance
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(height * riseFrac)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(
                    Brush.verticalGradient(listOf(medalColor.copy(alpha = pillarTopAlpha), medalColor.copy(alpha = pillarBottomAlpha)))
                )
                .border(1.dp, medalColor.copy(alpha = pillarBorderAlpha), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "#${entry.rank}",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color      = medalColor,
            )
        }
    }
}

// ── Leaderboard row ───────────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(
    entry         : LeaderboardEntry,
    rankLabel     : String? = null,
    index         : Int = 0,
    modifier      : Modifier = Modifier,
    onOpenProfile : () -> Unit = {},
) {
    val tier        = RankSystem.tierFor(entry.profile.stars)
    val isCurrentUser = entry.isCurrentUser

    // Stagger fade + slide entrance — ONCE per row. rememberSaveable survives the
    // row leaving/re-entering LazyColumn composition during scroll; plain remember
    // would replay the (delayed) animation on every scroll-back, leaving rows
    // briefly invisible. Only rows near the top get a stagger delay.
    var entered by rememberSaveable { mutableStateOf(false) }
    val entrance by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(300, delayMillis = (index * 40).coerceAtMost(280), easing = FastOutSlowInEasing),
        label         = "row_entrance",
    )
    LaunchedEffect(Unit) { entered = true }

    // Breathing cyan glow on the current user's row
    val glowAlpha = if (isCurrentUser) {
        val breath = rememberInfiniteTransition(label = "you_glow")
        val a by breath.animateFloat(
            initialValue  = 0.30f,
            targetValue   = 0.65f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "you_glow_alpha",
        )
        a
    } else 0f

    val bg          = if (isCurrentUser) AccentGreen.copy(alpha = 0.06f) else Color.Transparent
    val borderColor = if (isCurrentUser) AccentGreen.copy(alpha = glowAlpha) else GlassBorder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha        = entrance
                translationY = (1f - entrance) * 12.dp.toPx()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (isCurrentUser) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onOpenProfile() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Rank number
        Text(
            rankLabel ?: "#${entry.rank}",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color      = rankColor(entry.rank),
            modifier   = Modifier.width(36.dp),
            textAlign  = TextAlign.Center,
        )

        // Avatar
        UserAvatar(
            photoUrl    = entry.profile.photoDisplayUrl,
            displayName = entry.profile.displayName,
            size        = 40.dp,
            tierColor   = tier.color,
            borderWidth = if (isCurrentUser) 1.5.dp else 1.dp,
            cacheKey    = "avatar_${entry.profile.uid}_${entry.profile.photoUpdatedAt}",
        )

        // Name + username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.profile.displayName + if (isCurrentUser) " (you)" else "",
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = if (isCurrentUser) AccentGreen else TextPrimary,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                "@${entry.profile.username}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }

        // Tier badge — emblem + name
        Surface(
            shape    = RoundedCornerShape(50),
            color    = tier.color.copy(alpha = 0.12f),
            modifier = Modifier.border(0.5.dp, tier.color.copy(alpha = 0.30f), RoundedCornerShape(50)),
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RankEmblem(tier = tier, size = 14.dp)
                Text(
                    tier.name,
                    style      = MaterialTheme.typography.labelSmall,
                    color      = tier.color,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Stars + reps — stars always gold
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(12.dp))
                Text(formatStars(entry.profile.stars), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = StarColor)
            }
            Text("${entry.profile.totalReps} reps", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Loading / empty ───────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentGreen)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun rankColor(rank: Int): Color = when (rank) {
    1    -> StarColor // gold
    2    -> Color(0xFFBDBDBD) // silver
    3    -> Color(0xFFCD7F32) // bronze
    // TextMuted is theme-dependent (@Composable get) so we can't use it here.
    // Use a neutral mid-gray that reads clearly on both light and dark backgrounds.
    else -> Color(0xFF888888)
}

private fun formatStars(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> String.format("%.1fK", n / 1_000f)
    else           -> n.toString()
}
