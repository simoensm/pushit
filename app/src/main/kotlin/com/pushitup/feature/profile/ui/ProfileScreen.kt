package com.pushitup.feature.profile.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel
import com.pushitup.R
import com.pushitup.core.theme.*
import com.pushitup.core.ui.AchievementCard
import com.pushitup.core.ui.PublicProfileDialog
import com.pushitup.core.ui.RankEmblem
import com.pushitup.core.ui.RanksDialog
import com.pushitup.domain.model.*


@Composable
fun ProfileScreen(
    onSignOut            : () -> Unit,
    onNavigateToSettings : () -> Unit,
    viewModel            : ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showRules       by remember { mutableStateOf(false) }
    var showRanksDialog by remember { mutableStateOf(false) }

    // ── Photo upload: gallery → UCrop → compress → upload ────────────────────
    val cropDestFile = remember { File(context.cacheDir, "profile_crop.jpg") }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val croppedUri = UCrop.getOutput(result.data ?: return@rememberLauncherForActivityResult)
            ?: return@rememberLauncherForActivityResult

        // ── Compression pipeline ──────────────────────────────────────────────
        // UCrop already output a 360×360 JPEG at q=75.
        // We decode it, ensure it fits within MAX_DIM, then re-compress at q=72
        // to guarantee the final upload is under ~80 KB regardless of source.
        val rawBytes = context.contentResolver.openInputStream(croppedUri)
            ?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult

        val original = android.graphics.BitmapFactory
            .decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return@rememberLauncherForActivityResult

        val maxDim = 360
        val scaled = if (original.width > maxDim || original.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(original.width, original.height)
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width  * scale).toInt(),
                (original.height * scale).toInt(),
                true,
            )
        } else original

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)

        if (scaled !== original) scaled.recycle()
        original.recycle()

        viewModel.uploadPhoto(baos.toByteArray())
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val dest   = Uri.fromFile(cropDestFile)
        val darkBg = android.graphics.Color.parseColor("#0D0D0F")
        val green  = android.graphics.Color.parseColor("#1DB954")
        val toolbarBg = android.graphics.Color.parseColor("#141416")
        val opts = UCrop.Options().apply {
            // ── Dark theme (status bar handled by Theme.UCrop in themes.xml) ──
            setToolbarColor(toolbarBg)
            setToolbarWidgetColor(android.graphics.Color.WHITE)
            setToolbarTitle("Edit Profile Photo")
            setRootViewBackgroundColor(darkBg)
            // ── Circular crop with dark overlay + green handles ───────────────
            setCircleDimmedLayer(true)
            setDimmedLayerColor(android.graphics.Color.argb(210, 13, 13, 15))
            setShowCropGrid(false)
            setShowCropFrame(false)
            setActiveControlsWidgetColor(green)
            // ── Show bottom controls bar (rotate / scale) ─────────────────────
            setHideBottomControls(false)
            // ── Output: UCrop does a first-pass at 360×360 / q=75 ────────────
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(75)
            setMaxBitmapSize(1024)
        }
        val cropIntent = UCrop.of(uri, dest)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(360, 360)
            .withOptions(opts)
            .getIntent(context)
        // Redirect to our wrapper activity so the toolbar stays below the status bar
        cropIntent.setClass(context, com.pushitup.core.ui.PushItCropActivity::class.java)
        cropLauncher.launch(cropIntent)
    }

    // Auto-dismiss error after 6 s (longer so photo upload errors are readable)
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            kotlinx.coroutines.delay(6_000)
            viewModel.clearError()
        }
    }

    // ── Photo options bottom sheet ────────────────────────────────────────────
    var showPhotoSheet by remember { mutableStateOf(false) }
    if (showPhotoSheet) {
        PhotoOptionsSheet(
            hasPhoto       = !uiState.profile?.photoDisplayUrl.isNullOrBlank(),
            onChangePic    = { showPhotoSheet = false; galleryLauncher.launch("image/*") },
            onUseInitial   = { showPhotoSheet = false; viewModel.removePhoto() },
            onDismiss      = { showPhotoSheet = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDeep)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nav_profile), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // "How it works" pill
                    Surface(
                        onClick      = { showRules = true },
                        shape        = RoundedCornerShape(50),
                        color        = SurfaceGlass,
                        border       = BorderStroke(1.dp, GlassBorder),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint     = AccentGreen,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                "How it works",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = TextSecondary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary)
                    }
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentGreen)
                }
            } else {
                uiState.profile?.let { profile ->
                    ProfileContent(
                        profile       = profile,
                        uiState       = uiState,
                        onSaveName    = { viewModel.updateDisplayName(it) },
                        onSaveHeight  = { viewModel.updateHeight(it) },
                        onSaveWeight  = { viewModel.updateWeight(it) },
                        onSaveGoal    = { viewModel.updateDailyGoal(it) },
                        onClaim       = { viewModel.claimAchievement(it) },
                        onOpenRanks   = { showRanksDialog = true },
                        onEquipTag    = { viewModel.equipTag(it) },
                        onUnequipTag  = { viewModel.unequipTag(it) },
                        onPhotoClick  = { showPhotoSheet = true },
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // ── Error toast ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = uiState.error != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
            enter    = fadeIn() + slideInVertically { it / 2 },
            exit     = fadeOut(),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = ErrorRed) {
                Row(
                    modifier              = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(uiState.error ?: "", color = Color.White, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Public profile dialog
    uiState.viewingProfile?.let { pub ->
        val myFriends = uiState.profile?.friends ?: emptyList()
        val alreadyFriend = pub.uid in myFriends
        PublicProfileDialog(
            profile     = pub,
            todayReps   = uiState.viewingProfileTodayReps,
            isFriend    = alreadyFriend,
            onAddFriend = if (!alreadyFriend) ({ viewModel.sendFriendRequest(pub.uid) }) else null,
            onDismiss   = { viewModel.clearPublicProfile() },
        )
    }

    if (showRules) {
        RulesDialog(onDismiss = { showRules = false })
    }

    if (showRanksDialog) {
        val profile = uiState.profile
        RanksDialog(
            currentTier = profile?.let { RankSystem.tierFor(it.stars) },
            userStars   = profile?.stars ?: 0L,
            onDismiss   = { showRanksDialog = false },
        )
    }
}

// ── Profile content ───────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    profile      : UserProfile,
    uiState      : ProfileUiState,
    onSaveName   : (String) -> Unit,
    onSaveHeight : (Float?) -> Unit,
    onSaveWeight : (Float?) -> Unit,
    onSaveGoal   : (Int) -> Unit,
    onClaim      : (Achievement) -> Unit,
    onOpenRanks  : () -> Unit,
    onEquipTag   : (String) -> Unit,
    onUnequipTag : (String) -> Unit,
    onPhotoClick : () -> Unit,
) {
    var selectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    val tier     = RankSystem.tierFor(profile.stars)
    val nextTier = RankSystem.nextTier(profile.stars)
    val progress = RankSystem.progressToNext(profile.stars)
    val animProg by animateFloatAsState(targetValue = progress, label = "rankProg")

    var editingName        by remember { mutableStateOf(false) }
    var nameInput          by remember { mutableStateOf(profile.displayName) }
    var editingHeight      by remember { mutableStateOf(false) }
    var heightInput        by remember { mutableStateOf(profile.height?.toInt()?.toString() ?: "") }
    var editingWeight      by remember { mutableStateOf(false) }
    var weightInput        by remember { mutableStateOf(profile.weight?.toInt()?.toString() ?: "") }
    var editingGoal        by remember { mutableStateOf(false) }
    var goalInput          by remember { mutableStateOf(profile.dailyGoal.toString()) }
    var showAllAchievements by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Hero card ─────────────────────────────────────────────────────────
        ProfileHeroCard(
            profile          = profile,
            tier             = tier,
            isUploadingPhoto = uiState.isUploadingPhoto,
            onEditName       = { editingName = true },
            onPhotoClick     = onPhotoClick,
        )

        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Stats grid (2 rows × 3 cols) ─────────────────────────────────
            // Weekly workout time from sessions that started in the last 7 days
            val weekCutoff = java.time.Instant.now().minusSeconds(7L * 24 * 3600)
            val weekSeconds = uiState.recentSessions
                .filter { it.startedAt.isAfter(weekCutoff) }
                .sumOf { it.durationSeconds }
            val weekTimeLabel = when {
                weekSeconds >= 3600 -> "%dh %02dm".format(weekSeconds / 3600, (weekSeconds % 3600) / 60)
                weekSeconds >= 60   -> "%dm".format(weekSeconds / 60)
                else                -> "${weekSeconds}s"
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileStatChip(
                        icon     = Icons.Rounded.FitnessCenter,
                        tint     = AccentGreen,
                        label    = stringResource(R.string.profile_total_reps),
                        value    = formatLarge(profile.totalReps),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStatChip(
                        icon     = Icons.Rounded.EmojiEvents,
                        tint     = StarColor,
                        label    = stringResource(R.string.profile_best_session),
                        value    = formatLarge(uiState.bestSessionReps.toLong()),
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStatChip(
                        icon     = Icons.Rounded.Whatshot,
                        tint     = Color(0xFFFF6B35),
                        label    = stringResource(R.string.profile_best_streak),
                        value    = "${profile.longestStreak}d",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileStatChip(
                        icon     = Icons.Rounded.Timer,
                        tint     = Color(0xFF818CF8),
                        label    = "This week",
                        value    = weekTimeLabel,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileStatChip(
                        label    = stringResource(R.string.profile_stars),
                        value    = formatLarge(profile.stars),
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(18.dp)) }
                    ProfileStatChip(
                        icon     = Icons.Rounded.Group,
                        tint     = Color(0xFF34D399),
                        label    = stringResource(R.string.profile_friends_count),
                        value    = profile.friends.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Rank progress ─────────────────────────────────────────────────
            RankProgressCard(tier, nextTier, profile.stars, animProg, onClick = onOpenRanks)

            // ── Profile tags ──────────────────────────────────────────────────
            TagsSection(
                equippedTags  = profile.equippedTags,
                availableTags = uiState.availableTags,
                onEquip       = onEquipTag,
                onUnequip     = onUnequipTag,
            )

            // ── Weekly activity chart ─────────────────────────────────────────
            if (uiState.weeklyLogs.isNotEmpty() || true) {
                WeeklyActivityChart(logs = uiState.weeklyLogs)
            }

            // ── Recent activity ───────────────────────────────────────────────
            if (uiState.recentSessions.isNotEmpty()) {
                RecentActivitySection(
                    sessions  = uiState.recentSessions,
                    onSessionClick = { selectedSession = it },
                )
            }

            // ── Achievements badge row ────────────────────────────────────────
            AchievementsBadgeRow(
                unlocked    = uiState.unlockedAchievements,
                locked      = uiState.lockedAchievements,
                claimable   = uiState.claimableAchievements,
                progressFor = { a ->
                    a.progressFraction(
                        totalReps       = profile.totalReps,
                        longestStreak   = profile.longestStreak,
                        friendsCount    = profile.friends.size,
                        bestSessionReps = uiState.bestSessionReps.toLong(),
                    )
                },
                onClaim     = onClaim,
                onSeeAll    = { showAllAchievements = true },
            )

            // ── Body metrics ──────────────────────────────────────────────────
            BodyStatsCard(
                profile      = profile,
                onEditHeight = { editingHeight = true },
                onEditWeight = { editingWeight = true },
                onEditGoal   = { editingGoal = true },
            )
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    selectedSession?.let { session ->
        SessionDetailDialog(session = session, onDismiss = { selectedSession = null })
    }
    if (showAllAchievements) {
        AllAchievementsDialog(
            unlocked    = uiState.unlockedAchievements,
            locked      = uiState.lockedAchievements,
            progressFor = { a ->
                a.progressFraction(
                    totalReps       = profile.totalReps,
                    longestStreak   = profile.longestStreak,
                    friendsCount    = profile.friends.size,
                    bestSessionReps = uiState.bestSessionReps.toLong(),
                )
            },
            onDismiss   = { showAllAchievements = false },
        )
    }
    if (editingName) {
        EditDialog(title = "Display Name", value = nameInput, onChange = { nameInput = it },
            onConfirm = { onSaveName(nameInput); editingName = false }, onDismiss = { editingName = false })
    }
    if (editingHeight) {
        EditDialog(title = "Height (cm)", value = heightInput, onChange = { heightInput = it }, numeric = true,
            onConfirm = { onSaveHeight(heightInput.toFloatOrNull()); editingHeight = false }, onDismiss = { editingHeight = false })
    }
    if (editingWeight) {
        EditDialog(title = "Weight (kg)", value = weightInput, onChange = { weightInput = it }, numeric = true,
            onConfirm = { onSaveWeight(weightInput.toFloatOrNull()); editingWeight = false }, onDismiss = { editingWeight = false })
    }
    if (editingGoal) {
        EditDialog(title = "Daily Goal (reps)", value = goalInput, onChange = { goalInput = it }, numeric = true,
            onConfirm = {
                val g = goalInput.toIntOrNull()?.coerceIn(1, 9999) ?: profile.dailyGoal
                goalInput = g.toString()
                onSaveGoal(g)
                editingGoal = false
            },
            onDismiss = { editingGoal = false })
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroCard(
    profile          : UserProfile,
    tier             : RankTier,
    isUploadingPhoto : Boolean,
    onEditName       : () -> Unit,
    onPhotoClick     : () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        // Gradient background — solid rank colour at top, hard fade to page background
        val bgDeep = BackgroundDeep
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to tier.color,
                        0.50f to tier.color.copy(alpha = 0.60f).compositeOver(bgDeep),
                        1.00f to bgDeep,
                    )
                ),
        )

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar — photo or letter initial, tap to open photo options
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(3.dp, Color.White.copy(alpha = 0.80f), CircleShape)
                    .clickable(onClick = onPhotoClick),
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
                        contentDescription = "Profile photo",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        profile.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style      = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color      = Color.White,
                    )
                }

                // Subtle bottom-gradient edit affordance — always visible when idle
                if (!isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.38f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.62f),
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = "Change photo",
                            tint     = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(18.dp).padding(bottom = 4.dp),
                        )
                    }
                }

                // Spinner overlay — only while uploading
                if (isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color       = AccentGreen,
                            modifier    = Modifier.size(30.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }

            // Name row
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            profile.displayName,
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color      = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(onClick = onEditName),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Edit, "Edit name", tint = Color.White.copy(alpha = 0.80f), modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(
                        "@${profile.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }

            // Stars + Streak pills
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroPill(value = formatLarge(profile.stars), label = "stars") {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(14.dp))
                }
                HeroPill(
                    icon   = Icons.Rounded.Whatshot,
                    tint   = Color(0xFFFF6B35),
                    value  = "${profile.currentStreak}",
                    label  = "day streak",
                )
                // Rank badge pill — unique Canvas emblem per tier
                HeroPill(value = tier.name, label = "rank") {
                    RankEmblem(tier = tier, size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun HeroPill(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, value: String, label: String) {
    HeroPillBase(value = value, label = label) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun HeroPill(value: String, label: String, iconContent: @Composable () -> Unit) {
    HeroPillBase(value = value, label = label, iconContent = iconContent)
}

@Composable
private fun HeroPillBase(value: String, label: String, iconContent: @Composable () -> Unit) {
    val pillBg     = if (IsAppDark) Color.Black.copy(alpha = 0.35f) else AccentGreen.copy(alpha = 0.15f)
    val pillBorder = if (IsAppDark) Color.White.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.40f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(pillBg)
            .border(1.dp, pillBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        iconContent()
        Text(
            "$value $label",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = if (IsAppDark) Color.White else TextPrimary,
        )
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────────

@Composable
private fun ProfileStatChip(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    tint     : Color,
    label    : String,
    value    : String,
    modifier : Modifier = Modifier,
) {
    ProfileStatChipBase(label = label, value = value, modifier = modifier) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileStatChip(
    label    : String,
    value    : String,
    modifier : Modifier = Modifier,
    iconContent : @Composable () -> Unit,
) {
    ProfileStatChipBase(label = label, value = value, modifier = modifier, iconContent = iconContent)
}

@Composable
private fun ProfileStatChipBase(
    label       : String,
    value       : String,
    modifier    : Modifier = Modifier,
    iconContent : @Composable () -> Unit,
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        iconContent()
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 0.5.sp, textAlign = TextAlign.Center)
    }
}

// ── Rank progress card ────────────────────────────────────────────────────────

@Composable
private fun RankProgressCard(tier: RankTier, nextTier: RankTier?, stars: Long, animProg: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tier emblem — unique Canvas-drawn badge per rank tier
        RankEmblem(tier = tier, size = 52.dp)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tier.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = tier.color, letterSpacing = 1.sp)
                if (nextTier != null) {
                    Text(
                        "${formatLarge(nextTier.minStars - stars)} ⭐ to ${nextTier.name}",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = nextTier.color,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("MAX RANK", style = MaterialTheme.typography.labelSmall, color = tier.color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            LinearProgressIndicator(
                progress  = { animProg },
                modifier  = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color     = tier.color,
                trackColor = TextMuted.copy(alpha = 0.18f),
                strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }

        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

// ── Weekly activity chart ─────────────────────────────────────────────────────

@Composable
private fun WeeklyActivityChart(logs: List<DailyLog>) {
    val today     = java.time.LocalDate.now()
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Build a 7-slot list aligned to Mon–Sun
    val repsPerDay = (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        logs.find { it.date == date }?.totalReps ?: 0
    }
    val maxReps = repsPerDay.maxOrNull()?.coerceAtLeast(1) ?: 1

    // Day-of-week labels aligned to the 7 slots
    val slotLabels = (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        dayLabels[date.dayOfWeek.value - 1]
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("This Week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${repsPerDay.sum()} reps", style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
        }

        val BAR_MAX_HEIGHT = 68.dp

        Row(
            modifier              = Modifier.fillMaxWidth().height(BAR_MAX_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.Bottom,
        ) {
            repsPerDay.forEachIndexed { idx, reps ->
                val isToday  = idx == 6
                val frac     = if (reps > 0) (reps.toFloat() / maxReps).coerceIn(0.05f, 1f) else 0.03f
                val barColor = when {
                    isToday  -> AccentGreen
                    reps > 0 -> AccentGreen.copy(alpha = 0.45f)
                    else     -> GlassBorder.copy(alpha = 0.6f)
                }
                val animFrac by animateFloatAsState(targetValue = frac, label = "bar$idx")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(BAR_MAX_HEIGHT * animFrac)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slotLabels.forEach { label ->
                Text(
                    label,
                    modifier  = Modifier.weight(1f),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Achievements badge row ────────────────────────────────────────────────────

@Composable
private fun AchievementsBadgeRow(
    unlocked    : List<Achievement>,
    locked      : List<Achievement>,
    claimable   : List<Achievement>,
    progressFor : (Achievement) -> Float = { 0f },
    onClaim     : (Achievement) -> Unit,
    onSeeAll    : () -> Unit,
) {
    val all = unlocked.map { it to true } + locked.map { it to false }
    val claimableIds = claimable.map { it.id }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_advancements), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${unlocked.size}/${all.size}", style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.SemiBold)
                if (claimable.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(50), color = StarColor.copy(alpha = 0.18f)) {
                        Text(
                            "${claimable.size} to claim!",
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = StarColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            TextButton(
                onClick      = onSeeAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.profile_see_all), style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            }
        }

        var selectedAchievement by remember { mutableStateOf<Pair<Achievement, Boolean>?>(null) }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 0.dp),
        ) {
            items(all) { (achievement, isUnlocked) ->
                AchievementBadge(
                    achievement = achievement,
                    isUnlocked  = isUnlocked,
                    isClaimed   = !claimableIds.contains(achievement.id),
                    progress    = if (isUnlocked) null else progressFor(achievement),
                    onClaim     = if (claimableIds.contains(achievement.id)) ({ onClaim(achievement) }) else null,
                    onClick     = { selectedAchievement = achievement to isUnlocked },
                )
            }
        }

        selectedAchievement?.let { (ach, unlocked) ->
            AchievementDetailDialog(
                achievement = ach,
                isUnlocked  = unlocked,
                isClaiming  = claimableIds.contains(ach.id),
                onClaim     = if (claimableIds.contains(ach.id)) { { onClaim(ach); selectedAchievement = null } } else null,
                onDismiss   = { selectedAchievement = null },
            )
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement : Achievement,
    isUnlocked  : Boolean,
    isClaimed   : Boolean   = true,
    progress    : Float?    = null,
    onClaim     : (() -> Unit)? = null,
    onClick     : (() -> Unit)? = null,
) {
    // Delegate to the new Clash Royale-style card in core/ui
    AchievementCard(
        achievement = achievement,
        isUnlocked  = isUnlocked,
        isClaimed   = isClaimed,
        progress    = progress,
        onClaim     = onClaim,
        onClick     = onClick,
    )
}

// ── Profile tags section ──────────────────────────────────────────────────────

@Composable
private fun TagsSection(
    equippedTags  : List<String>,
    availableTags : List<String>,
    onEquip       : (String) -> Unit,
    onUnequip     : (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val canEquipMore = equippedTags.size < 3

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.LocalOffer, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Text("Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${equippedTags.size}/3", style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.SemiBold)
            }
            if (canEquipMore && availableTags.isNotEmpty()) {
                TextButton(
                    onClick        = { showPicker = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Add tag", style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                }
            }
        }

        if (equippedTags.isEmpty() && availableTags.isEmpty()) {
            Text(
                "Unlock achievements to earn tags for your profile",
                style  = MaterialTheme.typography.bodySmall,
                color  = TextMuted,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                equippedTags.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onUnequip(tag) })
                }
                if (canEquipMore && availableTags.isNotEmpty()) {
                    // "+ Add" placeholder chip
                    Surface(
                        shape    = RoundedCornerShape(50),
                        color    = SurfaceGlass,
                        modifier = Modifier
                            .border(1.dp, GlassBorder, RoundedCornerShape(50))
                            .clickable { showPicker = true },
                    ) {
                        Row(
                            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                            Text("Add", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
                if (equippedTags.isEmpty()) {
                    Text(
                        "Tap \"Add tag\" to pick your first tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }

    if (showPicker) {
        TagPickerDialog(
            availableTags = availableTags,
            onPick        = { tag -> onEquip(tag); showPicker = false },
            onDismiss     = { showPicker = false },
        )
    }
}

@Composable
private fun TagChip(tag: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AccentGreen.copy(alpha = 0.14f),
        modifier = Modifier.border(1.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(50)),
    ) {
        Row(
            modifier              = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tag, style = MaterialTheme.typography.labelMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.18f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, null, tint = AccentGreen, modifier = Modifier.size(9.dp))
            }
        }
    }
}

@Composable
private fun TagPickerDialog(
    availableTags : List<String>,
    onPick        : (String) -> Unit,
    onDismiss     : () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGlass)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Choose a tag", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("From your unlocked achievements", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = GlassBorder)

            // Tag grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTags.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tag ->
                            Surface(
                                shape    = RoundedCornerShape(50),
                                color    = AccentGreen.copy(alpha = 0.10f),
                                modifier = Modifier
                                    .border(1.dp, AccentGreen.copy(alpha = 0.30f), RoundedCornerShape(50))
                                    .clickable { onPick(tag) },
                            ) {
                                Text(
                                    tag,
                                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style     = MaterialTheme.typography.labelMedium,
                                    color     = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Cancel", color = TextSecondary)
            }
        }
    }
}

// ── Body stats card ───────────────────────────────────────────────────────────

@Composable
private fun BodyStatsCard(
    profile      : UserProfile,
    onEditHeight : () -> Unit,
    onEditWeight : () -> Unit,
    onEditGoal   : () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Body Stats & Goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyStatItem(
                icon     = Icons.Rounded.Height,
                label    = "Height",
                value    = if (profile.height != null) "${profile.height.toInt()} cm" else "—",
                onClick  = onEditHeight,
                modifier = Modifier.weight(1f),
            )
            BodyStatItem(
                icon     = Icons.Rounded.MonitorWeight,
                label    = "Weight",
                value    = if (profile.weight != null) "${profile.weight.toInt()} kg" else "—",
                onClick  = onEditWeight,
                modifier = Modifier.weight(1f),
            )
        }
        BodyStatItem(
            icon     = Icons.Rounded.Flag,
            label    = "Daily Goal",
            value    = "${profile.dailyGoal} reps",
            onClick  = onEditGoal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BodyStatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundDeep.copy(alpha = 0.50f))
            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.Edit, null, tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
    }
}

// ── All achievements dialog ───────────────────────────────────────────────────

@Composable
private fun AllAchievementsDialog(
    unlocked    : List<Achievement>,
    locked      : List<Achievement>,
    progressFor : (Achievement) -> Float = { 0f },
    onDismiss   : () -> Unit,
) {
    val tabs  = listOf(stringResource(R.string.advancements_all), stringResource(R.string.advancements_unlocked), stringResource(R.string.advancements_locked))
    var tab   by remember { mutableStateOf(0) }

    val displayed = when (tab) {
        1    -> unlocked.map { it to true }
        2    -> locked.map   { it to false }
        else -> unlocked.map { it to true } + locked.map { it to false }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.profile_advancements), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(stringResource(R.string.advancements_subtitle, unlocked.size, unlocked.size + locked.size),
                        style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = TextSecondary)
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C26))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { i, label ->
                    val selected = i == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) AccentGreen else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = if (selected) Color.Black else TextMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Grid — 3 columns
            var selectedAchievement by remember { mutableStateOf<Pair<Achievement, Boolean>?>(null) }

            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                modifier              = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding        = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
            ) {
                items(displayed) { (achievement, isUnlocked) ->
                    AchievementBadge(
                        achievement = achievement,
                        isUnlocked  = isUnlocked,
                        progress    = if (isUnlocked) null else progressFor(achievement),
                        onClick     = { selectedAchievement = achievement to isUnlocked },
                    )
                }
            }

            selectedAchievement?.let { (ach, unlocked) ->
                AchievementDetailDialog(
                    achievement = ach,
                    isUnlocked  = unlocked,
                    isClaiming  = false,
                    onClaim     = null,
                    onDismiss   = { selectedAchievement = null },
                )
            }
        }
    }
}

// ── Achievement detail dialog ─────────────────────────────────────────────────

@Composable
private fun AchievementDetailDialog(
    achievement : Achievement,
    isUnlocked  : Boolean,
    isClaiming  : Boolean,
    onClaim     : (() -> Unit)?,
    onDismiss   : () -> Unit,
) {
    val conditionText = when (val c = achievement.condition) {
        is AchievementCondition.TotalReps     -> "Reach ${formatLarge(c.count)} total push-ups"
        is AchievementCondition.Streak        -> "Maintain a ${c.days}-day workout streak"
        is AchievementCondition.FriendsCount  -> "Add ${c.count} friend${if (c.count > 1) "s" else ""}"
        is AchievementCondition.SingleSession -> "Do ${formatLarge(c.count)} reps in a single session"
    }
    val iconTint = achievementColor(achievement.condition)

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Game card hero ────────────────────────────────────────────────
            AchievementCard(
                achievement = achievement,
                isUnlocked  = isUnlocked,
                isClaimed   = !isClaiming,
                onClaim     = null,   // claim handled by button below
                onClick     = null,
                modifier    = Modifier.width(140.dp),
            )

            // ── Text details ──────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    achievement.description,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))

                // Condition row
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = iconTint, modifier = Modifier.size(15.dp))
                    Text(conditionText, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }

                // Star reward row
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(15.dp))
                    Text("+${achievement.starReward} stars on unlock", style = MaterialTheme.typography.bodySmall, color = StarColor)
                }

                // Tag row
                achievement.tag?.let { tag ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Tag, null, tint = AccentGreen, modifier = Modifier.size(15.dp))
                        Text("Unlocks tag $tag", style = MaterialTheme.typography.bodySmall, color = AccentGreen)
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            if (isClaiming && onClaim != null) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Later", color = TextMuted)
                    }
                    Button(
                        onClick  = onClaim,
                        modifier = Modifier.weight(2f),
                        colors   = ButtonDefaults.buttonColors(containerColor = StarColor),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Claim +${achievement.starReward}", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = TextMuted)
                }
            }
        }
    }
}

// ── Rules dialog ──────────────────────────────────────────────────────────────

@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceGlass)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("How It Works", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = TextSecondary) }
            }

            listOf(
                RulesEntry(Icons.Rounded.Star, StarColor, "Stars",
                    "Every push-up earns 1 star. Consecutive days add a streak bonus (+1 per extra day). " +
                    "Keep reps flowing with ≤3 s between each one for a combo multiplier: ×2 at 20 reps, ×3 at 30, up to ×6 at 60+."),
                RulesEntry(Icons.Rounded.Warning, ErrorRed, "Inactivity Penalty",
                    "Skipping a day deducts ${RankSystem.PENALTY_PER_MISSED_DAY} stars for every full missed day. " +
                    "A gap of 7 or more days adds an extra −${RankSystem.PENALTY_EXTRA_WEEK} stars on top. Stars never drop below zero."),
                RulesEntry(Icons.Rounded.EmojiEvents, Color(0xFFFFCA28), "Rankings",
                    "Your rank is determined by your total stars across 16 tiers: Recruit I–III → Soldier I–III → Warrior I–III → Champion I–III → Legend I–III → Immortal (200,000+)."),
                RulesEntry(Icons.Rounded.Whatshot, Color(0xFFF44336), "Streaks",
                    "A streak counts consecutive days with at least one push-up. Missing a day resets it to zero. Your longest streak is saved permanently."),
                RulesEntry(Icons.Rounded.FitnessCenter, AccentGreen, "Challenges",
                    "Activate a challenge to start tracking. Complete it within the time window to earn bonus stars. You can only have one active instance per challenge."),
                RulesEntry(Icons.Rounded.AutoAwesome, Color(0xFF7C4DFF), "Advancements",
                    "Automatic milestones unlocked by hitting lifetime totals — reps, streak days, single-session bests, or friends. They award bonus stars once and remain as permanent badges."),
                RulesEntry(Icons.Rounded.LocalOffer, AccentGreen, "Profile Tags",
                    "Many achievements unlock a short identity tag (e.g. #IronMan). Visit your Profile to equip up to 3 tags — they appear on your public card visible to friends and on the leaderboard."),
                RulesEntry(Icons.Rounded.Group, Color(0xFF2196F3), "Friends & Leaderboard",
                    "Add friends by username. Compare star counts on the leaderboard, filtered by today, this week, this month, or all time."),
            ).forEach { entry ->
                RulesSectionRow(entry)
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), shape = RoundedCornerShape(12.dp)) {
                Text("Got it", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class RulesEntry(val icon: ImageVector, val tint: Color, val title: String, val body: String)

@Composable
private fun RulesSectionRow(entry: RulesEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(entry.icon, null, tint = entry.tint, modifier = Modifier.size(18.dp))
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Text(entry.body, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
    }
}

// ── Generic edit dialog ───────────────────────────────────────────────────────

@Composable
private fun EditDialog(title: String, value: String, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, numeric: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text  = {
            OutlinedTextField(
                value         = value,
                onValueChange = onChange,
                singleLine    = true,
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentGreen, unfocusedBorderColor = GlassBorder,
                    cursorColor          = AccentGreen, focusedTextColor     = TextPrimary, unfocusedTextColor = TextPrimary,
                ),
            )
        },
        confirmButton   = { TextButton(onClick = onConfirm, enabled = value.isNotBlank() || numeric) { Text("Save", color = AccentGreen, fontWeight = FontWeight.Bold) } },
        dismissButton   = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}

// ── Recent activity section ───────────────────────────────────────────────────

private val SESSION_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val SESSION_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun RecentActivitySection(
    sessions       : List<WorkoutSession>,
    onSessionClick : (WorkoutSession) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val displayed = if (showAll) sessions else sessions.take(5)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                "Recent Activity",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
            )
            if (sessions.size > 5) {
                TextButton(
                    onClick        = { showAll = !showAll },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (showAll) "Show less" else "See all (${sessions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentGreen,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
                        null,
                        tint     = AccentGreen,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        displayed.forEach { session ->
            SessionCard(session = session, onClick = { onSessionClick(session) })
        }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession, onClick: () -> Unit) {
    val zone    = ZoneId.systemDefault()
    val local   = session.startedAt.atZone(zone).toLocalDateTime()
    val dateStr = local.format(SESSION_DATE_FMT)
    val timeStr = local.format(SESSION_TIME_FMT)

    val durationStr = formatDuration(session.durationSeconds)
    val paceStr     = "%.0f rpm".format(session.avgPace)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Icon column
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGreen.copy(alpha = 0.12f))
                .border(1.dp, AccentGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
        }

        // Centre: date + stats
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dateStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("·", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                if (session.isPersonalBest) {
                    Surface(shape = RoundedCornerShape(50), color = StarColor.copy(alpha = 0.15f)) {
                        Text(
                            "PB",
                            color      = StarColor,
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(Icons.Rounded.Repeat, "${session.reps} reps")
                StatPill(Icons.Rounded.Timer, durationStr)
                StatPill(Icons.Rounded.Speed, paceStr)
            }
        }

        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(11.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ── Session detail dialog ─────────────────────────────────────────────────────

@Composable
private fun SessionDetailDialog(session: WorkoutSession, onDismiss: () -> Unit) {
    val zone    = ZoneId.systemDefault()
    val local   = session.startedAt.atZone(zone).toLocalDateTime()
    val dateStr = local.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
    val timeStr = local.format(DateTimeFormatter.ofPattern("HH:mm"))

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Session Details",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = TextPrimary,
                    )
                    Text(
                        "$dateStr · $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = TextSecondary)
                }
            }

            // Personal best banner
            if (session.isPersonalBest) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StarColor.copy(alpha = 0.12f))
                        .border(1.dp, StarColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Star, null, tint = StarColor, modifier = Modifier.size(18.dp))
                    Text(
                        "Personal Best — all-time record for a single session!",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = StarColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Stat grid (2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCard(
                        icon    = Icons.Rounded.FitnessCenter,
                        tint    = AccentGreen,
                        label   = "Push-ups",
                        value   = session.reps.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    DetailStatCard(
                        icon    = Icons.Rounded.Timer,
                        tint    = Color(0xFF818CF8),
                        label   = "Duration",
                        value   = formatDuration(session.durationSeconds),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCard(
                        icon    = Icons.Rounded.Speed,
                        tint    = Color(0xFFFFC107),
                        label   = "Avg Pace",
                        value   = "%.1f rpm".format(session.avgPace),
                        modifier = Modifier.weight(1f),
                    )
                    DetailStatCard(
                        icon    = Icons.Rounded.TrendingUp,
                        tint    = Color(0xFFF44336),
                        label   = "Total / min",
                        value   = if (session.durationSeconds > 0) "%.1f".format(session.reps * 60f / session.durationSeconds) else "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Close button
            Button(
                onClick        = onDismiss,
                modifier       = Modifier.fillMaxWidth(),
                colors         = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape          = RoundedCornerShape(12.dp),
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailStatCard(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    tint     : Color,
    label    : String,
    value    : String,
    modifier : Modifier = Modifier,
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlass)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun achievementIcon(condition: AchievementCondition): ImageVector = when (condition) {
    is AchievementCondition.TotalReps     -> Icons.Rounded.FitnessCenter
    is AchievementCondition.Streak        -> Icons.Rounded.Whatshot
    is AchievementCondition.FriendsCount  -> Icons.Rounded.Group
    is AchievementCondition.SingleSession -> Icons.Rounded.Bolt
}

private fun achievementColor(condition: AchievementCondition): Color = when (condition) {
    is AchievementCondition.TotalReps     -> AccentGreen
    is AchievementCondition.Streak        -> Color(0xFFF44336)
    is AchievementCondition.FriendsCount  -> Color(0xFF2196F3)
    is AchievementCondition.SingleSession -> Color(0xFFFFAB40)  // orange-amber for session bests
}

private fun formatLarge(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> "${n / 1_000}K"
    else           -> n.toString()
}

// ── Photo options bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoOptionsSheet(
    hasPhoto     : Boolean,
    onChangePic  : () -> Unit,
    onUseInitial : () -> Unit,
    onDismiss    : () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest     = onDismiss,
        sheetState           = sheetState,
        containerColor       = SurfaceGlass,
        scrimColor           = Color.Black.copy(alpha = 0.55f),
        dragHandle           = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GlassBorder),
            )
        },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Profile Photo",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            HorizontalDivider(color = GlassBorder)

            // Upload a photo
            Button(
                onClick  = onChangePic,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (hasPhoto) "Change Photo" else "Choose Photo",
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.bodyLarge,
                )
            }

            // Use letter avatar (shown only when a photo is currently set)
            if (hasPhoto) {
                OutlinedButton(
                    onClick  = onUseInitial,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                ) {
                    Icon(Icons.Rounded.PersonOutline, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Use Letter Avatar",
                        fontWeight = FontWeight.SemiBold,
                        style      = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Cancel
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                border   = BorderStroke(1.dp, GlassBorder),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
