package com.pushitup.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pushitup.R
import com.pushitup.core.notification.NotificationPrefs
import com.pushitup.core.theme.*
import com.pushitup.core.theme.ThemeMode
import com.pushitup.core.ui.glassCard
import com.pushitup.feature.workout.domain.DetectionConfig
import com.pushitup.feature.workout.ui.PostWorkoutCelebrationOverlay
import com.pushitup.feature.workout.ui.WorkoutReward
import com.pushitup.feature.workout.ui.WorkoutRewardType

@Composable
fun SettingsScreen(
    onBack    : () -> Unit,
    onSignOut : () -> Unit,
    viewModel : SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    var showChangeEmail    by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showDeleteAccount  by remember { mutableStateOf(false) }
    var showSignOut        by remember { mutableStateOf(false) }

    // Google re-auth launcher — used only when deleting a Google account
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account.idToken?.let { idToken ->
                showDeleteAccount = false
                viewModel.deleteAccountGoogle(idToken)
            }
        } catch (_: ApiException) { /* user cancelled */ }
    }

    // Admin panel state
    var previewRewards    by remember { mutableStateOf<List<WorkoutReward>?>(null) }

    // Collect one-shot navigation events from the ViewModel (always on main thread)
    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                SettingsNavEvent.NavigateToLogin -> onSignOut()
            }
        }
    }

    LaunchedEffect(uiState.success, uiState.error) {
        if (uiState.success != null || uiState.error != null) {
            kotlinx.coroutines.delay(3500)
            viewModel.clearMessages()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDeep)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                modifier          = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(R.string.common_back), tint = TextSecondary)
                }
                Text(
                    stringResource(R.string.settings_title),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color      = TextPrimary,
                )
            }

            Column(
                modifier            = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── Account section ───────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_account))

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    SettingsRow(
                        icon    = Icons.Rounded.Email,
                        label   = stringResource(R.string.settings_email),
                        value   = uiState.currentEmail,
                        onClick = { showChangeEmail = true },
                    )
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    SettingsRow(
                        icon    = Icons.Rounded.Lock,
                        label   = stringResource(R.string.settings_password),
                        value   = stringResource(R.string.settings_password_change),
                        onClick = { showChangePassword = true },
                    )
                }

                // ── Notifications section ─────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_notifications))

                NotificationsSection(
                    prefs                     = uiState.notifPrefs,
                    onDailyReminderToggle     = viewModel::setDailyReminderEnabled,
                    onDailyReminderTimeChange = viewModel::setDailyReminderTime,
                    onLeaderboardToggle       = viewModel::setLeaderboardNotif,
                    onChallengeDeadlineToggle = viewModel::setChallengeDeadlineNotif,
                )

                // ── Appearance section ────────────────────────────────────────
                SectionLabel("Appearance")

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    ThemeModeRow(
                        icon     = Icons.Rounded.SettingsBrightness,
                        label    = "System default",
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        onClick  = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    ThemeModeRow(
                        icon     = Icons.Rounded.LightMode,
                        label    = "Light",
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick  = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    )
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    ThemeModeRow(
                        icon     = Icons.Rounded.DarkMode,
                        label    = "Dark",
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick  = { viewModel.setThemeMode(ThemeMode.DARK) },
                    )
                }

                // ── Language section ──────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_language))

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    LanguageRow(
                        label     = stringResource(R.string.settings_lang_english),
                        selected  = uiState.selectedLanguage == "en",
                        onClick   = { viewModel.setLanguage("en") },
                    )
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    LanguageRow(
                        label     = stringResource(R.string.settings_lang_french),
                        selected  = uiState.selectedLanguage == "fr",
                        onClick   = { viewModel.setLanguage("fr") },
                    )
                }

                // ── Guide tour ────────────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_guide))

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    SettingsRow(
                        icon    = Icons.Rounded.Explore,
                        label   = stringResource(R.string.settings_replay_tour),
                        onClick = {
                            viewModel.resetTour()
                            onBack()
                        },
                    )
                }

                // ── Support section ───────────────────────────────────────────
                SectionLabel("Support")

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    SettingsRow(
                        icon    = Icons.Rounded.BugReport,
                        label   = "Report a bug",
                        value   = "mathias.simoens1@gmail.com",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL,   arrayOf("mathias.simoens1@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "[Push It] Bug Report")
                                putExtra(Intent.EXTRA_TEXT,
                                    "Describe the bug:\n\n" +
                                    "Steps to reproduce:\n\n" +
                                    "Expected behaviour:\n\n" +
                                    "Actual behaviour:\n\n" +
                                    "---\nApp version: 1.0 (2)"
                                )
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    )
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    SettingsRow(
                        icon    = Icons.Rounded.Lightbulb,
                        label   = "Suggest a feature",
                        value   = "mathias.simoens1@gmail.com",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL,   arrayOf("mathias.simoens1@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "[Push It] Feature Request")
                                putExtra(Intent.EXTRA_TEXT,    "I'd love to see:\n\n")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }

                // ── Admin panel (hidden — derived directly from currentEmail) ─
                // Using currentEmail rather than uiState.isAdmin avoids async
                // timing issues — currentEmail is set synchronously at init.
                val isAdmin = uiState.currentEmail.equals("admin@pushit.com", ignoreCase = true)
                if (isAdmin) {
                    AdminSection(
                        onTestNotification = viewModel::sendTestNotification,
                        onPreviewAnimation = { rewards -> previewRewards = rewards },
                    )
                }

                // ── Session section ───────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_session))

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    SettingsRow(
                        icon    = Icons.Rounded.Logout,
                        label   = stringResource(R.string.settings_sign_out),
                        tint    = WarningAmber,
                        onClick = { showSignOut = true },
                    )
                }

                // ── Danger zone ───────────────────────────────────────────────
                SectionLabel(stringResource(R.string.settings_section_danger))

                Column(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp)) {
                    SettingsRow(
                        icon    = Icons.Rounded.DeleteForever,
                        label   = stringResource(R.string.settings_delete_account),
                        tint    = ErrorRed,
                        onClick = { showDeleteAccount = true },
                    )
                }

                // Toast / feedback
                uiState.success?.let { msg ->
                    FeedbackBar(msg, isError = false)
                }
                uiState.error?.let { msg ->
                    FeedbackBar(msg, isError = true)
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showChangeEmail) {
        ChangeEmailDialog(
            onConfirm = { newEmail, password ->
                viewModel.updateEmail(newEmail, password)
                showChangeEmail = false
            },
            onDismiss = { showChangeEmail = false },
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onConfirm = { current, newPass ->
                viewModel.updatePassword(current, newPass)
                showChangePassword = false
            },
            onDismiss = { showChangePassword = false },
        )
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            containerColor   = SurfaceGlass,
            title = { Text(stringResource(R.string.settings_sign_out_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text(stringResource(R.string.settings_sign_out_body), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showSignOut = false; viewModel.signOut() }) {
                    Text(stringResource(R.string.settings_sign_out), color = WarningAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text(stringResource(R.string.common_cancel), color = TextMuted) }
            },
        )
    }

    if (showDeleteAccount) {
        if (uiState.isGoogleUser) {
            // Google users: confirm intent, then re-authenticate via Google Sign-In
            GoogleDeleteAccountDialog(
                onConfirm = {
                    showDeleteAccount = false
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleDeleteLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                onDismiss = { showDeleteAccount = false },
            )
        } else {
            DeleteAccountDialog(
                onConfirm = { password ->
                    showDeleteAccount = false
                    viewModel.deleteAccount(password)
                },
                onDismiss = { showDeleteAccount = false },
            )
        }
    }

    // Admin: animation preview overlay — full-screen, dismissible
    previewRewards?.let { rewards ->
        PostWorkoutCelebrationOverlay(
            rewards   = rewards,
            onDismiss = { previewRewards = null },
        )
    }
}

// ── Theme mode row ────────────────────────────────────────────────────────────

@Composable
private fun ThemeModeRow(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    label   : String,
    selected: Boolean,
    onClick : () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint     = if (selected) AccentGreen else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                color    = if (selected) TextPrimary else TextSecondary,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint     = AccentGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Language row ──────────────────────────────────────────────────────────────

@Composable
private fun LanguageRow(
    label   : String,
    selected: Boolean,
    onClick : () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Language,
                contentDescription = null,
                tint     = if (selected) AccentGreen else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                color    = if (selected) TextPrimary else TextSecondary,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint     = AccentGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Change email dialog ───────────────────────────────────────────────────────

@Composable
private fun ChangeEmailDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var newEmail  by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = { Text(stringResource(R.string.settings_change_email_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_change_email_body), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                StyledTextField(value = newEmail, onChange = { newEmail = it }, label = stringResource(R.string.settings_new_email), keyboardType = KeyboardType.Email)
                StyledTextField(
                    value = password, onChange = { password = it }, label = stringResource(R.string.settings_current_password),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TextMuted)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newEmail, password) }, enabled = newEmail.isNotBlank() && password.isNotBlank()) {
                Text(stringResource(R.string.settings_send_verification), color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) } },
    )
}

