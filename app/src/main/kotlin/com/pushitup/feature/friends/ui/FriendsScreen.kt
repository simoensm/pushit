package com.pushitup.feature.friends.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pushitup.R
import com.pushitup.core.onboarding.OnboardingViewModel
import com.pushitup.core.onboarding.TourCoordinates
import com.pushitup.core.onboarding.TourTarget
import com.pushitup.core.theme.*
import com.pushitup.core.ui.PublicProfileDialog
import com.pushitup.core.ui.UserAvatar
import com.pushitup.core.ui.glassCard
import com.pushitup.domain.model.FriendRequest
import com.pushitup.domain.model.UserProfile
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(viewModel: FriendsViewModel = hiltViewModel()) {
    val uiState        by viewModel.uiState.collectAsState()
    val listState       = rememberLazyListState()
    val scope           = rememberCoroutineScope()
    val tourVm: OnboardingViewModel = hiltViewModel()
    val tourComplete by tourVm.isComplete.collectAsState()
    val scrollEnabled = tourComplete != false
    val searchFocus     = remember { FocusRequester() }
    var triggerFocus   by remember { mutableStateOf(false) }
    var viewingProfile by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(uiState.actionSuccess, uiState.actionError) {
        if (uiState.actionSuccess != null || uiState.actionError != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(triggerFocus) {
        if (triggerFocus) {
            listState.animateScrollToItem(0)
            kotlinx.coroutines.delay(150)
            searchFocus.requestFocus()
            triggerFocus = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDeep)) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled   = scrollEnabled,
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { TourCoordinates.register(TourTarget.FRIENDS_HEADER, it) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.friends_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("Find people by their username",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Button(
                        onClick        = { triggerFocus = true },
                        colors         = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape          = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.friends_add), color = Color.Black,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // ── Search ────────────────────────────────────────────────────────
            item {
                SearchSection(
                    state         = uiState,
                    viewModel     = viewModel,
                    focusRequester = searchFocus,
                    onOpenProfile = { viewingProfile = it },
                )
            }

            // ── Friend Requests panel (dedicated card) ────────────────────────
            if (uiState.incomingRequests.isNotEmpty()) {
                item {
                    FriendRequestsPanel(
                        requests  = uiState.incomingRequests,
                        onAccept  = { viewModel.acceptRequest(it) },
                        onDecline = { viewModel.declineRequest(it) },
                    )
                }
            }

            // ── Friends list ──────────────────────────────────────────────────
            if (uiState.friends.isNotEmpty()) {
                item { SectionLabel("My Friends (${uiState.friends.size})") }
                items(uiState.friends, key = { it.uid }) { friend ->
                    FriendCard(
                        profile  = friend,
                        onClick  = {
                            viewingProfile = friend
                            viewModel.loadProfileExtra(friend.uid)
                        },
                        onRemove = { viewModel.removeFriend(friend.uid) },
                    )
                }
            }

            if (uiState.friends.isEmpty() && uiState.incomingRequests.isEmpty() && uiState.searchQuery.isEmpty()) {
                item { EmptyFriendsState(onAddFriend = { triggerFocus = true }) }
            }
        }

        viewingProfile?.let { pub ->
            PublicProfileDialog(
                profile     = pub,
                todayReps   = uiState.viewingProfileTodayReps,
                isFriend    = true,
                onDismiss   = { viewingProfile = null },
            )
        }

        val msg = uiState.actionSuccess ?: uiState.actionError
        AnimatedVisibility(visible = msg != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = if (uiState.actionSuccess != null) AccentGreen else ErrorRed) {
                Text(msg ?: "", color = Color.Black,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SearchSection(
    state          : FriendsUiState,
    viewModel      : FriendsViewModel,
    focusRequester : FocusRequester,
    onOpenProfile  : (UserProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value         = state.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder   = { Text(stringResource(R.string.friends_search_hint), color = TextMuted) },
            leadingIcon   = {
                if (state.isSearching)
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else
                    Icon(Icons.Rounded.Search, null, tint = TextMuted)
            },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {{
                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                    Icon(Icons.Rounded.Close, null, tint = TextMuted)
                }
            }} else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AccentGreen, unfocusedBorderColor = GlassBorder,
                focusedTextColor     = TextPrimary, unfocusedTextColor   = TextPrimary,
                cursorColor          = AccentGreen,
                focusedContainerColor   = SurfaceGlass,
                unfocusedContainerColor = SurfaceGlass,
            ),
            shape    = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )

        if (state.searchResults.isNotEmpty()) {
            Column(modifier = Modifier.glassCard().padding(4.dp)) {
                state.searchResults.forEachIndexed { index, user ->
                    SearchResultRow(
                        user          = user,
                        isFriend      = state.friends.any { it.uid == user.uid },
                        isPending     = user.uid in state.pendingToUids,
                        onAddClick    = { viewModel.sendRequest(user.uid) },
                        onOpenProfile = { onOpenProfile(user) },
                    )
                    if (index < state.searchResults.lastIndex)
                        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                }
            }
        } else if (state.searchQuery.length >= 2 && !state.isSearching) {
            Box(
                modifier         = Modifier.glassCard().fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No users found for \"${state.searchQuery}\"", color = TextMuted)
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    user          : UserProfile,
    isFriend      : Boolean,
    isPending     : Boolean,
    onAddClick    : () -> Unit,
    onOpenProfile : () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable { onOpenProfile() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            photoUrl    = user.photoDisplayUrl,
            displayName = user.displayName,
            size        = 42.dp,
            tierColor   = AccentGreen,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("@${user.username}", color = AccentGreen, style = MaterialTheme.typography.bodySmall)
        }
        when {
            isFriend  -> Icon(Icons.Rounded.Check, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
            isPending -> Surface(
                shape    = RoundedCornerShape(50),
                color    = WarningAmber.copy(alpha = 0.12f),
                modifier = Modifier.border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(50)),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.HourglassTop, null, tint = WarningAmber, modifier = Modifier.size(12.dp))
                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = WarningAmber, fontWeight = FontWeight.SemiBold)
                }
            }
            else      -> IconButton(onClick = onAddClick) {
                Icon(Icons.Rounded.PersonAdd, null, tint = AccentGreen)
            }
        }
    }
}

// ── Friend Requests panel ─────────────────────────────────────────────────────

@Composable
private fun FriendRequestsPanel(
    requests  : List<FriendRequest>,
    onAccept  : (FriendRequest) -> Unit,
    onDecline : (FriendRequest) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, WarningAmber.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .animateContentSize(),
    ) {
        // Panel header — always visible
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Badge with count
                Box(
                    modifier         = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WarningAmber.copy(alpha = 0.18f))
                        .border(1.dp, WarningAmber.copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${requests.size}",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color      = WarningAmber,
                    )
                }
                Column {
                    Text(
                        "Friend Requests",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = TextPrimary,
                    )
                    Text(
                        if (requests.size == 1) "1 person wants to connect"
                        else "${requests.size} people want to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                tint     = TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }

        // Requests list — collapsible
        if (expanded) {
            HorizontalDivider(color = WarningAmber.copy(alpha = 0.18f))
            requests.forEachIndexed { index, req ->
                RequestRow(
                    request   = req,
                    onAccept  = { onAccept(req) },
                    onDecline = { onDecline(req) },
                )
                if (index < requests.lastIndex)
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun RequestRow(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            photoUrl    = request.fromPhotoUrl,
            displayName = request.fromDisplayName,
            size        = 42.dp,
            tierColor   = AccentGreen,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(request.fromDisplayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("@${request.fromUsername}", color = AccentGreen, style = MaterialTheme.typography.bodySmall)
        }
        // Decline button
        IconButton(
            onClick  = onDecline,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ErrorRed.copy(alpha = 0.10f)),
        ) {
            Icon(Icons.Rounded.Close, "Decline", tint = ErrorRed, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        // Accept button
        IconButton(
            onClick  = onAccept,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.15f)),
        ) {
            Icon(Icons.Rounded.Check, "Accept", tint = AccentGreen, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FriendCard(profile: UserProfile, onClick: () -> Unit, onRemove: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Row(
        modifier          = Modifier.glassCard().fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            photoUrl    = profile.photoDisplayUrl,
            displayName = profile.displayName,
            size        = 42.dp,
            tierColor   = AccentGreen,
            cacheKey    = "avatar_${profile.uid}_${profile.photoUpdatedAt}",
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("@${profile.username}", color = AccentGreen, style = MaterialTheme.typography.bodySmall)
        }
        Text("${profile.totalReps} reps", color = TextMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { showConfirm = true }) {
            Icon(Icons.Rounded.PersonRemove, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
    if (showConfirm) {
        AlertDialog(onDismissRequest = { showConfirm = false }, containerColor = SurfaceGlass,
            title = { Text("Remove ${profile.displayName}?", color = TextPrimary) },
            text  = { Text("They'll no longer appear on your leaderboard.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { onRemove(); showConfirm = false }) {
                Text("Remove", color = ErrorRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = TextMuted) } })
    }
}

@Composable
private fun EmptyFriendsState(onAddFriend: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Rounded.Group, null, tint = AccentGreen.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
        Text("No friends yet", style = MaterialTheme.typography.titleMedium,
            color = TextSecondary, fontWeight = FontWeight.SemiBold)
        Text("Add friends to compete on the leaderboard", style = MaterialTheme.typography.bodySmall,
            color = TextMuted)
        Button(
            onClick = onAddFriend,
            colors  = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape   = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Rounded.PersonAdd, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Find Friends", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextMuted,
        letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
}
