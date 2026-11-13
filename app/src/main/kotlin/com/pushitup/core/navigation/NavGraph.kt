package com.pushitup.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pushitup.R
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.pushitup.core.theme.*
import com.pushitup.core.onboarding.OnboardingOverlay
import com.pushitup.core.ui.UpdateBanner
import com.pushitup.core.ui.WhatsNewDialog
import com.pushitup.core.update.AppUpdateViewModel
import com.pushitup.feature.auth.ui.AuthViewModel
import com.pushitup.feature.auth.ui.GoogleProfileSetupScreen
import com.pushitup.feature.auth.ui.LoginScreen
import com.pushitup.feature.auth.ui.SignUpScreen
import com.pushitup.feature.challenges.ui.ChallengesScreen
import com.pushitup.feature.dashboard.ui.DashboardScreen
import com.pushitup.feature.friends.ui.FriendsScreen
import com.pushitup.feature.leaderboard.ui.LeaderboardScreen
import com.pushitup.feature.profile.ui.ProfileScreen
import com.pushitup.feature.profile.ui.ProfileViewModel
import com.pushitup.feature.settings.ui.SettingsScreen
import com.pushitup.feature.workout.ui.WorkoutScreen

sealed class Screen(val route: String) {
    data object Login              : Screen("login")
    data object SignUp             : Screen("signup")
    data object GoogleProfileSetup : Screen("google_profile_setup")
    data object Dashboard          : Screen("dashboard")
    data object Workout            : Screen("workout")
    data object Challenges         : Screen("challenges")
    data object Leaderboard        : Screen("leaderboard")
    data object Friends            : Screen("friends")
    data object Profile            : Screen("profile")
    data object Settings           : Screen("settings")
}

private val BOTTOM_TAB_ROUTES = setOf(
    Screen.Dashboard.route,
    Screen.Challenges.route,
    Screen.Leaderboard.route,
    Screen.Friends.route,
    Screen.Profile.route,
)

@Composable
fun PushItNavGraph(
    navController : NavHostController = rememberNavController(),
    profileVm     : ProfileViewModel  = hiltViewModel(),
    authVm        : AuthViewModel     = hiltViewModel(),
    updateVm      : AppUpdateViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    val startDestination = if (FirebaseAuth.getInstance().currentUser != null)
        Screen.Dashboard.route else Screen.Login.route

    val showBottomBar = currentRoute in BOTTOM_TAB_ROUTES

    // Observe only the fields needed by the nav bar (photo + initial)
    val profileUiState by profileVm.uiState.collectAsState()
    val navPhotoUrl  = profileUiState.profile?.photoDisplayUrl
    val navPhotoKey  = "nav_avatar_${profileUiState.profile?.uid}_${profileUiState.profile?.photoUpdatedAt}"
    // Use Firestore profile name, fall back to Firebase Auth display name / email while loading
    val currentUser  = FirebaseAuth.getInstance().currentUser
    val navInitial   = profileUiState.profile?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: currentUser?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: currentUser?.email?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "?"

    // Onboarding overlay sits above everything; only shown on first launch
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor      = BackgroundDeep,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute    = currentRoute,
                    profilePhotoUrl = navPhotoUrl,
                    profilePhotoKey = navPhotoKey,
                    profileInitial  = navInitial,
                    onHome          = { navController.navigateTab(Screen.Dashboard.route) },
                    onChallenges    = { navController.navigateTab(Screen.Challenges.route) },
                    onLeaderboard   = { navController.navigateTab(Screen.Leaderboard.route) },
                    onFriends       = { navController.navigateTab(Screen.Friends.route) },
                    onProfile       = { navController.navigateTab(Screen.Profile.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(padding),
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToSignUp  = { navController.navigate(Screen.SignUp.route) },
                    onNeedsProfileSetup = {
                        navController.navigate(Screen.GoogleProfileSetup.route) {
                            popUpTo(Screen.Login.route) { inclusive = false }
                        }
                    },
                    onLoginSuccess      = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    viewModel           = authVm,
                )
            }
            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onNavigateToLogin   = { navController.popBackStack() },
                    onNeedsProfileSetup = {
                        navController.navigate(Screen.GoogleProfileSetup.route) {
                            popUpTo(Screen.Login.route) { inclusive = false }
                        }
                    },
                    onSignUpSuccess     = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    viewModel           = authVm,
                )
            }
            composable(Screen.GoogleProfileSetup.route) {
                GoogleProfileSetupScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    viewModel       = authVm,
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onStartWorkout      = { navController.navigate(Screen.Workout.route) { launchSingleTop = true } },
                    onNavigateToProfile = { navController.navigateTab(Screen.Profile.route) },
                )
            }
            composable(Screen.Workout.route) {
                WorkoutScreen(onSessionEnd = { navController.popBackStack() })
            }
            composable(Screen.Challenges.route)  { ChallengesScreen() }
            composable(Screen.Leaderboard.route) { LeaderboardScreen() }
            composable(Screen.Friends.route)     { FriendsScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onSignOut            = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack    = { navController.popBackStack() },
                    onSignOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }

    // Show the guide on ALL main-tab routes — the tour navigates between tabs
    // (Dashboard → Challenges → Leaderboard → Friends → Profile), so mounting it
    // only on the dashboard would kill the tour at the first navigation step.
    // Never shown during auth / profile setup / workout.
    if (currentRoute in BOTTOM_TAB_ROUTES) {
        OnboardingOverlay(navController = navController)
    }

    // ── Update banner + What's New — shown on any main tab, not during auth ──
    if (currentRoute in BOTTOM_TAB_ROUTES) {
        val updateState by updateVm.uiState.collectAsState()

        // What's New dialog — shown once per version on first launch after update
        updateState.whatsNewChangelog?.let { changelog ->
            WhatsNewDialog(
                changelog = changelog,
                onDismiss = { updateVm.dismissWhatsNew() },
            )
        }

        // Update available banner — shown at bottom above the nav bar
        if (updateState.availableVersion != null && !updateState.updateBannerDismissed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                UpdateBanner(
                    versionName = updateState.availableVersion!!.latestVersionName,
                    onDismiss   = { updateVm.dismissUpdateBanner() },
                )
            }
        }
    }
    } // end Box
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Dashboard.route) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}