// ── Change password dialog ────────────────────────────────────────────────────

@Composable
private fun ChangePasswordDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var current     by remember { mutableStateOf("") }
    var newPass     by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew     by remember { mutableStateOf(false) }

    val mismatch = newPass.isNotEmpty() && confirmPass.isNotEmpty() && newPass != confirmPass
    val tooShort = newPass.isNotEmpty() && newPass.length < 6

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = { Text(stringResource(R.string.settings_change_password_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StyledTextField(
                    value = current, onChange = { current = it }, label = stringResource(R.string.settings_current_password),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(if (showCurrent) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TextMuted)
                        }
                    }
                )
                StyledTextField(
                    value = newPass, onChange = { newPass = it }, label = stringResource(R.string.settings_new_password),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(if (showNew) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TextMuted)
                        }
                    }
                )
                StyledTextField(
                    value = confirmPass, onChange = { confirmPass = it }, label = stringResource(R.string.settings_confirm_password),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                )
                if (mismatch) Text(stringResource(R.string.settings_password_mismatch), color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                if (tooShort) Text(stringResource(R.string.settings_password_too_short), color = WarningAmber, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(current, newPass) },
                enabled  = current.isNotBlank() && newPass.length >= 6 && newPass == confirmPass,
            ) {
                Text(stringResource(R.string.settings_password_update), color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) } },
    )
}

