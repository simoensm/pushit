package com.pushitup.feature.workout.ui

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.MobileOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pushitup.R
import com.pushitup.core.theme.*
import com.pushitup.core.ui.glassBanner
import com.pushitup.core.ui.glassCard
import com.pushitup.domain.model.PushUpVariant
import com.pushitup.feature.workout.data.PoseAnalyzer
import com.pushitup.feature.workout.domain.PushUpState
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WorkoutScreen(
    onSessionEnd : () -> Unit,
    viewModel    : WorkoutViewModel = hiltViewModel(),
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    when {
        cameraPermission.status.isGranted       ->
            WorkoutContent(viewModel = viewModel, onSessionEnd = onSessionEnd)
        cameraPermission.status.shouldShowRationale ->
            PermissionScaffold(
                icon       = Icons.Rounded.Lock,
                title      = stringResource(R.string.workout_permission_needed),
                body       = stringResource(R.string.workout_camera_rationale),
                buttonText = stringResource(R.string.workout_allow_camera),
                onRequest  = { cameraPermission.launchPermissionRequest() },
            )
        else ->
            PermissionScaffold(
                icon       = Icons.Rounded.CameraAlt,
                title      = stringResource(R.string.workout_camera_required),
                body       = stringResource(R.string.workout_camera_rationale),
                buttonText = stringResource(R.string.workout_grant_permission),
                onRequest  = { cameraPermission.launchPermissionRequest() },
            )
    }
}

// ── Permission screen ─────────────────────────────────────────────────────────