@Composable
private fun AppBottomBar(
    currentRoute    : String?,
    profilePhotoUrl : String?,
    profilePhotoKey : String,
    profileInitial  : String,
    onHome          : () -> Unit,
    onChallenges    : () -> Unit,
    onLeaderboard   : () -> Unit,
    onFriends       : () -> Unit,
    onProfile       : () -> Unit,
) {
    val border = AccentGreen.copy(alpha = 0.18f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDeep)
            .drawBehind { drawLine(border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        NavBarItem(Icons.Rounded.Home,        stringResource(R.string.nav_home),        currentRoute == Screen.Dashboard.route,   onHome)
        NavBarItem(Icons.Rounded.EmojiEvents, stringResource(R.string.nav_challenges),  currentRoute == Screen.Challenges.route,  onChallenges)
        NavBarItem(Icons.Rounded.Leaderboard, stringResource(R.string.nav_leaderboard), currentRoute == Screen.Leaderboard.route, onLeaderboard)
        NavBarItem(Icons.Rounded.Group,       stringResource(R.string.nav_friends),     currentRoute == Screen.Friends.route,     onFriends)
        ProfileNavBarItem(
            photoUrl  = profilePhotoUrl,
            cacheKey  = profilePhotoKey,
            initial   = profileInitial,
            label     = stringResource(R.string.nav_profile),
            selected  = currentRoute == Screen.Profile.route,
            onClick   = onProfile,
        )
    }
}

@Composable
private fun NavBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) AccentGreen else TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style      = MaterialTheme.typography.labelSmall,
            color      = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Profile tab item — shows the user's photo (or letter initial) instead of a generic icon. */
@Composable
private fun ProfileNavBarItem(
    photoUrl : String?,
    cacheKey : String,
    initial  : String,
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit,
) {
    val ringColor = if (selected) AccentGreen else Color.Transparent
    val labelColor = if (selected) AccentGreen else TextMuted

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(2.dp, ringColor, CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUrl)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .crossfade(true)
                        .build(),
                    contentDescription = label,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    initial,
                    color      = if (selected) AccentGreen else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style      = MaterialTheme.typography.labelSmall,
            color      = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