// ── Delete account dialog ─────────────────────────────────────────────────────

@Composable
private fun DeleteAccountDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = { Text(stringResource(R.string.settings_delete_title), color = ErrorRed, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.settings_delete_body),
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
                StyledTextField(
                    value = password, onChange = { password = it }, label = stringResource(R.string.settings_delete_confirm_hint),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TextMuted)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank()) {
                Text(stringResource(R.string.settings_delete_forever), color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) } },
    )
}

@Composable
private fun GoogleDeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = { Text("Delete account", color = ErrorRed, fontWeight = FontWeight.Bold) },
        text  = {
            Text(
                "This will permanently delete your account and all your data. " +
                "You'll be asked to sign in with Google to confirm.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) {
                Text("Continue with Google", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = TextMuted)
            }
        },
    )
}

// ── Shared UI ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style         = MaterialTheme.typography.labelSmall,
        color         = TextMuted,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier      = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon   : ImageVector,
    label  : String,
    value  : String     = "",
    tint   : Color      = TextSecondary,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = tint.takeIf { tint != TextSecondary } ?: TextPrimary,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) Text(value, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FeedbackBar(message: String, isError: Boolean) {
    val bg = if (isError) ErrorRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f)
    val fg = if (isError) ErrorRed else AccentGreen
    Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (isError) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
                null, tint = fg, modifier = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Text(message, color = fg, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Notifications section ─────────────────────────────────────────────────────

@Composable
private fun NotificationsSection(
    prefs                     : NotificationPrefs,
    onDailyReminderToggle     : (Boolean) -> Unit,
    onDailyReminderTimeChange : (Int, Int) -> Unit,
    onLeaderboardToggle       : (Boolean) -> Unit,
    onChallengeDeadlineToggle : (Boolean) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth().glassCard(cornerRadius = 14.dp).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Daily reminder toggle
        NotifToggleRow(
            icon    = Icons.Rounded.Notifications,
            label   = stringResource(R.string.settings_notif_daily_reminder),
            desc    = stringResource(R.string.settings_notif_daily_reminder_desc),
            checked = prefs.dailyReminderEnabled,
            onToggle = onDailyReminderToggle,
        )

        // Reminder time picker — only shown when daily reminder is on
        if (prefs.dailyReminderEnabled) {
            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
            Surface(
                onClick = { showTimePicker = true },
                color   = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Schedule, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.settings_notif_reminder_time),
                        color    = TextPrimary,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%02d:%02d".format(prefs.dailyReminderHour, prefs.dailyReminderMinute),
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)

        // Leaderboard rank toggle
        NotifToggleRow(
            icon     = Icons.Rounded.Leaderboard,
            label    = stringResource(R.string.settings_notif_leaderboard),
            desc     = stringResource(R.string.settings_notif_leaderboard_desc),
            checked  = prefs.leaderboardNotif,
            onToggle = onLeaderboardToggle,
        )

        HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)

        // Challenge deadline toggle
        NotifToggleRow(
            icon     = Icons.Rounded.Timer,
            label    = stringResource(R.string.settings_notif_challenges),
            desc     = stringResource(R.string.settings_notif_challenges_desc),
            checked  = prefs.challengeDeadlineNotif,
            onToggle = onChallengeDeadlineToggle,
        )

    }

    // Time picker dialog
    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour   = prefs.dailyReminderHour,
            initialMinute = prefs.dailyReminderMinute,
            onConfirm     = { h, m -> onDailyReminderTimeChange(h, m); showTimePicker = false },
            onDismiss     = { showTimePicker = false },
        )
    }
}