@Composable
private fun PermissionScaffold(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    title      : String,
    body       : String,
    buttonText : String,
    onRequest  : () -> Unit,
) {
    Box(
        modifier         = Modifier.fillMaxSize().background(BackgroundDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            // Icon illustration — replaces emoji with a clean circular badge
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = AccentGreen,
                    modifier           = Modifier.size(46.dp),
                )
            }

            Text(
                title,
                style     = MaterialTheme.typography.headlineMedium,
                color     = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                body,
                style     = MaterialTheme.typography.bodyLarge,
                color     = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )

            Button(
                onClick  = onRequest,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape    = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    buttonText,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black,
                    style      = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// ── Main workout content ──────────────────────────────────────────────────────

@Composable
private fun WorkoutContent(viewModel: WorkoutViewModel, onSessionEnd: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back immediately when a zero-rep session is ended (nothing to save)
    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onSessionEnd() }
    }

    // Latch bad-form and wrong-variant warnings for at least 2 seconds so the
    // user has time to read them before they disappear.
    var latchedBadForm    by remember { mutableStateOf(false) }
    var latchedWrongVariant by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.badFormWarning) {
        if (uiState.badFormWarning) {
            latchedBadForm = true
            kotlinx.coroutines.delay(2000)
            latchedBadForm = false
        }
    }
    LaunchedEffect(uiState.wrongVariantWarning) {
        if (uiState.wrongVariantWarning) {
            latchedWrongVariant = true
            kotlinx.coroutines.delay(2000)
            latchedWrongVariant = false
        }
    }

    if (uiState.phase == WorkoutPhase.ENDED) {
        // Show celebration overlay first if there are pending rewards
        if (uiState.pendingRewards.isNotEmpty()) {
            PostWorkoutCelebrationOverlay(
                rewards   = uiState.pendingRewards,
                onDismiss = { viewModel.dismissRewards() },
            )
            return
        }
        uiState.sessionSummary?.let { summary ->
            SessionSummaryScreen(summary = summary, onDone = onSessionEnd)
            return
        }
    }

    val currentLandmarks by viewModel.landmarks.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera preview + ML Kit ───────────────────────────────────────────
        CameraPreviewWithAnalysis(analyzer = viewModel.poseAnalyzer)

        // ── Skeleton overlay ──────────────────────────────────────────────────
        SkeletonOverlay(landmarks = currentLandmarks, badForm = uiState.badFormWarning)

        // ── Gradient scrims for HUD legibility ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.78f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.90f))))
        )

        // ── Top HUD (hidden during IDLE — nothing meaningful to show yet) ──────
        if (uiState.phase != WorkoutPhase.IDLE) {
            TopHud(
                elapsedSeconds = uiState.elapsedSeconds,
                pushUpState    = uiState.pushUpState,
                modifier       = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 52.dp, start = 24.dp, end = 24.dp),
            )
        }

        // ── Calibration or rep counter (hidden during IDLE) ───────────────────
        if (uiState.phase != WorkoutPhase.IDLE) {
            if (uiState.pushUpState == PushUpState.CALIBRATING && uiState.phase == WorkoutPhase.ACTIVE) {
                CalibrationOverlay(
                    progress = uiState.calibProgress,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                RepCounter(
                    repCount = uiState.repCount,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // ── Combo multiplier badge ────────────────────────────────────────────
        if (uiState.comboMultiplier > 1 && uiState.phase == WorkoutPhase.ACTIVE) {
            ComboBadge(
                multiplier      = uiState.comboMultiplier,
                consecutiveReps = uiState.consecutiveReps,
                modifier        = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 190.dp),
            )
        }

        // ── Banners (phone moving > wrong variant > bad form) ────────────────
        if (uiState.phoneMovingWarning && uiState.phase == WorkoutPhase.ACTIVE) {
            PhoneMovingBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 136.dp, start = 24.dp, end = 24.dp),
            )
        } else if (latchedWrongVariant && uiState.phase == WorkoutPhase.ACTIVE) {
            WrongVariantBanner(
                variant  = uiState.selectedVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 136.dp, start = 24.dp, end = 24.dp),
            )
        } else if (latchedBadForm && uiState.phase == WorkoutPhase.ACTIVE) {
            BadFormBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 136.dp, start = 24.dp, end = 24.dp),
            )
        }

        // ── Variant picker (IDLE) ─────────────────────────────────────────────
        if (uiState.phase == WorkoutPhase.IDLE) {
            VariantPickerOverlay(
                selected  = uiState.selectedVariant,
                onSelect  = viewModel::selectVariant,
                onStart   = viewModel::startSession,
                onCancel  = onSessionEnd,
                modifier  = Modifier.align(Alignment.BottomCenter),
            )
        }

        // ── Variant badge (ACTIVE / PAUSED) ───────────────────────────────────
        if (uiState.phase == WorkoutPhase.ACTIVE || uiState.phase == WorkoutPhase.PAUSED) {
            VariantBadge(
                variant  = uiState.selectedVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 20.dp),
            )
        }

        // ── Bottom controls ───────────────────────────────────────────────────
        if (uiState.phase != WorkoutPhase.IDLE) {
            BottomControls(
                phase    = uiState.phase,
                onStart  = viewModel::startSession,
                onPause  = viewModel::pauseSession,
                onResume = viewModel::resumeSession,
                onEnd    = viewModel::endSession,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

// ── CameraX wiring ────────────────────────────────────────────────────────────

@Composable
private fun CameraPreviewWithAnalysis(analyzer: PoseAnalyzer?) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()

        val provider = ProcessCameraProvider.getInstance(context).await()

        val preview = Preview.Builder().build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { ia -> if (analyzer != null) ia.setAnalyzer(executor, analyzer) }

        provider.unbindAll()
        try {
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis,
            )
        } catch (e: Exception) {
            android.util.Log.e("WorkoutScreen", "Camera bind failed", e)
        }

        try { kotlinx.coroutines.awaitCancellation() }
        finally {
            provider.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

// ── Calibration overlay ───────────────────────────────────────────────────────

@Composable
private fun CalibrationOverlay(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(300),
        label         = "calib_progress",
    )

    Column(
        modifier            = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Icon replaces the 🎯 emoji
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.Adjust,
                contentDescription = null,
                tint               = AccentGreen,
                modifier           = Modifier.size(28.dp),
            )
        }

        Text(
            text       = "Calibrating",
            style      = MaterialTheme.typography.headlineMedium,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text      = "Do 1–2 slow push-ups so the app\nlearns your range of motion",
            style     = MaterialTheme.typography.bodyLarge,
            color     = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress  = { animatedProgress },
            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color     = AccentGreen,
            trackColor = TextMuted.copy(alpha = 0.18f),
        )
        Text(
            text  = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = AccentGreen,
        )
    }
}

// ── Top HUD ───────────────────────────────────────────────────────────────────

