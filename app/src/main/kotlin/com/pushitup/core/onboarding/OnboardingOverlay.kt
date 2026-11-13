package com.pushitup.core.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.pushitup.R
import com.pushitup.core.navigation.Screen
import com.pushitup.core.theme.*
import kotlinx.coroutines.delay

private val MASCOT_W  = 180.dp
private val MASCOT_VH = 155.dp

@Composable
fun OnboardingOverlay(
    navController: NavHostController,
    viewModel    : OnboardingViewModel = hiltViewModel(),
) {
    val isComplete by viewModel.isComplete.collectAsState()
    val stepIndex  by viewModel.stepIndex.collectAsState()

    if (isComplete != false) return

    val step = ALL_TOUR_STEPS.getOrNull(stepIndex) ?: return

    // ── Navigate when step targets a new screen ───────────────────────────────
    LaunchedEffect(step.id) {
        step.targetRoute?.let { route ->
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    // ── Per-step entrance flag ────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(step.id) {
        visible = false
        delay(120)
        visible = true
    }

    // ── Mascot entry animation ────────────────────────────────────────────────
    val mascotEntryDp by animateDpAsState(
        targetValue   = if (visible) 0.dp else MASCOT_VH,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label         = "mascot_entry",
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // 0. Input blocker — the tour is modal. Without this, taps fall through
        //    the scrim to the screen underneath (incl. the bottom nav bar), which
        //    can change routes mid-tour and desync or kill the guide.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {},
                )
        )

        // 1. Scrim + rounded-corner spotlight ──────────────────────────────────
        SpotlightScrim(step = step)

        // 2. Skip button ───────────────────────────────────────────────────────
        TextButton(
            onClick  = viewModel::skip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 4.dp, top = 4.dp),
        ) {
            Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.tour_skip),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // 3. Step progress dots ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            ALL_TOUR_STEPS.forEachIndexed { i, _ ->
                Box(
                    modifier = Modifier
                        .size(if (i == stepIndex) 8.dp else 5.dp)
                        .background(
                            color = if (i == stepIndex) AccentGreen else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        )
                )
            }
        }

        // 4. Speech bubble — drawn BEHIND Leo ──────────────────────────────────
        val bubbleStart: Dp = if (step.mascotOnRight) 8.dp else (MASCOT_W - 30.dp)
        val bubbleEnd  : Dp = if (step.mascotOnRight) (MASCOT_W - 30.dp) else 8.dp

        AnimatedVisibility(
            visible  = visible,
            enter    = fadeIn(tween(250)) + slideInVertically(tween(280)) { it / 5 },
            exit     = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = bubbleStart, end = bubbleEnd)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
        ) {
            SpeechBubble(
                step       = step,
                onNext     = viewModel::advance,
                tailOnLeft = !step.mascotOnRight,
            )
        }

        // 5. Leo — drawn ON TOP of the bubble ──────────────────────────────────
        val mascotAlignment = if (step.mascotOnRight) Alignment.BottomEnd else Alignment.BottomStart

        Box(
            modifier = Modifier
                .align(mascotAlignment)
                .size(width = MASCOT_W, height = MASCOT_VH)
                .clipToBounds()
                .offset(y = mascotEntryDp),
        ) {
            Image(
                painter            = painterResource(step.mascotRes),
                contentDescription = null,
                contentScale       = ContentScale.FillWidth,
                alignment          = Alignment.TopCenter,
                modifier           = Modifier
                    .width(MASCOT_W)
                    .height(290.dp)
                    .graphicsLayer {
                        scaleX = if (step.mascotOnRight) -1f else 1f
                    },
            )
        }
    }
}

// ── Scrim with true rounded-corner spotlight ──────────────────────────────────

@Composable
private fun SpotlightScrim(step: TourStep) {
    val scrimColor = Color.Black.copy(alpha = 0.82f)

    val rect: Rect? = step.target?.let { TourCoordinates.rects[it] }

    if (rect == null) {
        Box(Modifier.fillMaxSize().background(scrimColor))
        return
    }

    val density = LocalDensity.current

    val pulseTransition = rememberInfiniteTransition("pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label         = "pulse",
    )

    with(density) {
        val pad    = 8.dp.toPx()
        val corner = 16.dp.toPx()
        val cutL   = rect.left   - pad
        val cutT   = rect.top    - pad
        val cutR   = rect.right  + pad
        val cutB   = rect.bottom + pad

        Box(Modifier.fillMaxSize()) {

            // Canvas draws the full scrim then cuts a rounded rect hole
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                // 1. Full opaque scrim
                drawRect(scrimColor)
                // 2. Punch a rounded-corner hole — BlendMode.Clear erases those pixels
                drawRoundRect(
                    color        = Color.Transparent,
                    topLeft      = Offset(cutL, cutT),
                    size         = Size(cutR - cutL, cutB - cutT),
                    cornerRadius = CornerRadius(corner),
                    blendMode    = BlendMode.Clear,
                )
            }

            // Pulsing green border around the cutout (same rounded shape)
            Box(
                Modifier
                    .offset(x = cutL.toDp(), y = cutT.toDp())
                    .size(
                        width  = (cutR - cutL).toDp(),
                        height = (cutB - cutT).toDp(),
                    )
                    .border(
                        width  = 2.dp,
                        color  = AccentGreen.copy(alpha = pulseAlpha),
                        shape  = RoundedCornerShape(16.dp),
                    )
            )
        }
    }
}

// ── Speech bubble ─────────────────────────────────────────────────────────────

@Composable
private fun SpeechBubble(
    step      : TourStep,
    onNext    : () -> Unit,
    tailOnLeft: Boolean,
    modifier  : Modifier = Modifier,
) {
    val shape = RoundedCornerShape(
        topStart    = if (tailOnLeft) 4.dp  else 20.dp,
        topEnd      = if (tailOnLeft) 20.dp else 4.dp,
        bottomStart = 20.dp,
        bottomEnd   = 20.dp,
    )

    // The bubble background is always dark — hardcode text colors so they
    // remain readable regardless of the system (light / dark) theme.
    Column(
        modifier = modifier
            .background(Color(0xFF18181F), shape)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState  = step.titleRes,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "bubble_title",
        ) { res ->
            Text(
                stringResource(res),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = Color.White,                       // always white on dark bubble
            )
        }

        Spacer(Modifier.height(5.dp))

        AnimatedContent(
            targetState  = step.bodyRes,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn(tween(200))) togetherWith
                (slideOutHorizontally { -it / 4 } + fadeOut(tween(150)))
            },
            label = "bubble_body",
        ) { res ->
            Text(
                stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),        // always light on dark bubble
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick        = onNext,
            modifier       = Modifier.align(Alignment.End).height(34.dp),
            shape          = RoundedCornerShape(10.dp),
            colors         = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            Text(
                text       = if (step.isLast) stringResource(R.string.tour_done)
                             else             stringResource(R.string.tour_next),
                color      = Color.Black,
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