@Composable
private fun NotifToggleRow(
    icon    : ImageVector,
    label   : String,
    desc    : String,
    checked : Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(desc, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = TextPrimary,
                checkedTrackColor  = AccentGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = GlassBorder,
            ),
        )
    }
}

@Composable
private fun ReminderTimePickerDialog(
    initialHour  : Int,
    initialMinute: Int,
    onConfirm    : (Int, Int) -> Unit,
    onDismiss    : () -> Unit,
) {
    var hour   by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceGlass,
        title = {
            Text(
                stringResource(R.string.settings_notif_reminder_time),
                color      = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Hour drum
                TimeWheelColumn(
                    value     = hour,
                    range     = 0..23,
                    onInc     = { hour = (hour + 1) % 24 },
                    onDec     = { hour = (hour + 23) % 24 },
                )

                Text(
                    ":",
                    style      = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color      = TextPrimary,
                    modifier   = Modifier.padding(horizontal = 8.dp),
                )

                // Minute drum
                TimeWheelColumn(
                    value     = minute,
                    range     = 0..59,
                    onInc     = { minute = (minute + 1) % 60 },
                    onDec     = { minute = (minute + 59) % 60 },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) {
                Text(stringResource(R.string.common_ok), color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextMuted) }
        },
    )
}

@Composable
private fun TimeWheelColumn(
    value : Int,
    range : IntRange,
    onInc : () -> Unit,
    onDec : () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Up arrow
        IconButton(
            onClick  = onInc,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGreen.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = AccentGreen, modifier = Modifier.size(28.dp))
        }

        // Value display
        Box(
            modifier          = Modifier
                .size(72.dp, 64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BackgroundDeep)
                .border(1.5.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
            contentAlignment  = Alignment.Center,
        ) {
            Text(
                text       = "%02d".format(value),
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color      = TextPrimary,
            )
        }

        // Down arrow
        IconButton(
            onClick  = onDec,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGreen.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = AccentGreen, modifier = Modifier.size(28.dp))
        }
    }
}