@Composable
private fun TopHud(
    elapsedSeconds : Long,
    pushUpState    : PushUpState,
    modifier       : Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        Text(
            text       = "%02d:%02d".format(minutes, seconds),
            style      = MaterialTheme.typography.headlineMedium,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
        )

        val upLabel    = stringResource(R.string.workout_state_up)
        val downLabel  = stringResource(R.string.workout_state_down)
        val calibLabel = stringResource(R.string.workout_state_calibrating)
        val readyLabel = stringResource(R.string.workout_state_ready)
        val (stateLabel, stateColor) = when (pushUpState) {
            PushUpState.UP          -> upLabel    to AccentGreen
            PushUpState.DOWN        -> downLabel  to AccentGreenDark
            PushUpState.CALIBRATING -> calibLabel to WarningAmber
            PushUpState.IDLE        -> readyLabel to TextMuted
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = stateColor.copy(alpha = 0.20f),
        ) {
            Text(
                text       = stateLabel,
                color      = stateColor,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// ── Warning banners ───────────────────────────────────────────────────────────

@Composable
private fun WrongVariantBanner(
    variant: com.pushitup.domain.model.PushUpVariant,
    modifier: Modifier = Modifier,
) {
    val hint = when (variant) {
        com.pushitup.domain.model.PushUpVariant.DIAMS      -> "Rapprochez vos mains en triangle sous la poitrine"
        com.pushitup.domain.model.PushUpVariant.ECARTEEES  -> "Écartez vos mains bien au-delà des épaules"
        com.pushitup.domain.model.PushUpVariant.MILITAIRE  -> "Placez vos mains dans l'axe des épaules"
        else -> "Position des mains incorrecte"
    }
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .glassBanner(tintColor = WarningAmber)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Rounded.PanTool,
            contentDescription = null,
            tint               = WarningAmber,
            modifier           = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text       = hint,
            color      = WarningAmber,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BadFormBanner(modifier: Modifier = Modifier) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .glassBanner(tintColor = WarningAmber)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Rounded.Warning,
            contentDescription = null,
            tint               = WarningAmber,
            modifier           = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text       = stringResource(R.string.workout_bad_form),
            color      = WarningAmber,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PhoneMovingBanner(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .glassBanner(tintColor = ErrorRed)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.MobileOff,
                contentDescription = null,
                tint               = ErrorRed,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text       = stringResource(R.string.workout_counting_paused),
                color      = ErrorRed,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text  = stringResource(R.string.workout_phone_moving),
            color = ErrorRed.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ── Animated rep counter ──────────────────────────────────────────────────────

@Composable
private fun RepCounter(repCount: Int, modifier: Modifier = Modifier) {
    // Scale bounce (~15% overshoot) + glow flash on every counted rep (game-brief spec).
    val bump = remember { Animatable(1f) }
    LaunchedEffect(repCount) {
        if (repCount > 0) {
            bump.snapTo(1.15f)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    val glowAlpha = ((bump.value - 1f) / 0.15f).coerceIn(0f, 1f)

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Radial glow pulse behind the number
            Canvas(Modifier.matchParentSize()) {
                if (glowAlpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentGreen.copy(alpha = glowAlpha * 0.40f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.75f,
                        ),
                        radius = size.maxDimension * 0.75f,
                        center = center,
                    )
                }
            }
            AnimatedContent(
                targetState  = repCount,
                transitionSpec = {
                    val goingUp = targetState > initialState
                    (slideInVertically(
                        animationSpec = tween(220, easing = EaseOutCubic),
                    ) { if (goingUp) it else -it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(180, easing = EaseInCubic),
                    ) { if (goingUp) -it else it } + fadeOut(tween(120)))
                },
                label = "repCounter",
            ) { count ->
                Text(
                    text       = count.toString(),
                    modifier   = Modifier.scale(bump.value),
                    style      = MaterialTheme.typography.displayLarge,
                    color      = AccentGreen,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            text          = stringResource(R.string.workout_reps),
            style         = MaterialTheme.typography.labelLarge,
            color         = AccentGreen,
            letterSpacing = 4.sp,
        )
    }
}

// ── Combo multiplier badge ────────────────────────────────────────────────────

@Composable
private fun ComboBadge(
    multiplier      : Int,
    consecutiveReps : Int,
    modifier        : Modifier = Modifier,
) {
    // Colour ramp: green → amber → deep orange → red as multiplier grows
    val badgeColor = when (multiplier) {
        2    -> AccentGreen
        3    -> Color(0xFFFFA726)   // amber
        4    -> Color(0xFFFF7043)   // deep orange
        else -> Color(0xFFE53935)   // red (5×+)
    }

    // Pop-in bounce when the multiplier level changes
    val pop = remember { Animatable(1f) }
    LaunchedEffect(multiplier) {
        pop.snapTo(1.25f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Row(
        modifier              = modifier
            .scale(pop.value)
            .clip(RoundedCornerShape(50))
            .background(badgeColor.copy(alpha = 0.20f))
            .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.MilitaryTech,
            contentDescription = null,
            tint   = badgeColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text       = "×$multiplier COMBO",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color      = badgeColor,
            letterSpacing = 1.5.sp,
        )
        Text(
            text  = "$consecutiveReps",
            style = MaterialTheme.typography.labelMedium,
            color = badgeColor.copy(alpha = 0.7f),
        )
    }
}

// ── Bottom controls ───────────────────────────────────────────────────────────

@Composable
private fun BottomControls(
    phase    : WorkoutPhase,
    onStart  : () -> Unit,
    onPause  : () -> Unit,
    onResume : () -> Unit,
    onEnd    : () -> Unit,
    modifier : Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        when (phase) {
            WorkoutPhase.IDLE -> { /* picker overlay handles start */ }

            WorkoutPhase.ACTIVE -> {
                SmallControlButton(Icons.Rounded.Close,    stringResource(R.string.workout_end),    ErrorRed,    onEnd)
                SmallControlButton(Icons.Rounded.Pause,    stringResource(R.string.workout_pause),  AccentGreen, onPause)
            }

            WorkoutPhase.PAUSED -> {
                SmallControlButton(Icons.Rounded.Close,     stringResource(R.string.workout_end),    ErrorRed,    onEnd)
                SmallControlButton(Icons.Rounded.PlayArrow, stringResource(R.string.workout_resume), AccentGreen, onResume)
            }

            WorkoutPhase.ENDED -> { /* handled by summary screen */ }
        }
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    Button(
        onClick  = onClick,
        colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        shape    = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .height(64.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint     = Color.Black,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Start Workout",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = Color.Black,
        )
    }
}

@Composable
private fun SmallControlButton(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    color   : Color,
    onClick : () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier         = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onClick) {
                Icon(icon, label, tint = color, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = color.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
    }
}

// ── Session summary ───────────────────────────────────────────────────────────

@Composable
private fun SessionSummaryScreen(summary: SessionSummary, onDone: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(BackgroundDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (summary.isPersonalBest) {
                // Trophy icon replaces the 🏆 emoji
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(StarColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint               = StarColor,
                        modifier           = Modifier.size(36.dp),
                    )
                }
            }

            Text(
                text       = if (summary.isPersonalBest) stringResource(R.string.workout_personal_best) else stringResource(R.string.workout_session_complete),
                style      = MaterialTheme.typography.headlineLarge,
                color      = if (summary.isPersonalBest) StarColor else TextPrimary,
                textAlign  = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            // Stats card
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StatRow(stringResource(R.string.workout_total_reps), summary.totalReps.toString())
                StatRow(stringResource(R.string.workout_duration),   "%02d:%02d".format(
                    summary.durationSeconds / 60, summary.durationSeconds % 60))
                StatRow(stringResource(R.string.workout_avg_pace),   "%.1f rpm".format(summary.avgPace))
                if (summary.starsEarned > 0) {
                    StatRow(stringResource(R.string.workout_stars_earned), "+${summary.starsEarned} ★", StarColor)
                }
                if (summary.comboBonus > 0) {
                    StatRow(
                        label      = stringResource(R.string.workout_combo_bonus),
                        value      = "+${summary.comboBonus} ★",
                        valueColor = Color(0xFFFFA726),
                    )
                }
            }

            if (summary.isPersonalBest) {
                Row(
                    modifier            = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StarColor.copy(alpha = 0.12f))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.MilitaryTech,
                        contentDescription = null,
                        tint               = StarColor,
                        modifier           = Modifier.size(18.dp),
                    )
                    Text(
                        text       = "Personal Best — ${summary.totalReps} reps",
                        color      = StarColor,
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Button(
                onClick  = onDone,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape    = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    stringResource(R.string.workout_back_dashboard),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            color      = valueColor,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Variant picker overlay (shown in IDLE state) ──────────────────────────────

@Composable
private fun VariantPickerOverlay(
    selected : PushUpVariant,
    onSelect : (PushUpVariant) -> Unit,
    onStart  : () -> Unit,
    onCancel : () -> Unit,
    modifier : Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.97f))),
            )
            .padding(top = 24.dp, bottom = 48.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            stringResource(R.string.workout_type_title),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = Color.White,
            modifier   = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )

        // Drum-roll wheel picker
        VariantWheelPicker(
            variants = PushUpVariant.entries,
            selected = selected,
            onSelect = onSelect,
        )

        Spacer(Modifier.height(14.dp))

        // ── Setup tip ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint     = AccentGreen,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Place your phone ~30 cm away on the floor, facing you",
                style  = MaterialTheme.typography.bodySmall,
                color  = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Cancel + Start row ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick  = onCancel,
                shape    = RoundedCornerShape(50),
                modifier = Modifier.weight(1f).height(60.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Button(
                onClick   = onStart,
                colors    = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape     = RoundedCornerShape(50),
                modifier  = Modifier.weight(2f).height(60.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.workout_begin),
                    color      = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp,
                )
            }
        }
    }
}

// ── Drum-roll wheel picker ────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VariantWheelPicker(
    variants : List<PushUpVariant>,
    selected : PushUpVariant,
    onSelect : (PushUpVariant) -> Unit,
    modifier : Modifier = Modifier,
) {
    val itemHeightDp  = 64.dp
    val visibleItems  = 3

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = variants.indexOf(selected).coerceAtLeast(0),
    )
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // Derive selected from center position
    val centeredIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(centeredIndex) {
        val idx = centeredIndex.coerceIn(variants.indices)
        if (variants[idx] != selected) onSelect(variants[idx])
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeightDp * visibleItems),
    ) {
        LazyColumn(
            state             = listState,
            flingBehavior     = fling,
            contentPadding    = PaddingValues(vertical = itemHeightDp), // allows first/last to reach center
            modifier          = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(variants) { index, variant ->
                val isCenter = index == centeredIndex
                VariantWheelItem(
                    variant    = variant,
                    isSelected = isCenter,
                    modifier   = Modifier.height(itemHeightDp),
                )
            }
        }

        // Subtle divider lines above and below the selected center item
        HorizontalDivider(
            modifier  = Modifier.align(Alignment.TopCenter).padding(top = itemHeightDp),
            color     = Color.White.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
        HorizontalDivider(
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = itemHeightDp),
            color     = Color.White.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun VariantWheelItem(
    variant    : PushUpVariant,
    isSelected : Boolean,
    modifier   : Modifier = Modifier,
) {
    val alpha = if (isSelected) 1f else 0.35f
    val multiplierColor = when {
        variant.starMultiplier >= 2.0f -> Color(0xFFFF6B6B)
        variant.starMultiplier >= 1.5f -> Color(0xFFF97316)
        variant.starMultiplier >= 1.3f -> Color(0xFFFFD700)
        else                           -> Color.White.copy(alpha = 0.5f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Difficulty dots
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < variant.difficultyStars)
                                AccentGreen.copy(alpha = alpha)
                            else
                                Color.White.copy(alpha = 0.18f * alpha)
                        ),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                variant.localizedName(),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color      = if (isSelected) AccentGreen else Color.White.copy(alpha = alpha),
            )
            if (isSelected) {
                Text(
                    variant.localizedDesc(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        // Multiplier chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(multiplierColor.copy(alpha = 0.15f * alpha))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                "×${"%.1f".format(variant.starMultiplier)}",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = multiplierColor.copy(alpha = alpha),
            )
        }
    }
}

// ── Variant badge (shown during ACTIVE/PAUSED) ────────────────────────────────

@Composable
private fun VariantBadge(
    variant  : PushUpVariant,
    modifier : Modifier = Modifier,
) {
    if (variant == PushUpVariant.NORMAL) return      // no badge for the default
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            variant.localizedName(),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = AccentGreen,
        )
    }
}

// ── Variant localization helpers ──────────────────────────────────────────────

@Composable
private fun PushUpVariant.localizedName(): String = stringResource(
    when (this) {
        PushUpVariant.NORMAL    -> R.string.variant_normal_name
        PushUpVariant.MILITAIRE -> R.string.variant_military_name
        PushUpVariant.ECARTEEES -> R.string.variant_wide_name
        PushUpVariant.DIAMS     -> R.string.variant_diamond_name
        PushUpVariant.PIQUEES   -> R.string.variant_pike_name
        PushUpVariant.UNE_MAIN  -> R.string.variant_one_arm_name
    }
)

@Composable
private fun PushUpVariant.localizedDesc(): String = stringResource(
    when (this) {
        PushUpVariant.NORMAL    -> R.string.variant_normal_desc
        PushUpVariant.MILITAIRE -> R.string.variant_military_desc
        PushUpVariant.ECARTEEES -> R.string.variant_wide_desc
        PushUpVariant.DIAMS     -> R.string.variant_diamond_desc
        PushUpVariant.PIQUEES   -> R.string.variant_pike_desc
        PushUpVariant.UNE_MAIN  -> R.string.variant_one_arm_desc
    }
)
