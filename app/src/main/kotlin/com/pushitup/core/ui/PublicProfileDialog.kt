package com.pushitup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pushitup.core.theme.*
import com.pushitup.domain.model.RankSystem
import com.pushitup.domain.model.UserProfile
// RankEmblem, rankIcon are in the same package (com.pushitup.core.ui)

@Composable
fun PublicProfileDialog(
    profile     : UserProfile,
    todayReps   : Int = 0,
    isFriend    : Boolean = false,
    onAddFriend : (() -> Unit)? = null,
    onDismiss   : () -> Unit,
) {
    val tier = RankSystem.tierFor(profile.stars)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass)
                .border(1.dp, tier.color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Close button
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick   = onDismiss,
                    modifier  = Modifier.align(Alignment.TopEnd).size(28.dp),
                ) {
                    Icon(Icons.Rounded.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Avatar — photo if available, letter initial as fallback
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(tier.color.copy(alpha = 0.3f), tier.color.copy(alpha = 0.05f))
                        )
                    )
                    .border(2.dp, tier.color.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!profile.photoDisplayUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(profile.photoDisplayUrl)
                            .memoryCacheKey("avatar_${profile.uid}_${profile.photoUpdatedAt}")
                            .diskCacheKey("avatar_${profile.uid}_${profile.photoUpdatedAt}")
                            .crossfade(true)
                            .build(),
                        contentDescription = profile.displayName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color      = tier.color,
                    )
                }
            }

            // Name + username
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    profile.displayName,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary,
                )
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            // Rank badge
            Surface(
                shape    = RoundedCornerShape(50),
                color    = tier.color.copy(alpha = 0.12f),
                modifier = Modifier.border(1.dp, tier.color.copy(alpha = 0.3f), RoundedCornerShape(50)),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RankEmblem(tier = tier, size = 20.dp)
                    Text(
                        tier.name.uppercase(),
                        style       = MaterialTheme.typography.labelLarge,
                        fontWeight  = FontWeight.Black,
                        color       = tier.color,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            // Equipped tags
            if (profile.equippedTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    profile.equippedTags.forEach { tag ->
                        Surface(
                            shape    = RoundedCornerShape(50),
                            color    = AccentGreen.copy(alpha = 0.12f),
                            modifier = Modifier.border(0.5.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(50)),
                        ) {
                            Text(
                                tag,
                                modifier  = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style     = MaterialTheme.typography.labelSmall,
                                color     = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // ── Today's push-ups highlight ────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(14.dp),
                color    = AccentGreen.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.FitnessCenter, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Text(
                            "Today's push-ups",
                            style  = MaterialTheme.typography.bodyMedium,
                            color  = TextSecondary,
                        )
                    }
                    Text(
                        todayReps.toString(),
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = AccentGreen,
                    )
                }
            }

            HorizontalDivider(color = GlassBorder)

            // Public stats
            Row(
                modifier               = Modifier.fillMaxWidth(),
                horizontalArrangement  = Arrangement.SpaceEvenly,
            ) {
                PublicStat(icon = Icons.Rounded.Star,          label = "Stars",      value = formatPublic(profile.stars),      tint = StarColor)
                VerticalDivider(color = GlassBorder, modifier = Modifier.height(48.dp))
                PublicStat(icon = Icons.Rounded.FitnessCenter, label = "Total Reps", value = formatPublic(profile.totalReps), tint = AccentGreen)
                VerticalDivider(color = GlassBorder, modifier = Modifier.height(48.dp))
                PublicStat(icon = Icons.Rounded.Whatshot,      label = "Streak",     value = "${profile.currentStreak}d",      tint = WarningAmber)
            }

            // ── Add Friend button (shown only for non-friends) ─────────────────
            if (!isFriend && onAddFriend != null) {
                Button(
                    onClick  = { onAddFriend(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Friend", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun PublicStat(icon: ImageVector, label: String, value: String, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

private fun formatPublic(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> "${n / 1_000}K"
    else           -> n.toString()
}