// ── Admin panel ───────────────────────────────────────────────────────────────

private val ADMIN_ACCENT = Color(0xFF8B5CF6)   // purple — visually distinct from prod UI

@Composable
private fun AdminSection(
    onTestNotification : () -> Unit,
    onPreviewAnimation : (List<WorkoutReward>) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // ── Section header — tap to collapse ─────────────────────────────────
        Surface(
            onClick = { expanded = !expanded },
            color   = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.Security,
                    null,
                    tint     = ADMIN_ACCENT,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "ADMIN PANEL",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = ADMIN_ACCENT,
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint     = ADMIN_ACCENT.copy(alpha = 0.60f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(ADMIN_ACCENT.copy(alpha = 0.08f), ADMIN_ACCENT.copy(alpha = 0.03f))
                        )
                    )
                    .border(1.dp, ADMIN_ACCENT.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
            ) {

                // ── Test notification ─────────────────────────────────────────
                AdminRow(
                    icon    = Icons.Rounded.NotificationsActive,
                    label   = "Send test notification",
                    desc    = "Fires a real notification to verify channels are working",
                    onClick = onTestNotification,
                )

                HorizontalDivider(color = ADMIN_ACCENT.copy(alpha = 0.15f), thickness = 0.5.dp)

                // ── Animation previews ────────────────────────────────────────
                AdminRow(
                    icon    = Icons.Rounded.EmojiEvents,
                    label   = "Preview: Achievement unlock",
                    desc    = "Shows the post-workout celebration for a new achievement",
                    tint    = StarColor,
                    onClick = {
                        onPreviewAnimation(listOf(
                            WorkoutReward(WorkoutRewardType.ACHIEVEMENT, "Iron Will", "Complete 5 sessions — unlocked!", stars = 150),
                        ))
                    },
                )

                HorizontalDivider(color = ADMIN_ACCENT.copy(alpha = 0.15f), thickness = 0.5.dp)

                AdminRow(
                    icon    = Icons.Rounded.Bolt,
                    label   = "Preview: Challenge complete",
                    desc    = "Shows the post-workout celebration for a completed challenge",
                    tint    = Color(0xFF22C55E),
                    onClick = {
                        onPreviewAnimation(listOf(
                            WorkoutReward(WorkoutRewardType.CHALLENGE, "Century Club", "100 reps in one session!", stars = 500),
                        ))
                    },
                )

                HorizontalDivider(color = ADMIN_ACCENT.copy(alpha = 0.15f), thickness = 0.5.dp)

                AdminRow(
                    icon    = Icons.Rounded.AutoAwesome,
                    label   = "Preview: Rank up",
                    desc    = "Shows the rank-up celebration overlay",
                    tint    = ADMIN_ACCENT,
                    onClick = {
                        onPreviewAnimation(listOf(
                            WorkoutReward(WorkoutRewardType.RANK_UP, "Gold I", "You reached Gold I!", stars = 0),
                        ))
                    },
                )

                HorizontalDivider(color = ADMIN_ACCENT.copy(alpha = 0.15f), thickness = 0.5.dp)

                AdminRow(
                    icon    = Icons.Rounded.AutoAwesome,
                    label   = "Preview: All rewards at once",
                    desc    = "Stacked achievement + challenge + rank-up in one overlay",
                    tint    = Color(0xFFFF6B35),
                    onClick = {
                        onPreviewAnimation(listOf(
                            WorkoutReward(WorkoutRewardType.ACHIEVEMENT, "First Blood",  "First session ever!",           stars = 50),
                            WorkoutReward(WorkoutRewardType.CHALLENGE,   "Speed Demon",  "30 reps in under 60 seconds!",  stars = 350),
                            WorkoutReward(WorkoutRewardType.RANK_UP,     "Silver II",    "You reached Silver II!",        stars = 0),
                        ))
                    },
                )

                HorizontalDivider(color = ADMIN_ACCENT.copy(alpha = 0.15f), thickness = 0.5.dp)

                // ── Detection tuning ──────────────────────────────────────────
                DetectionTuningPanel()
            }
        }
    }
}

