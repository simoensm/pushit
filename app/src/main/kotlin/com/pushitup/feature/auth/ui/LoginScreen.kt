package com.pushitup.feature.auth.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.pushitup.R
import com.pushitup.core.theme.*
import com.pushitup.core.ui.glassCard

@Composable
fun LoginScreen(
    onNavigateToSignUp  : () -> Unit,
    onLoginSuccess      : () -> Unit,
    onNeedsProfileSetup : () -> Unit = {},
    viewModel           : AuthViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsState().value
    val focus   = LocalFocusManager.current
    val context = LocalContext.current

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onLoginSuccess()
    }
    LaunchedEffect(uiState.needsProfileSetup) {
        if (uiState.needsProfileSetup) onNeedsProfileSetup()
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account.idToken?.let { viewModel.signInWithGoogle(it) }
        } catch (_: ApiException) { /* user cancelled */ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        // Logo
        Image(
            painter            = painterResource(R.drawable.ic_logo_transparent),
            contentDescription = "PushIt",
            modifier           = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "PushIt",
            style      = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color      = TextPrimary,
        )
        Text(
            "Sign in to track your reps",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(40.dp))

        // ── Google button ─────────────────────────────────────────────────────
        Button(
            onClick  = {
                // Sign out first so the account picker always appears
                googleSignInClient.signOut().addOnCompleteListener {
                    googleLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            enabled  = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor   = Color.White,
            ),
        ) {
            Image(
                painter            = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Continue with Google", fontWeight = FontWeight.SemiBold, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        // ── Divider ───────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
            Text(
                "  or  ",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = GlassBorder)
        }

        Spacer(Modifier.height(16.dp))

        // ── Email/password form ───────────────────────────────────────────────
        Column(
            modifier            = Modifier.glassCard().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                label         = { Text("Email") },
                leadingIcon   = { Icon(Icons.Rounded.Email, null, tint = TextMuted) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                colors          = authFieldColors(),
                modifier        = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text("Password") },
                leadingIcon   = { Icon(Icons.Rounded.Lock, null, tint = TextMuted) },
                trailingIcon  = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            null, tint = TextMuted,
                        )
                    }
                },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) viewModel.signIn(email, password)
                }),
                colors   = authFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.error != null) {
                Text(uiState.error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick  = { viewModel.signIn(email, password) },
                enabled  = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
                colors   = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onNavigateToSignUp) {
                Text("Sign Up", color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = AccentGreen,
    unfocusedBorderColor    = GlassBorder,
    focusedLabelColor       = AccentGreen,
    unfocusedLabelColor     = TextMuted,
    cursorColor             = AccentGreen,
    focusedTextColor        = TextPrimary,
    unfocusedTextColor      = TextPrimary,
    focusedContainerColor   = SurfaceGlass,
    unfocusedContainerColor = SurfaceGlass,
)