@Composable
private fun DetectionTuningPanel() {
    var debounce      by remember { mutableLongStateOf(DetectionConfig.debounceMs) }
    var minDownFrames by remember { mutableIntStateOf(DetectionConfig.minDownFrames) }
    var margin        by remember { mutableFloatStateOf(DetectionConfig.thresholdMargin) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Tune, null, tint = ADMIN_ACCENT, modifier = Modifier.size(18.dp))
            Text("Detection Tuning", color = ADMIN_ACCENT, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                DetectionConfig.reset()
                debounce      = DetectionConfig.debounceMs
                minDownFrames = DetectionConfig.minDownFrames
                margin        = DetectionConfig.thresholdMargin
            }) { Text("Reset", color = ADMIN_ACCENT, style = MaterialTheme.typography.labelSmall) }
        }

        // Debounce
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Debounce", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${debounce} ms", color = ADMIN_ACCENT, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Slider(
                value         = debounce.toFloat(),
                onValueChange = { debounce = it.toLong(); DetectionConfig.debounceMs = debounce },
                valueRange    = 200f..1200f,
                steps         = 9,
                colors        = SliderDefaults.colors(activeTrackColor = ADMIN_ACCENT, thumbColor = ADMIN_ACCENT),
            )
            Text("Min ms between two counted reps. Higher = less sensitive to noise.", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }

        // Min DOWN frames
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Min DOWN frames", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("$minDownFrames frames (~${minDownFrames * 33} ms)", color = ADMIN_ACCENT, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Slider(
                value         = minDownFrames.toFloat(),
                onValueChange = { minDownFrames = it.toInt(); DetectionConfig.minDownFrames = minDownFrames },
                valueRange    = 1f..15f,
                steps         = 13,
                colors        = SliderDefaults.colors(activeTrackColor = ADMIN_ACCENT, thumbColor = ADMIN_ACCENT),
            )
            Text("Consecutive frames signal must stay in DOWN zone before a rep bottom is accepted.", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }

        // Threshold margin
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("Threshold margin", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${(margin * 100).toInt()}%", color = ADMIN_ACCENT, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Slider(
                value         = margin,
                onValueChange = { margin = it; DetectionConfig.thresholdMargin = margin },
                valueRange    = 0.10f..0.40f,
                steps         = 5,
                colors        = SliderDefaults.colors(activeTrackColor = ADMIN_ACCENT, thumbColor = ADMIN_ACCENT),
            )
            Text("Fraction of calibrated range used as UP/DOWN margin. Lower = wider dead-zone = harder to misfire.", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AdminRow(
    icon    : ImageVector,
    label   : String,
    desc    : String,
    tint    : Color      = ADMIN_ACCENT,
    onClick : () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(desc,  color = TextMuted,   style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = tint.copy(alpha = 0.70f), modifier = Modifier.size(16.dp))
        }
    }
}

// ── Styled text field ─────────────────────────────────────────────────────────

@Composable
private fun StyledTextField(
    value               : String,
    onChange            : (String) -> Unit,
    label               : String,
    keyboardType        : KeyboardType          = KeyboardType.Text,
    visualTransformation: VisualTransformation  = VisualTransformation.None,
    trailingIcon        : (@Composable () -> Unit)? = null,
    isError             : Boolean               = false,
) {
    OutlinedTextField(
        value                = value,
        onValueChange        = onChange,
        label                = { Text(label, color = TextMuted) },
        singleLine           = true,
        isError              = isError,
        keyboardOptions      = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon         = trailingIcon,
        modifier             = Modifier.fillMaxWidth(),
        colors               = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentGreen,
            unfocusedBorderColor = GlassBorder,
            errorBorderColor     = ErrorRed,
            cursorColor          = AccentGreen,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
        ),
    )
}
